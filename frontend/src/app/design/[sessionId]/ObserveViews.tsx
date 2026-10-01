"use client";

import { useState } from "react";
import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
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

/** Seconds → "+mm:ss" on the incident clock (the charts' numeric x axis). */
function clock(seconds: number): string {
  const sign = seconds < 0 ? "-" : "+";
  const abs = Math.abs(Math.round(seconds));
  return `${sign}${String(Math.floor(abs / 60)).padStart(2, "0")}:${String(abs % 60).padStart(2, "0")}`;
}

function secondsAfter(iso: string, incidentStartedAt: string): number {
  return (new Date(iso).getTime() - new Date(incidentStartedAt).getTime()) / 1000;
}

type MetricDef = { key: string; label: string; pick: (p: SeriesPoint["state"], backlog: number) => number; color: string };

/** docs/OBSERVABILITY_UI_PLAN.md O2 — every field the engine computes, for charts and the comparison overlay. */
const METRICS: MetricDef[] = [
  { key: "rps", label: "요청 수 (RPS)", pick: (s) => Math.round(s.trafficRps), color: "#2f80ff" },
  { key: "errorRate", label: "에러율 (%)", pick: (s) => Number((s.errorRate * 100).toFixed(2)), color: "#ef4444" },
  { key: "p95", label: "P95 지연 (ms)", pick: (s) => Math.round(s.p95LatencyMs), color: "#a78bfa" },
  { key: "backlog", label: "적체 (backlog)", pick: (_s, b) => b, color: "#f59e0b" },
  { key: "cacheHit", label: "캐시 hit ratio (%)", pick: (s) => Number((s.cacheHitRatio * 100).toFixed(1)), color: "#22c55e" },
  { key: "dbRead", label: "DB 읽기 사용률 (%)", pick: (s) => Number((s.dbReadLoad * 100).toFixed(1)), color: "#06b6d4" },
  { key: "dbWrite", label: "DB 쓰기 사용률 (%)", pick: (s) => Number((s.dbWriteLoad * 100).toFixed(1)), color: "#0ea5e9" },
  { key: "pool", label: "커넥션 풀 사용률 (%)", pick: (s) => Number((s.connectionPoolUsage * 100).toFixed(1)), color: "#eab308" },
  { key: "throughput", label: "처리량 (/s)", pick: (s) => Number(s.consumerThroughput.toFixed(1)), color: "#14b8a6" },
  { key: "cpu", label: "Saturation (%)", pick: (s) => Number((s.cpuUtilization * 100).toFixed(1)), color: "#f97316" },
];

const RANGES: { key: string; label: string; seconds: number | null }[] = [
  { key: "5m", label: "최근 5분", seconds: 300 },
  { key: "15m", label: "최근 15분", seconds: 900 },
  { key: "all", label: "인시던트 전체", seconds: null },
];

/**
 * docs/OBSERVABILITY_UI_PLAN.md O2 — the incident on its own clock, with every
 * action drawn where it happened (Change Overlay), a time range, and an overlay
 * of any two metrics so the learner can line up "hit ratio fell" with "DB
 * load rose" themselves. Nothing here is highlighted as the cause.
 */
