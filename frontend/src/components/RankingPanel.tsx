"use client";

import { useEffect, useState } from "react";
import type { MyRanking, RankingBoard } from "@/lib/api";
import { getMyRanking, getRankingBoard, setRankingVisibility } from "@/lib/api";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { LoadingState } from "@/components/ui/LoadingState";

type BoardKey = "overall" | "recent";

const BOARD_TABS: { key: BoardKey; label: string; empty: string }[] = [
  { key: "overall", label: "종합", empty: "아직 집계된 참여자가 없습니다." },
  { key: "recent", label: "최근 30일 상승", empty: "최근 30일 동안 점수가 오른 사람이 아직 없습니다." },
];

/** 티어 색은 Badge 의 기존 variant 를 재사용한다 — 랭킹만의 색 체계를 새로 만들지 않는다. */
const TIER_VARIANT: Record<string, "neutral" | "success" | "warning" | "accent"> = {
  TRAINEE: "neutral",
  OPERATOR: "neutral",
  RESPONDER: "accent",
  ARCHITECT: "success",
  PRINCIPAL: "warning",
};

/**
 * ADR-0042 / docs/LEARNING_COMMUNITY_PLAN.md §6.6 — Drill Score 와 랭킹.
 *
 * 화면이 지켜야 할 두 가지가 있다. 하나는 **점수 옆에 항상 계산 근거를 두는 것**
 * — 설명할 수 없는 점수는 훈련 동기가 아니라 불신이 된다. 다른 하나는 **티어와
 * 백분위를 순위보다 앞세우는 것** — "37등"은 대부분에게 아무 말도 해주지 않지만
 * "상위 30% · 대응자, 다음 티어까지 82점"은 다음에 뭘 할지를 말해준다.
 */
/**
 * 순위 문장. 서버는 "아직 순위가 없는" 경우(완료한 시나리오 없음 / 노출 거부)
 * rank 와 topPercent 를 모두 비운다 — 화면은 그 둘이 함께 있을 때만 순위를
 * 말하고, 아니면 왜 없는지를 말한다.
 */
function standingText(mine: MyRanking): string {
  if (typeof mine.rank === "number" && typeof mine.topPercent === "number") {
    // 1등이 "상위 0%" 가 되지 않도록 최소 1% 로 읽는다.
    return `상위 ${Math.max(mine.topPercent, 1)}% · ${mine.participantCount}명 중 ${mine.rank}위`;
  }
  if (mine.optedOut) return "랭킹에서 숨겨져 있습니다 (점수는 그대로 쌓입니다)";
  if (mine.score === 0) return "공식 시나리오를 하나 완료하면 순위가 생깁니다";
  return "아직 비교할 참여자가 없습니다";
}

export function RankingPanel() {
  const [mine, setMine] = useState<MyRanking | null>(null);
  const [board, setBoard] = useState<BoardKey>("overall");
  const [rows, setRows] = useState<RankingBoard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getMyRanking()
      .then(setMine)
      .catch(() => setError("점수를 불러오지 못했습니다."));
  }, []);

  useEffect(() => {
    setRows(null);
    getRankingBoard(board)
      .then(setRows)
      .catch(() => setRows({ board: "OVERALL", entries: [], participantCount: 0 }));
  }, [board]);

  async function toggleVisibility() {
    if (!mine || saving) return;
    setSaving(true);
    try {
      const updated = await setRankingVisibility(!mine.optedOut);
      setMine(updated);
      // 내가 보드에서 빠지거나 다시 들어왔으니 목록도 다시 읽는다.
      setRows(await getRankingBoard(board));
    } catch {
      setError("설정을 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  if (error && !mine) return <Card as="section"><p className="text-sm text-danger">{error}</p></Card>;
  if (!mine) return <Card as="section"><LoadingState /></Card>;

  const tierVariant = TIER_VARIANT[mine.tier] ?? "neutral";
  const activeTab = BOARD_TABS.find((t) => t.key === board)!;

  return (
    <Card as="section">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold">Drill Score</h2>
        <button
          type="button"
          onClick={toggleVisibility}
          disabled={saving}
          className="text-xs text-foreground-muted underline underline-offset-2 disabled:opacity-50"
        >
          {mine.optedOut ? "랭킹에 내 닉네임 표시하기" : "랭킹에서 내 닉네임 숨기기"}
        </button>
      </div>

      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <span className="text-3xl font-semibold tabular-nums">{mine.score}</span>
        <Badge variant={tierVariant}>{mine.tierLabel}</Badge>
        <span className="text-sm text-foreground-muted">{standingText(mine)}</span>
      </div>

      {mine.pointsToNextTier !== null && mine.pointsToNextTier !== undefined && (
        <p className="mt-1 text-xs text-foreground-muted">
          다음 티어 <b>{mine.nextTierLabel}</b>까지 {mine.pointsToNextTier}점 — 아직 풀지 않은 도메인을 하나
          끝내는 것이 같은 도메인을 다시 푸는 것보다 빠릅니다.
        </p>
      )}

      <div className="mt-4">
        <h3 className="mb-1 text-xs font-semibold text-foreground-muted">점수 계산 근거</h3>
        {mine.breakdown.length === 0 ? (
          <p className="text-sm text-foreground-muted">
            아직 완료한 공식 시나리오가 없습니다. 하나를 끝까지 마치면 점수가 생깁니다.
          </p>
        ) : (
          <ul className="divide-y divide-border">
            {mine.breakdown.map((best) => (
              <li key={best.domain} className="flex flex-wrap items-baseline justify-between gap-2 py-1.5 text-sm">
                <span className="flex flex-wrap items-baseline gap-2">
                  {best.title}
                  <Badge>{best.difficulty}</Badge>
                </span>
                <span className="text-xs text-foreground-muted tabular-nums">
                  최고 {best.bestScore}점 × {best.weight.toFixed(1)} = {best.points}점
                </span>
              </li>
            ))}
          </ul>
        )}
        <p className="mt-2 text-xs text-foreground-muted">
          도메인별 <b>최고 세션 점수</b>만 난이도 가중해 합산합니다 — 같은 시나리오를 반복해도 점수는 오르지
          않고, 아직 해보지 않은 도메인이 가장 큰 상승 여지입니다.
        </p>
      </div>

      <div className="mt-4">
        <div className="mb-1 flex flex-wrap items-center gap-2">
          {BOARD_TABS.map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setBoard(tab.key)}
              className={`rounded px-2 py-0.5 text-xs ${
                board === tab.key ? "bg-accent/15 text-accent" : "text-foreground-muted hover:text-foreground"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
        {rows === null ? (
          <LoadingState />
        ) : rows.entries.length === 0 ? (
          <p className="text-sm text-foreground-muted">{activeTab.empty}</p>
        ) : (
          <ol className="divide-y divide-border">
            {rows.entries.map((entry) => (
              <li
                key={`${entry.rank}-${entry.nickname}`}
                className={`flex items-baseline justify-between gap-2 py-1.5 text-sm ${
                  entry.isMe ? "font-semibold text-accent" : ""
                }`}
              >
                <span className="flex items-baseline gap-2">
                  <span className="w-6 shrink-0 text-right text-xs text-foreground-muted tabular-nums">
                    {entry.rank}
                  </span>
                  {entry.nickname}
                  <span className="text-xs text-foreground-muted">{entry.tierLabel}</span>
                </span>
                <span className="text-xs tabular-nums text-foreground-muted">
                  {board === "recent" ? `+${entry.score}` : entry.score}점
                </span>
              </li>
            ))}
          </ol>
        )}
      </div>
    </Card>
  );
}
