import type { SystemState } from "@/lib/api";

// Mirrors backend SimulationEngine's utilization bands (docs/ARCHITECTURE.md §6):
// 0~60% 안정 / 60~95% latency·p95 증가 / 95%+ error 증가·timeout·drop. Collapsed to
// the design system's 3-color status vocabulary (success/warning/danger) —
// SysDrill_UIUX_Design_Plan.docx §3 defines exactly those three, not four.
export function utilizationColorClass(utilization: number): string {
  if (utilization < 0.6) return "text-success";
  if (utilization < 0.95) return "text-warning";
  return "text-danger";
}

export function utilizationStatus(utilization: number): "success" | "warning" | "danger" {
  if (utilization < 0.6) return "success";
  if (utilization < 0.95) return "warning";
  return "danger";
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

/** Labels and formats for engine metrics — shared by the engine labs and content blocks so both say the same thing. */
export const STATE_METRICS: Partial<Record<keyof SystemState, { label: string; format: (v: number) => string }>> = {
  trafficRps: { label: "트래픽", format: (v) => `${v.toFixed(0)}/s` },
  p95LatencyMs: { label: "P95 지연", format: formatMs },
  errorRate: { label: "에러율", format: formatPercent },
  availability: { label: "가용성", format: formatPercent },
  dbReadLoad: { label: "DB 읽기 사용률", format: formatPercent },
  dbWriteLoad: { label: "DB 쓰기 사용률", format: formatPercent },
  connectionPoolUsage: { label: "커넥션 풀 사용률", format: formatPercent },
  cacheHitRatio: { label: "캐시 hit ratio", format: formatPercent },
  cacheLatencyMs: { label: "캐시 지연", format: formatMs },
  queueLag: { label: "적체 / 대기", format: (v) => v.toLocaleString() },
  consumerThroughput: { label: "처리 용량", format: (v) => `${v.toFixed(1)}/s` },
  externalDependencyLatencyMs: { label: "외부 의존성 지연", format: formatMs },
  cpuUtilization: { label: "CPU 사용률", format: formatPercent },
  memoryUtilization: { label: "메모리 사용률", format: formatPercent },
};

export type Direction = "UP" | "SAME" | "DOWN";

/** Within 2% of the previous value counts as "about the same" — the backend's `Direction.of` uses the same band. */
export function directionOf(before: number, after: number): Direction {
  const scale = Math.max(Math.abs(before), 1e-9);
  const change = (after - before) / scale;
  if (Math.abs(change) < 0.02) return "SAME";
  return change > 0 ? "UP" : "DOWN";
}
