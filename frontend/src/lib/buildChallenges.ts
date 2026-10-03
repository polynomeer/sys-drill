/**
 * PLAN.md Round E2 (docs/LEARNING_EXPANSION_PLAN.md L4-b) — Build 과제 전부를
 * `/bridge` 하나에서 푼다. 같은 과제의 언어별 판(rate-limiter / rate-limiter-ts / -java …)은
 * 하나의 "과제"로 묶고, 각 과제를 그 메커니즘이 실제로 쓰이는 시뮬레이션 도메인에
 * 연결한다 — 근거는 그 도메인의 워게임 액션이다(`SimulationActionType`).
 */

export type BuildLanguage = "python" | "typescript" | "java" | "kotlin" | "go";

/**
 * 언어별 판의 slug 접미사와 표시 이름. Python이 기본 판이라 접미사가 없다(V9), 나머지는
 * V44(TypeScript)·V72(Java·Kotlin·Go)의 slug. `language` 값은 백엔드 `BuildChallenge.languages`와 같다.
 */
export const BUILD_LANGUAGES: { language: BuildLanguage; suffix: string; label: string }[] = [
  { language: "python", suffix: "", label: "Python" },
  { language: "typescript", suffix: "-ts", label: "TypeScript" },
  { language: "java", suffix: "-java", label: "Java" },
  { language: "kotlin", suffix: "-kotlin", label: "Kotlin" },
  { language: "go", suffix: "-go", label: "Go" },
];

export function languageLabel(language: BuildLanguage): string {
  return BUILD_LANGUAGES.find((l) => l.language === language)?.label ?? language;
}

/** 백엔드가 돌려주는 `language` 문자열 → 알려진 언어. 모르는 값은 기본 판(Python)으로 본다. */
export function toBuildLanguage(value: string): BuildLanguage {
  return BUILD_LANGUAGES.find((l) => l.language === value)?.language ?? "python";
}

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
  { family: "cache", title: "Cache", domain: "product-browsing" }, // ENABLE_SINGLE_FLIGHT, INCREASE_CACHE_TTL
  { family: "idempotency", title: "Idempotency Layer", domain: "payment" }, // ENABLE_IDEMPOTENT_PG_RETRY
  { family: "consistent-hashing", title: "Consistent Hashing", domain: "product-browsing" }, // no action — Hot Key 분산 concept
  { family: "outbox", title: "Transactional Outbox", domain: "payment" }, // payment's outbox backlog (ADD_DISPATCHER_WORKERS)
];

/** `rate-limiter-ts` → `rate-limiter`. 언어별 판은 slug 접미사로만 구분된다(V44·V72). */
export function familyOf(slug: string): string {
  const variant = BUILD_LANGUAGES.find((l) => l.suffix && slug.endsWith(l.suffix));
  return variant ? slug.slice(0, -variant.suffix.length) : slug;
}

/** `rate-limiter-go` → `go`. 접미사가 없으면 기본 판(Python). */
export function languageOfSlug(slug: string): BuildLanguage {
  return BUILD_LANGUAGES.find((l) => l.suffix && slug.endsWith(l.suffix))?.language ?? "python";
}

export function slugFor(family: string, language: BuildLanguage): string {
  const suffix = BUILD_LANGUAGES.find((l) => l.language === language)?.suffix ?? "";
  return `${family}${suffix}`;
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
