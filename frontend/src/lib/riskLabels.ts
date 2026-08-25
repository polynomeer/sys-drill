// Friendly labels for RuleEvaluator's riskKeys (backend/.../evaluation/RuleEvaluator.kt),
// used wherever a raw key like "MISSING_IDEMPOTENCY" would otherwise be shown as-is.
const LABELS: Record<string, string> = {
  MISSING_IDEMPOTENCY: "멱등성 처리",
  MISSING_CONCURRENCY_CONTROL: "동시성 제어",
  MISSING_RATE_LIMIT: "Rate Limit",
  MISSING_OBSERVABILITY: "관측 가능성",
};

export function riskLabel(riskKey: string): string {
  return LABELS[riskKey] ?? riskKey;
}
