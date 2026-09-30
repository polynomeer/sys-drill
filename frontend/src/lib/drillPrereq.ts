import type { SessionSummary } from "@/lib/api";

/** Drill Map 난이도 선행 추천 슬라이스 — 3단계 전순서가 이번 슬라이스의 "그래프" 전부.
 * 세션 시작을 막지 않는 advisory 힌트에만 쓰인다(ADR-0030과 같은 철학).
 * Home 목록과 Drill 개요 페이지가 같은 판단을 공유한다. */
export const TIER_ORDER = ["EASY", "MEDIUM", "HARD"];

export function completedTiers(sessions: SessionSummary[]): Set<string> {
  return new Set(sessions.filter((s) => s.status === "COMPLETED" && s.difficulty).map((s) => s.difficulty!));
}

export function needsPrereq(difficulty: string | null | undefined, completed: Set<string>): boolean {
  if (!difficulty) return false;
  const tierIndex = TIER_ORDER.indexOf(difficulty);
  if (tierIndex <= 0) return false; // 알 수 없는 값이거나 이미 최하위 티어면 힌트 없음
  return !TIER_ORDER.slice(0, tierIndex).some((lowerTier) => completed.has(lowerTier));
}

/** docs/CODECRAFTERS_BENCHMARK.md §3.4 — the pinned "첫 Drill": the first
 * official scenario (no org, no marketplace creator) in the lowest tier.
 * Chosen from data, not a hard-coded id, so re-tiering scenarios in a
 * migration (as V43 did) moves the pin with it. */
export function pickFirstDrill<T extends { difficulty: string | null; organizationId: string | null; creatorNickname?: string | null }>(
  scenarios: T[],
): T | undefined {
  const official = scenarios.filter((s) => !s.organizationId && !s.creatorNickname);
  for (const tier of TIER_ORDER) {
    const match = official.find((s) => s.difficulty === tier);
    if (match) return match;
  }
  return undefined;
}