export function SeriesCharts({
  points,
  incidentStartedAt,
  steps = [],
}: {
  points: SeriesPoint[];
  incidentStartedAt: string;
  steps?: TimelineStep[];
}) {
  const [range, setRange] = useState("all");
  const [left, setLeft] = useState("errorRate");
  const [right, setRight] = useState("cpu");
  const [selected, setSelected] = useState<number | null>(null);
  if (points.length < 2) return null;

  const all = points.map((p) => {
    const row: Record<string, number> = { sec: Math.round(secondsAfter(p.t, incidentStartedAt)) };
    METRICS.forEach((m) => (row[m.key] = m.pick(p.state, p.backlog)));
    return row;
  });
  const last = all[all.length - 1].sec;
  const span = RANGES.find((r) => r.key === range)?.seconds ?? null;
  const data = span === null ? all : all.filter((row) => row.sec >= last - span);
  const markers = steps
    .map((step, index) => ({ step, index, sec: Math.round(secondsAfter(step.appliedAt, incidentStartedAt)) }))
    .filter((m) => m.sec >= (data[0]?.sec ?? 0));

  const shown = METRICS.filter((m) => ["rps", "errorRate", "p95"].includes(m.key) || (m.key === "backlog" && points.some((p) => p.backlog > 0)));
  const xAxis = (
    <XAxis
      dataKey="sec"
      type="number"
      domain={["dataMin", "dataMax"]}
      tickFormatter={clock}
      tick={{ fill: "var(--foreground-muted)", fontSize: 10 }}
      stroke="var(--border)"
      minTickGap={24}
    />
  );
  const markerLines = markers.map((m) => (
    <ReferenceLine
      key={m.step.step}
      x={m.sec}
      stroke={m.step.actionType ? "var(--accent)" : "var(--danger)"}
      strokeDasharray={m.step.actionType ? "4 3" : undefined}
    />
  ));

  const leftDef = METRICS.find((m) => m.key === left)!;
  const rightDef = METRICS.find((m) => m.key === right)!;
  const selectedStep = selected === null ? null : steps[selected];
  const previousStep = selected === null || selected === 0 ? null : steps[selected - 1];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2">
        {RANGES.map((r) => (
          <button
            key={r.key}
            type="button"
            onClick={() => setRange(r.key)}
            aria-pressed={range === r.key}
            className={`rounded-full border px-2.5 py-0.5 text-xs ${range === r.key ? "border-accent text-accent" : "border-border text-foreground-muted hover:text-foreground"}`}
          >
            {r.label}
          </button>
        ))}
        <span className="ml-auto text-[11px] text-foreground-muted">
          <span className="text-danger">│</span> 인시던트 시작 · <span className="text-accent">┆</span> 조치
        </span>
      </div>

      {markers.length > 0 && (
        <div className="flex flex-wrap gap-1.5" aria-label="변경 마커">
          {markers.map((m) => (
            <button
              key={m.step.step}
              type="button"
              onClick={() => setSelected(selected === m.index ? null : m.index)}
              aria-pressed={selected === m.index}
              className={`rounded border px-2 py-0.5 font-mono text-[11px] ${selected === m.index ? "border-accent text-accent" : "border-border text-foreground-muted hover:text-foreground"}`}
            >
              {m.step.actionType ? "▲" : "⚡"} {clock(m.sec)} {m.step.actionType ?? "인시던트 시작"}
            </button>
          ))}
        </div>
      )}

      {selectedStep && (
        <Card as="section" className="min-w-0">
          <p className="text-sm font-medium">
            {clock(secondsAfter(selectedStep.appliedAt, incidentStartedAt))} · {selectedStep.actionType ?? "인시던트 시작"}
          </p>
          <p className="mt-1 text-xs text-foreground-muted">{selectedStep.label}</p>
          {previousStep && (
            <table className="mt-3 w-full text-xs">
              <thead>
                <tr className="text-foreground-muted">
                  <th className="text-left font-normal">지표 (정상 상태 기준)</th>
                  <th className="text-right font-normal">직전</th>
                  <th className="text-right font-normal">직후</th>
                </tr>
              </thead>
              <tbody className="font-mono">
                {METRICS.filter((m) => m.key !== "backlog").map((m) => {
                  const before = m.pick(previousStep.systemState, 0);
                  const after = m.pick(selectedStep.systemState, 0);
                  if (before === after) return null;
                  return (
                    <tr key={m.key}>
                      <td className="py-0.5 font-sans">{m.label}</td>
                      <td className="text-right">{before}</td>
                      <td className="text-right">{after}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </Card>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        {shown.map((chart) => (
          <Card key={chart.key} as="section" className="min-w-0">
            <h2 className="mb-2 text-sm font-semibold text-foreground-muted">{chart.label}</h2>
            <ResponsiveContainer width="100%" height={140}>
              <LineChart data={data}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                {xAxis}
                <YAxis width={44} tick={{ fill: "var(--foreground-muted)", fontSize: 10 }} stroke="var(--border)" />
                <Tooltip contentStyle={CHART_TOOLTIP_STYLE} labelFormatter={(v) => clock(Number(v))} />
                {markerLines}
                <Line type="monotone" dataKey={chart.key} stroke={chart.color} strokeWidth={2} dot={false} isAnimationActive={false} />
              </LineChart>
            </ResponsiveContainer>
          </Card>
        ))}
      </div>

      <Card as="section" className="min-w-0">
        <div className="mb-2 flex flex-wrap items-center gap-2 text-sm">
          <h2 className="mr-2 font-semibold text-foreground-muted">겹쳐 보기</h2>
          <MetricSelect value={left} onChange={setLeft} />
          <span className="text-foreground-muted">×</span>
          <MetricSelect value={right} onChange={setRight} />
        </div>
        <ResponsiveContainer width="100%" height={180}>
          <LineChart data={data}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
            {xAxis}
            <YAxis yAxisId="l" width={44} tick={{ fill: leftDef.color, fontSize: 10 }} stroke="var(--border)" />
            <YAxis yAxisId="r" orientation="right" width={44} tick={{ fill: rightDef.color, fontSize: 10 }} stroke="var(--border)" />
            <Tooltip contentStyle={CHART_TOOLTIP_STYLE} labelFormatter={(v) => clock(Number(v))} />
            {markers.map((m) => (
              <ReferenceLine key={m.step.step} yAxisId="l" x={m.sec} stroke="var(--border)" />
            ))}
            <Line yAxisId="l" type="monotone" dataKey={left} name={leftDef.label} stroke={leftDef.color} strokeWidth={2} dot={false} isAnimationActive={false} />
            <Line yAxisId="r" type="monotone" dataKey={right} name={rightDef.label} stroke={rightDef.color} strokeWidth={2} dot={false} isAnimationActive={false} />
          </LineChart>
        </ResponsiveContainer>
      </Card>
    </div>
  );
}

function MetricSelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="min-w-0 rounded border border-border bg-transparent px-2 py-1 text-xs"
      aria-label="비교할 지표"
    >
      {METRICS.map((m) => (
        <option key={m.key} value={m.key}>
          {m.label}
        </option>
      ))}
    </select>
  );
}
