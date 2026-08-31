// Mirrors backend SimulationEngine's utilization bands (docs/ARCHITECTURE.md §6):
// 0~60% 안정 / 60~80% latency 증가 / 80~95% p95·p99 급등 / 95~100% error 증가 / 100%+ timeout·drop.
export function utilizationColorClass(utilization: number): string {
  if (utilization < 0.6) return "text-green-600 dark:text-green-400";
  if (utilization < 0.8) return "text-yellow-600 dark:text-yellow-400";
  if (utilization < 0.95) return "text-orange-600 dark:text-orange-400";
  return "text-red-600 dark:text-red-400";
}

export function formatPercent(value: number): string {
  return `${(value * 100).toFixed(1)}%`;
}

export function formatMs(value: number): string {
  return `${value.toFixed(0)}ms`;
}

export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}초`;
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}분 ${rest}초`;
}
