import type { MasteryLevel } from "@/lib/api";

/** PLAN.md Round E18 (docs/LEARNING_EXPANSION_PLAN.md L7) — one vocabulary for every screen that shows mastery. */
export const MASTERY_META: Record<MasteryLevel, { symbol: string; label: string; hint: string; tone: "neutral" | "danger" | "warning" | "success" }> = {
  NOT_STARTED: { symbol: "○", label: "미시작", hint: "관련 시나리오를 아직 완료하지 않았습니다.", tone: "neutral" },
  WEAK: { symbol: "⚠", label: "약점", hint: "가장 최근 관련 세션에서 다시 지적받았습니다.", tone: "danger" },
  PRACTICED: { symbol: "◐", label: "연습함", hint: "최근엔 지적되지 않았지만 한 가지 변형에서만 확인됐습니다.", tone: "warning" },
  CONFIDENT: { symbol: "●", label: "신뢰", hint: "서로 다른 변형 2개 이상에서 지적되지 않았습니다.", tone: "success" },
};
