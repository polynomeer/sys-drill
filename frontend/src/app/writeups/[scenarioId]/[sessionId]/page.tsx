"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ApiError, WriteupDetail, getWriteup } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { LoadingState } from "@/components/ui/LoadingState";
import { formatDuration } from "@/lib/metrics";
import { ComparePanel, WriteupSummaryCard } from "./WriteupExtras";

const PHASE_LABELS: Record<string, string> = {
  INITIAL: "초기 설계",
  FOLLOWUP: "꼬리설계",
  INCIDENT: "장애 대응 회고",
};

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.2 — 공개 풀이 한 편.
 *
 * 공개 단위가 세션 하나 통째인 이유가 이 화면에 그대로 드러난다: 답안만 떼어
 * 놓으면 "왜 이 설계가 이 점수였나"가 사라져 학습 가치가 남지 않는다. 그래서
 * 단계별 답안 옆에 점수와 지적받은 리스크를 함께 둔다.
 */
export default function WriteupDetailPage() {
  const params = useParams<{ scenarioId: string; sessionId: string }>();
  const router = useRouter();
  const { scenarioId, sessionId } = params;

  const [writeup, setWriteup] = useState<WriteupDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    getWriteup(sessionId)
      .then(setWriteup)
      .catch((e: unknown) => {
        // 403 은 "아직 자격이 없다"라 목록으로 돌려보낸다 — 거기에 안내와 시작 버튼이 있다.
        if (e instanceof ApiError && e.status === 403) {
          router.replace(`/writeups/${scenarioId}`);
          return;
        }
        setError("풀이를 불러오지 못했습니다.");
      });
  }, [scenarioId, sessionId, router]);

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6 p-8">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h1 className="text-xl font-semibold">공개된 풀이</h1>
        <Link href={`/writeups/${scenarioId}`} className="text-sm underline">
          목록으로
        </Link>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}
      {!writeup && !error && <LoadingState />}

      {writeup && (
        <>
          <Card className="flex flex-wrap items-baseline justify-between gap-2">
            <div>
              <p className="text-sm text-foreground-muted">{writeup.scenarioTitle}</p>
              <p className="flex flex-wrap items-baseline gap-2 font-medium">
                {writeup.anonymous ? "익명" : (writeup.authorNickname ?? "알 수 없음")}
                {writeup.mine && <Badge variant="accent">내 풀이</Badge>}
              </p>
            </div>
            <span className="text-sm tabular-nums text-foreground-muted">
              {typeof writeup.averageScore === "number" ? `평균 ${writeup.averageScore}점` : "점수 없음"}
            </span>
          </Card>

          {/* docs/COMMUNITY_EXPANSION_PLAN.md C8 (PLAN.md Round E15) */}
          <WriteupSummaryCard writeup={writeup} onUpdated={setWriteup} />
          {!writeup.mine && <ComparePanel sessionId={writeup.sessionId} />}

          <Card as="section">
            <h2 className="mb-3 text-sm font-semibold text-foreground-muted">단계별 답안</h2>
            <ul className="flex flex-col gap-4">
              {writeup.phases.map((phase, i) => (
                <li key={i} className="border-t border-border pt-4 first:border-t-0 first:pt-0">
                  <div className="mb-2 flex items-baseline justify-between gap-2">
                    <span className="text-sm font-medium">{PHASE_LABELS[phase.phase] ?? phase.phase}</span>
                    <span className="font-mono text-sm">{phase.score ?? "-"} / 100</span>
                  </div>
                  <p className="whitespace-pre-wrap text-sm">{phase.answer ?? "(답안 없음)"}</p>
                  {phase.topRisks.length > 0 && (
                    <ul className="mt-2 list-inside list-disc text-xs text-foreground-muted">
                      {phase.topRisks.map((risk, j) => (
                        <li key={j}>{risk}</li>
                      ))}
                    </ul>
                  )}
                </li>
              ))}
            </ul>
          </Card>

          {(writeup.rootCause || writeup.preventionItems.length > 0) && (
            <Card as="section">
              <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
                <h2 className="text-sm font-semibold text-foreground-muted">포스트모템</h2>
                {(writeup.mttdSeconds != null || writeup.mttrSeconds != null) && (
                  <span className="text-xs text-foreground-muted">
                    {writeup.mttdSeconds != null && `MTTD ${formatDuration(writeup.mttdSeconds)}`}
                    {writeup.mttdSeconds != null && writeup.mttrSeconds != null && " · "}
                    {writeup.mttrSeconds != null && `MTTR ${formatDuration(writeup.mttrSeconds)}`}
                  </span>
                )}
              </div>
              {writeup.rootCause && <p className="whitespace-pre-wrap text-sm">{writeup.rootCause}</p>}
              {writeup.preventionItems.length > 0 && (
                <ul className="mt-2 list-inside list-disc space-y-1 text-sm text-foreground-muted">
                  {writeup.preventionItems.map((item, i) => (
                    <li key={i}>{item}</li>
                  ))}
                </ul>
              )}
            </Card>
          )}
        </>
      )}
    </div>
  );
}
