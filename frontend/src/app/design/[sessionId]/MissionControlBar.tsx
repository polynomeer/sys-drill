"use client";

import { useEffect, useState } from "react";
import type { HealthStatus, SeriesPoint, SloStatus } from "@/lib/api";
import { formatMs, formatPercent } from "@/lib/metrics";

/**
 * docs/OBSERVABILITY_UI_PLAN.md O1 — the strip that stays on top of the incident
 * screen whatever tab is open. The status is computed by the server from the
 * series (ADR-0045), not re-derived here.
 *
 * Deliberately no score and no "likely cause" — the plan's two hard rules
 * (Hidden Score, "증상은 보여주되 원인은 알려주지 않는다").
 */
const STATUS_META: Record<HealthStatus, { label: string; className: string }> = {
  HEALTHY: { label: "HEALTHY", className: "bg-success/15 text-success" },
  DEGRADED: { label: "DEGRADED", className: "bg-warning/15 text-warning" },
  CRITICAL: { label: "CRITICAL", className: "bg-danger/15 text-danger" },
  RECOVERING: { label: "RECOVERING", className: "bg-accent/15 text-accent" },
  RECOVERED: { label: "RECOVERED", className: "bg-success/15 text-success" },
};

function formatElapsed(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mmss = `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
  return h > 0 ? `${h}:${mmss}` : mmss;
}

export function MissionControlBar({
  title,
  latest,
  incidentStartedAt,
  slo,
  firingAlerts = 0,
  onAlertsClick,
}: {
  title: string;
  latest: SeriesPoint | null;
  incidentStartedAt: string | null;
  /** PLAN.md Round E12 (M3) — the learner's SLO against the latest point. */
  slo?: SloStatus | null;
  firingAlerts?: number;
  onAlertsClick?: () => void;
}) {
  // Ticks every second between the 3s polls so the clock doesn't stutter.
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  if (!latest || !incidentStartedAt) return null;
  const meta = STATUS_META[latest.status];
  const s = latest.state;
  const signals: { label: string; value: string }[] = [
    { label: "RPS", value: s.trafficRps >= 1000 ? `${(s.trafficRps / 1000).toFixed(1)}K` : s.trafficRps.toFixed(0) },
    { label: "P95", value: formatMs(s.p95LatencyMs) },
    { label: "Errors", value: formatPercent(s.errorRate) },
    { label: "Availability", value: formatPercent(s.availability) },
  ];

  return (
    <div
      role="status"
      aria-label="시스템 상태"
      className="sticky top-2 z-10 flex flex-wrap items-center gap-x-5 gap-y-2 rounded-xl border border-border bg-surface/95 px-4 py-3 backdrop-blur"
    >
      <span className="min-w-0 truncate text-sm font-semibold">{title}</span>
      <span className={`rounded-full px-2.5 py-0.5 font-mono text-xs font-semibold tracking-wide ${meta.className}`}>
        ● {meta.label}
      </span>
      <span className="font-mono text-sm tabular-nums text-foreground-muted" title="인시던트 경과 시간">
        {formatElapsed(now - new Date(incidentStartedAt).getTime())}
      </span>
      <div className="flex flex-wrap gap-x-5 gap-y-1 sm:ml-auto">
        {signals.map((signal) => (
          <span key={signal.label} className="flex items-baseline gap-1.5">
            <span className="text-[11px] uppercase tracking-wide text-foreground-muted">{signal.label}</span>
            <span className="font-mono text-sm tabular-nums">{signal.value}</span>
          </span>
        ))}
        {slo && (
          <span className="flex items-baseline gap-1.5" title="SLO (Alerts 탭에서 설정)">
            <span className="text-[11px] uppercase tracking-wide text-foreground-muted">SLO</span>
            <span className="font-mono text-xs">
              <span className={slo.availabilityMet ? "text-success" : "text-danger"}>Avail{slo.availabilityMet ? "✓" : "✕"}</span>{" "}
              <span className={slo.p95Met ? "text-success" : "text-danger"}>P95{slo.p95Met ? "✓" : "✕"}</span>{" "}
              <span className={slo.errorRateMet ? "text-success" : "text-danger"}>Err{slo.errorRateMet ? "✓" : "✕"}</span>
            </span>
          </span>
        )}
        {firingAlerts > 0 && (
          <button type="button" onClick={onAlertsClick} className="flex items-baseline gap-1.5 text-danger">
            <span className="text-[11px] uppercase tracking-wide">Alerts</span>
            <span className="font-mono text-sm">{firingAlerts}</span>
          </button>
        )}
        {latest.backlog > 0 && (
          <span className="flex items-baseline gap-1.5">
            <span className="text-[11px] uppercase tracking-wide text-foreground-muted">Backlog</span>
            <span className="font-mono text-sm tabular-nums">{latest.backlog.toLocaleString()}</span>
          </span>
        )}
      </div>
    </div>
  );
}
