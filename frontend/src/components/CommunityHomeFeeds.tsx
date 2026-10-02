"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { type CommunityHome, getCommunityHome } from "@/lib/api";
import { Badge } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";

const LAST_VISIT_KEY = "sysdrill:community:lastVisit";
const KIND_LABELS: Record<string, string> = { QUESTION: "질문", DESIGN: "설계", RESPONSE: "대응", INSIGHT: "인사이트" };

function readLastVisit(): string | undefined {
  try {
    return window.localStorage.getItem(LAST_VISIT_KEY) ?? undefined;
  } catch {
    return undefined;
  }
}

function writeLastVisit(at: string) {
  try {
    window.localStorage.setItem(LAST_VISIT_KEY, at);
  } catch {
    // a private window — "new" just falls back to the last 7 days
  }
}

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C11 (PLAN.md Round E23) — the top of the Community home:
 * what's new in the Drills I did (since my last visit), busy threads this week, and the
 * writeups that drew the most reviews. The last-visit time lives in localStorage only.
 */
export function CommunityHomeFeeds() {
  const [home, setHome] = useState<CommunityHome | null>(null);

  useEffect(() => {
    const since = readLastVisit();
    getCommunityHome(since)
      .then((h) => {
        setHome(h);
        writeLastVisit(new Date().toISOString());
      })
      .catch(() => setHome(null));
  }, []);

  if (!home) return null;

  return (
    <>
      <Card as="section">
        <h2 className="mb-2 text-sm font-semibold">내 Drill에서</h2>
        {home.myDrills.length === 0 ? (
          <p className="text-sm text-foreground-muted">Drill을 하나 완료하면 같은 문제를 푼 사람들의 풀이와 토론 소식이 여기에 모입니다.</p>
        ) : (
          <ul className="divide-y divide-border">
            {home.myDrills.map((d) => (
              <li key={d.scenarioId} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                <span className="flex flex-wrap items-center gap-2">
                  {d.title}
                  {d.newWriteups > 0 && <Badge variant="success">새 풀이 {d.newWriteups}</Badge>}
                  {d.newDiscussions > 0 && <Badge variant="warning">새 토론 {d.newDiscussions}</Badge>}
                </span>
                <span className="flex items-center gap-3 text-xs">
                  <Link href={`/writeups/${d.scenarioId}`} className="underline">
                    풀이 {d.writeups}편 비교하기
                  </Link>
                  <Link href={`/discussions/${d.scenarioId}`} className="text-foreground-muted underline">
                    토론
                  </Link>
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {home.activeDiscussions.length > 0 && (
        <Card as="section">
          <h2 className="mb-2 text-sm font-semibold">이번 주 활발한 토론</h2>
          <ul className="flex flex-col gap-3">
            {home.activeDiscussions.map((t) => (
              <li key={t.scenarioId} className="text-sm">
                <Link href={`/discussions/${t.scenarioId}`} className="font-medium hover:underline">
                  {t.title}
                </Link>
                <span className="ml-2 text-xs text-foreground-muted">이번 주 {t.postsThisWeek}개</span>
                <ul className="mt-1 flex flex-col gap-0.5 text-xs text-foreground-muted">
                  {t.latest.map((p) => (
                    <li key={p.id}>
                      <span className="mr-1">[{KIND_LABELS[p.kind] ?? p.kind}]</span>
                      {p.spoilerLocked ? "🔒 풀이 내용이 담긴 글 — 완료하면 보입니다" : p.excerpt}
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {home.notableWriteups.length > 0 && (
        <Card as="section">
          <h2 className="mb-2 text-sm font-semibold">주목할 풀이</h2>
          <p className="mb-2 text-xs text-foreground-muted">리뷰가 많이 달린 풀이입니다.</p>
          <ul className="divide-y divide-border">
            {home.notableWriteups.map((w) => (
              <li key={w.sessionId} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                {w.locked ? (
                  <span className="text-foreground-muted">🔒 {w.scenarioTitle} — 완료하면 볼 수 있습니다</span>
                ) : (
                  <Link href={`/writeups/${w.scenarioId}/${w.sessionId}`} className="hover:underline">
                    {w.scenarioTitle} · {w.authorNickname ?? "익명"}
                  </Link>
                )}
                <span className="text-xs text-foreground-muted">리뷰 {w.reviewCount}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </>
  );
}
