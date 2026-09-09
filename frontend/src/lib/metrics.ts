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
