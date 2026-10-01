"use client";

import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { SeriesPoint, TimelineStep } from "@/lib/api";
import { formatMs, formatPercent, utilizationColorClass } from "@/lib/metrics";
import { Card } from "@/components/ui/Card";

/**
 * docs/OBSERVABILITY_UI_PLAN.md O1 — the Observe workspace's views, all drawn from
 * the server series (ADR-0045) so a reload, a spectator and the replay see the
 * same curve. Thresholds colour every metric by the same utilization bands; no
 * view points at a cause.
 */

/** "+02:30" relative to the incident start; "-00:40" before it. */
export function relativeLabel(iso: string, incidentStartedAt: string): string {
  const seconds = Math.round((new Date(iso).getTime() - new Date(incidentStartedAt).getTime()) / 1000);
  const sign = seconds < 0 ? "-" : "+";
  const abs = Math.abs(seconds);
  return `${sign}${String(Math.floor(abs / 60)).padStart(2, "0")}:${String(abs % 60).padStart(2, "0")}`;
}

type Signal = {
  key: string;
  label: string;
  hint: string;
  pick: (p: SeriesPoint) => number;
  format: (v: number) => string;
  /** 0..1 utilization-like value for the same band colouring everywhere. */
  severity: (p: SeriesPoint) => number;
  color: string;
};

/** Google SRE's four golden signals, mapped onto fields the engine already computes. */
const GOLDEN_SIGNALS: Signal[] = [
  {
    key: "latency",
    label: "Latency",
    hint: "P95",
    pick: (p) => p.state.p95LatencyMs,
    format: formatMs,
    severity: (p) => p.state.cpuUtilization,
    color: "#a78bfa",
  },
  {
    key: "traffic",
    label: "Traffic",
    hint: "요청/초",
    pick: (p) => p.state.trafficRps,
    format: (v) => `${v.toFixed(0)}/s`,
    severity: () => 0,
    color: "#2f80ff",
  },
  {
    key: "errors",
    label: "Errors",
    hint: "에러율",
    pick: (p) => p.state.errorRate,
    format: formatPercent,
    // Error rate gets its own bands — on the utilization bands a 30% error rate would read as "warning".
    severity: (p) => (p.state.errorRate < 0.005 ? 0 : p.state.errorRate < 0.05 ? 0.7 : 1),
    color: "#ef4444",
  },
  {
    key: "saturation",
    label: "Saturation",
    hint: "가장 바쁜 자원",
    pick: (p) => p.state.cpuUtilization,
    format: formatPercent,
    severity: (p) => p.state.cpuUtilization,
    color: "#f59e0b",
  },
];

export function GoldenSignals({ points }: { points: SeriesPoint[] }) {
  const latest = points.at(-1);
  if (!latest) return null;
  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {GOLDEN_SIGNALS.map((signal) => (
        <Card key={signal.key} as="section" className="min-w-0 !p-3">
          <div className="flex items-baseline justify-between gap-2">
            <p className="text-xs font-semibold text-foreground-muted">{signal.label}</p>
            <p className="text-[10px] text-foreground-muted">{signal.hint}</p>
          </div>
          <p className={`mt-1 font-mono text-lg font-medium ${signal.key === "traffic" ? "" : utilizationColorClass(signal.severity(latest))}`}>
            {signal.format(signal.pick(latest))}
          </p>
          <ResponsiveContainer width="100%" height={36}>
            <LineChart data={points.map((p) => ({ v: signal.pick(p) }))}>
              <Line type="monotone" dataKey="v" stroke={signal.color} strokeWidth={1.5} dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </Card>
      ))}
    </div>
  );
}

/** The incident's action log, newest first — what changed, and when. */
export function RecentChanges({
  steps,
  incidentStartedAt,
  limit,
}: {
  steps: TimelineStep[];
  incidentStartedAt: string;
  limit?: number;
}) {
  const changes = [...steps].reverse().slice(0, limit ?? steps.length);
  return (
    <Card as="section">
      <h2 className="mb-2 text-sm font-semibold text-foreground-muted">{limit ? "최근 변경" : "변경 이력"}</h2>
      {changes.length === 0 ? (
        <p className="text-sm text-foreground-muted">아직 변경이 없습니다.</p>
      ) : (
        <ul className="flex flex-col gap-1.5 text-sm">
          {changes.map((step) => (
            <li key={step.step} className="flex gap-3">
              <span className="shrink-0 font-mono text-xs tabular-nums text-foreground-muted">
                {relativeLabel(step.appliedAt, incidentStartedAt)}
              </span>
              <span className="min-w-0">
                <span className="mr-1">{step.actionType ? "▲" : "⚡"}</span>
                {step.actionType ?? step.label}
                {step.actionType && <span className="block text-xs text-foreground-muted">{step.label}</span>}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

const CHART_TOOLTIP_STYLE = { background: "var(--surface-elevated)", border: "1px solid var(--border)", fontSize: 12 };

/** RPS / error-rate over the incident, on the incident clock. */
export function SeriesCharts({ points, incidentStartedAt }: { points: SeriesPoint[]; incidentStartedAt: string }) {
  if (points.length < 2) return null;
  const data = points.map((p) => ({
    t: relativeLabel(p.t, incidentStartedAt),
    rps: Math.round(p.state.trafficRps),
    errorRate: Number((p.state.errorRate * 100).toFixed(2)),
    p95: Math.round(p.state.p95LatencyMs),
    backlog: p.backlog,
  }));
  const charts: { key: keyof (typeof data)[number]; title: string; color: string }[] = [
    { key: "rps", title: "요청 수 (RPS)", color: "#2f80ff" },
    { key: "errorRate", title: "에러율 (%)", color: "#ef4444" },
    { key: "p95", title: "P95 지연 (ms)", color: "#a78bfa" },
  ];
  if (points.some((p) => p.backlog > 0)) charts.push({ key: "backlog", title: "적체 (backlog)", color: "#f59e0b" });

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {charts.map((chart) => (
        <Card key={chart.key} as="section" className="min-w-0">
          <h2 className="mb-2 text-sm font-semibold text-foreground-muted">{chart.title}</h2>
          <ResponsiveContainer width="100%" height={140}>
            <LineChart data={data}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis dataKey="t" tick={{ fill: "var(--foreground-muted)", fontSize: 10 }} stroke="var(--border)" minTickGap={24} />
              <YAxis width={44} tick={{ fill: "var(--foreground-muted)", fontSize: 10 }} stroke="var(--border)" />
              <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
              <Line type="monotone" dataKey={chart.key} stroke={chart.color} strokeWidth={2} dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </Card>
      ))}
    </div>
  );
}
