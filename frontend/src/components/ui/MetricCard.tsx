import { Card } from "./Card";

type Status = "neutral" | "success" | "warning" | "danger";

const STATUS_TEXT_CLASSES: Record<Status, string> = {
  neutral: "text-foreground",
  success: "text-success",
  warning: "text-warning",
  danger: "text-danger",
};

function sparklinePoints(values: number[], width: number, height: number): string {
  if (values.length < 2) return "";
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  return values
    .map((v, i) => {
      const x = (i / (values.length - 1)) * width;
      const y = height - ((v - min) / range) * height;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");
}

/**
 * SysDrill_UIUX_Design_Plan.docx §7 — 값, 단위, 변화율, 상태, sparkline 순서.
 * Sparkline is a plain inline SVG polyline (no charting library) since this
 * only needs a small trend hint, not an interactive chart.
 */
export function MetricCard({
  label,
  value,
  unit,
  change,
  status = "neutral",
  sparkline,
  className = "",
}: {
  label: string;
  value: string | number;
  unit?: string;
  change?: string;
  status?: Status;
  sparkline?: number[];
  className?: string;
}) {
  return (
    <Card className={className}>
      <p className="text-xs text-foreground-muted">{label}</p>
      <p className={`mt-1 font-mono text-2xl font-semibold ${STATUS_TEXT_CLASSES[status]}`}>
        {value}
        {unit && <span className="ml-1 text-sm font-normal text-foreground-muted">{unit}</span>}
      </p>
      <div className="mt-2 flex items-center justify-between gap-2">
        {change && <span className={`text-xs font-medium ${STATUS_TEXT_CLASSES[status]}`}>{change}</span>}
        {sparkline && sparkline.length >= 2 && (
          <svg viewBox="0 0 64 20" width="64" height="20" className="text-foreground-muted" preserveAspectRatio="none">
            <polyline
              points={sparklinePoints(sparkline, 64, 20)}
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              className={STATUS_TEXT_CLASSES[status]}
            />
          </svg>
        )}
      </div>
    </Card>
  );
}
