// Friendly labels for RuleEvaluator's riskKeys (backend/.../evaluation/RuleEvaluator.kt),
// used wherever a raw key like "MISSING_IDEMPOTENCY" would otherwise be shown as-is.
const LABELS: Record<string, string> = {
  MISSING_IDEMPOTENCY: "멱등성 처리",
  MISSING_CONCURRENCY_CONTROL: "동시성 제어",
  MISSING_RATE_LIMIT: "Rate Limit",
  MISSING_OBSERVABILITY: "관측 가능성",
  MISSING_ASYNC_BOUNDARY: "비동기 경계",
  MISSING_IDEMPOTENT_CONSUMER: "Idempotent Consumer",
  MISSING_RETRY_BACKOFF: "Retry/Backoff",
  MISSING_DLQ: "DLQ",
  MISSING_CIRCUIT_BREAKER: "Circuit Breaker",
  MISSING_CACHE_POLICY_SEPARATION: "캐시 정책 분리",
  MISSING_KEY_DISTRIBUTION: "Hot Key 분산",
  MISSING_SINGLE_FLIGHT: "Single-flight",
  MISSING_READ_REPLICA: "Read Replica",
};

export function riskLabel(riskKey: string): string {
  return LABELS[riskKey] ?? riskKey;
}
