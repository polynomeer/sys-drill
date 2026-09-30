"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { WriteupList, listWriteups, startSession } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { LoadingState } from "@/components/ui/LoadingState";

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.2 / ADR-0041 — 시나리오별 공개 풀이 목록.
 *
 * 잠긴 상태는 오류 화면이 아니라 이 페이지의 정상 상태다. 서버는 200 에
 * `locked: true` 와 편 수만 담아 주고, 여기서는 그 숫자를 **동기로** 쓴다 —
 * "먼저 직접 풀어보세요"만 있으면 벽이지만, "5편이 기다립니다"가 붙으면 문이다.
 */
export default function WriteupListPage() {
  const params = useParams<{ scenarioId: string }>();
  const router = useRouter();
  const scenarioId = params.scenarioId;

  const [list, setList] = useState<WriteupList | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    listWriteups(scenarioId)
      .then(setList)
      .catch(() => setError("풀이 목록을 불러오지 못했습니다."));
  }, [scenarioId, router]);

  async function handleStart() {
    if (starting) return;
    setStarting(true);
    try {
      const session = await startSession(scenarioId);
      router.push(`/design/${session.id}`);
    } catch {
      setStarting(false);
      setError("세션을 시작하지 못했습니다.");
    }
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">공개된 풀이</h1>
        {list && <p className="mt-1 text-sm text-foreground-muted">{list.scenarioTitle}</p>}
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}
      {!list && !error && <LoadingState />}

      {list?.locked && (
        <Card as="section">
          <h2 className="mb-2 text-sm font-semibold">먼저 직접 풀어보세요</h2>
          <p className="mb-3 text-sm text-foreground-muted">
            {list.count > 0
              ? `이 시나리오에는 공개된 풀이가 ${list.count}편 있습니다. 한 번 끝까지 마치면 모두 열립니다.`
              : "이 시나리오를 완료하면 다른 사람의 풀이를 볼 수 있습니다."}
          </p>
          <p className="mb-4 text-xs text-foreground-muted">
            남의 설계를 먼저 읽고 들어가면 그 세션은 훈련이 아니라 받아쓰기가 됩니다. 점수가 낮아도 괜찮습니다 —
            직접 판단해 본 다음에야 남의 풀이가 제대로 읽힙니다.
          </p>
          <Button onClick={handleStart} disabled={starting} size="sm">
            {starting ? "시작하는 중..." : "지금 풀어보기"}
          </Button>
        </Card>
      )}

      {list && !list.locked && (
        <Card as="section">
          <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-sm font-semibold">{list.count}편</h2>
            <span className="text-xs text-foreground-muted">최근 공개순</span>
          </div>
          {list.writeups.length === 0 ? (
            <p className="text-sm text-foreground-muted">
              아직 공개된 풀이가 없습니다. 리포트 화면에서 내 풀이를 첫 번째로 공개해보세요.
            </p>
          ) : (
            <ul className="divide-y divide-border">
              {list.writeups.map((writeup) => (
                <li key={writeup.sessionId} className="py-2">
                  <Link
                    href={`/writeups/${scenarioId}/${writeup.sessionId}`}
                    className="flex flex-wrap items-baseline justify-between gap-2 text-sm hover:underline"
                  >
                    <span className="flex flex-wrap items-baseline gap-2">
                      {writeup.anonymous ? "익명" : (writeup.authorNickname ?? "알 수 없음")}
                      {writeup.mine && <Badge variant="accent">내 풀이</Badge>}
                    </span>
                    <span className="text-xs tabular-nums text-foreground-muted">
                      {typeof writeup.averageScore === "number" ? `평균 ${writeup.averageScore}점` : "점수 없음"}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}
    </div>
  );
}
