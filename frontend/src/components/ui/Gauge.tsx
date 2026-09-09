const STATUS_COLOR: Record<"success" | "warning" | "danger", string> = {
  success: "var(--success)",
  warning: "var(--warning)",
  danger: "var(--danger)",
};

/** SysDrill_UIUX_Design_Plan.docx §5.5 — CPU/Memory circular gauges. Plain SVG `<circle>` stroke-dasharray, no charting library (recharts is reserved for the time-series line charts). */
export function Gauge({
  label,
  value,
  status = "success",
  size = 88,
}: {
  label: string;
  value: number;
  status?: "success" | "warning" | "danger";
  size?: number;
}) {
  const stroke = 8;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const clamped = Math.min(1, Math.max(0, value));
  const offset = circumference * (1 - clamped);

  return (
    <div className="flex flex-col items-center gap-1">
      <div className="relative" style={{ width: size, height: size }}>
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90">
          <circle cx={size / 2} cy={size / 2} r={radius} stroke="var(--border)" strokeWidth={stroke} fill="none" />
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            stroke={STATUS_COLOR[status]}
            strokeWidth={stroke}
            fill="none"
            strokeDasharray={circumference}
            strokeDashoffset={offset}
            strokeLinecap="round"
            className="transition-[stroke-dashoffset] duration-500"
          />
        </svg>
        <p
          className="absolute inset-0 flex items-center justify-center font-mono text-lg font-semibold"
          style={{ color: STATUS_COLOR[status] }}
        >
          {(clamped * 100).toFixed(0)}%
        </p>
      </div>
      <p className="text-xs text-foreground-muted">{label}</p>
    </div>
  );
}
