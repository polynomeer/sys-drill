"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { type ChallengeBoard, getChallengeBoard } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C13 (PLAN.md Round E31) — one challenge's board. Score first, then the
 * time to a declared full recovery (M5). Writeups appear only in the debrief, after the end.
 */
export default function ChallengeEventPage() {
  const router = useRouter();
  const { eventId } = useParams<{ eventId: string }>();
  const [board, setBoard] = useState<ChallengeBoard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    getChallengeBoard(eventId)
      .then(setBoard)
      .catch(() => setError("챌린지를 찾을 수 없습니다."));
  }, [eventId, router]);

  if (error) return <p className="mx-auto max-w-7xl p-8 text-sm text-danger">{error}</p>;
  if (!board) return <div className="mx-auto max-w-7xl p-8"><LoadingState /></div>;
  const { event } = board;

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6 p-8">
      <div>
        <Link href="/community" className="text-sm text-foreground-muted hover:text-foreground">
          ← Community
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">{event.title}</h1>
      </div>

      {/* 왼쪽은 보드(디브리프), 오른쪽은 챌린지 정보·규칙·참가 버튼. 좁은 화면에서는 정보가 먼저 온다. */}
      <div className="flex flex-col gap-6 lg:grid lg:grid-cols-[minmax(0,1fr)_360px] lg:items-start">
        <aside className="flex flex-col gap-4 lg:order-2">
          <Card as="section">
            <p className="text-sm text-foreground-muted">
              {event.scenarioTitle} · {new Date(event.startsAt).toLocaleString()} ~ {new Date(event.endsAt).toLocaleString()} · 참가 {event.participants}명
            </p>
            <p className="mt-2 text-xs text-foreground-muted">
              기간 안에 시작해 기간 안에 끝낸 판 중 가장 좋은 한 판이 올라갑니다. 점수가 같으면 복구를 선언하기까지 걸린 시간(완전 복구만)으로 줄 세웁니다.
            </p>
          </Card>
          {event.phase === "LIVE" && (
            <Button href={`/drills/${event.scenarioId}`} className="self-start">
              참가하기 →
            </Button>
          )}
        </aside>

        <Card as="section" className="min-w-0 lg:order-1">
          <h2 className="mb-2 text-sm font-semibold">{board.debriefOpen ? "디브리프" : "보드"}</h2>
          {board.entries.length === 0 ? (
            <p className="text-sm text-foreground-muted">아직 기록이 없습니다.</p>
          ) : (
            <div className="overflow-x-auto">
              <ol className="flex min-w-[20rem] flex-col divide-y divide-border text-sm">
                {board.entries.map((e) => (
                  <li key={e.rank} className={`grid grid-cols-[2rem_1fr_4rem_6rem_5rem] items-center gap-2 py-2 ${e.mine ? "font-medium text-accent" : ""}`}>
                    <span className="text-foreground-muted">{e.rank}</span>
                    <span className="truncate">{e.nickname}</span>
                    <span className="font-mono">{e.score ?? "-"}점</span>
                    <span className="font-mono text-xs text-foreground-muted">
                      {e.resolvedSeconds !== null ? `복구 ${Math.floor(e.resolvedSeconds / 60)}분 ${e.resolvedSeconds % 60}초` : "복구 미선언"}
                    </span>
                    <span className="text-right text-xs">
                      {e.writeupSessionId && (
                        <Link href={`/writeups/${event.scenarioId}/${e.writeupSessionId}`} className="underline">
                          풀이
                        </Link>
                      )}
                    </span>
                  </li>
                ))}
              </ol>
            </div>
          )}
          {board.debriefOpen ? (
            <p className="mt-3 text-xs">
              <Link href={`/discussions/${event.scenarioId}`} className="underline">
                이 시나리오 토론에서 디브리프 이어가기 →
              </Link>
            </p>
          ) : (
            <p className="mt-3 text-xs text-foreground-muted">풀이는 챌린지가 끝난 뒤 디브리프에서 열립니다.</p>
          )}
        </Card>
      </div>
    </div>
  );
}
