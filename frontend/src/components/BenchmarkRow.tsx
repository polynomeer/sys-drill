import type { BenchmarkMetric } from "@/lib/api";

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.1 — 한 지표의 "나 vs 커뮤니티" 한 줄.
 *
 * 표본이 부족하면 서버가 distribution/topPercent 를 null 로 내려주므로
 * (3명이 푼 시나리오의 "상위 33%"는 노이즈다) 여기서는 내 값만 보여주고
 * 비교 칸은 안내 문구로 대체한다.
 */
export function BenchmarkRow({
  label,
  metric,
  format,
}: {
  label: string;
  metric: BenchmarkMetric;
  format: (value: number) => string;
}) {
  const { mine, sampleSize, distribution, topPercent, higherIsBetter } = metric;

  return (
    <div className="grid grid-cols-[1fr_auto_auto] items-baseline gap-x-4 gap-y-1 py-2">
      <span className="text-sm text-foreground-muted">{label}</span>
      <span className="font-mono text-sm font-semibold tabular-nums">{mine !== null ? format(mine) : "-"}</span>
      {distribution ? (
        <span className="font-mono text-xs text-foreground-muted tabular-nums">
          p50 {format(distribution.p50)} · p90 {format(distribution.p90)}
        </span>
      ) : (
        <span className="text-xs text-foreground-muted">비교 표본 부족 ({sampleSize}명)</span>
      )}
      {topPercent !== null && mine !== null && (
        <span className="col-start-2 col-end-4 text-xs text-foreground-muted">
          상위 {topPercent}%
          <span className="ml-1 opacity-70">({higherIsBetter ? "높을수록 좋음" : "낮을수록 좋음"})</span>
        </span>
      )}
    </div>
  );
}
