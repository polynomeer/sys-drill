// 역량 카테고리 6개의 한국어 이름. 슬러그는 backend 의
// RuleEvaluator.categoryByRiskKey 가 정하므로 그쪽과 맞춰 유지한다
// (Skill Graph slice 1, docs/DRILLS_SIMULATION_VISION.md §6).
//
// 개념(riskKey) 라벨과 달리 이 6개는 프론트 상수로 남는다 — ADR-0039 가 DB 로
// 옮긴 것은 개념 *콘텐츠* 이고, 카테고리 이름은 채점 분류의 표시용 이름이라
// 서버도 LearningService.CATEGORY_LABELS 로 같은 값을 들고 있다.
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
