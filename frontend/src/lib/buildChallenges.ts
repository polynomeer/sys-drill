/**
 * PLAN.md Round E2 (docs/LEARNING_EXPANSION_PLAN.md L4-b) — Build 챌린지 7개를
 * `/bridge` 하나에서 푼다. 같은 과제의 언어별 판(rate-limiter / rate-limiter-ts)은
 * 하나의 "과제"로 묶고, 각 과제를 그 메커니즘이 실제로 쓰이는 시뮬레이션 도메인에
 * 연결한다 — 근거는 그 도메인의 워게임 액션이다(`SimulationActionType`).
 */

export type BuildLanguage = "python" | "typescript";

export interface ChallengeFamily {
  family: string;
  title: string;
  /** 이 과제 다음에 이어지는 Drill의 도메인 (Bridge Mode). */
  domain: string;
}

export const CHALLENGE_FAMILIES: ChallengeFamily[] = [
  { family: "rate-limiter", title: "Rate Limiter", domain: "coupon" }, // STRENGTHEN_RATE_LIMIT
  { family: "queue", title: "Queue", domain: "notification" }, // ADD_CONSUMERS
  { family: "circuit-breaker", title: "Circuit Breaker", domain: "notification" }, // ENABLE_CIRCUIT_BREAKER
  { family: "retry-backoff", title: "Retry / Backoff", domain: "notification" }, // ADJUST_RETRY_BACKOFF
  { family: "distributed-lock", title: "Distributed Lock", domain: "reservation" }, // ENABLE_FINE_GRAINED_LOCKING
  { family: "event-bus", title: "Event Bus", domain: "payment" }, // outbox dispatcher (ADD_DISPATCHER_WORKERS)
];

/** `rate-limiter-ts` → `rate-limiter`. 언어별 판은 `-ts` 접미사로만 구분된다(V44). */
export function familyOf(slug: string): string {
  return slug.replace(/-ts$/, "");
}

export function slugFor(family: string, language: BuildLanguage): string {
  return language === "typescript" ? `${family}-ts` : family;
}

export function familyInfo(family: string): ChallengeFamily | undefined {
  return CHALLENGE_FAMILIES.find((f) => f.family === family);
}

/** 트랙 페이지 — 이 도메인으로 이어지는 Build 과제들. */
export function buildsForDomain(domain: string): { title: string; href: string }[] {
  return CHALLENGE_FAMILIES.filter((f) => f.domain === domain).map((f) => ({
    title: `${f.title} 구현`,
    href: `/bridge?challenge=${f.family}`,
  }));
}
