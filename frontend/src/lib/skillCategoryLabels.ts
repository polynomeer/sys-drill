// Friendly labels for the cross-domain competency categories backend's
// RuleEvaluator.categoryByRiskKey groups riskKeys into (Skill Graph slice 1,
// docs/DRILLS_SIMULATION_VISION.md §6) — mirrors riskLabels.ts's shape, keep
// the slugs in sync with backend/.../evaluation/RuleEvaluator.kt.
const CATEGORY_LABELS: Record<string, string> = {
  CONCURRENCY_CONSISTENCY: "동시성·정합성",
  RESILIENCE: "트래픽 보호·복원력",
  CACHING_DATA_ACCESS: "캐싱·데이터 접근 전략",
  ASYNC_BATCH: "비동기·배치 처리",
  CAPACITY_TIMING: "용량·시간 제약 설계",
  OBSERVABILITY: "관측 가능성",
};

export function categoryLabel(category: string): string {
  return CATEGORY_LABELS[category] ?? category;
}
