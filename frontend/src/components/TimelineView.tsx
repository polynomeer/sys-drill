"use client";

import { useState } from "react";
import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { SystemState, TimelineContentBlock } from "@/lib/api";
import { actionLabel } from "@/lib/actionLabels";
import { STATE_METRICS, formatDuration } from "@/lib/metrics";

type Series = NonNullable<TimelineContentBlock["resolved"]>["series"][number];

const TONE_COLOR: Record<Series["tone"], string> = {
  none: "var(--foreground-muted)",
  bad: "var(--danger)",
  good: "var(--success)",
};

const TONE_LABEL: Record<Series["tone"], string> = { none: "방치", bad: "틀린 대응", good: "올바른 완화" };

/**
 * docs/LEARNING_DEEPENING_PLAN.md L13 — an incident on the engine's clock: how it spreads with
 * nothing done, which alert fires first, and how a Bad Fix and the right mitigation recover (or
 * don't). Every value is the backend's TelemetrySampler output; the table under the charts says
 * the same as the lines for screen readers.
 */
export function TimelineView({ block }: { block: TimelineContentBlock }) {
  const resolved = block.resolved;
  const [hidden, setHidden] = useState<Set<number>>(new Set());
  if (!resolved) return null;

  const rows = resolved.seconds.map((second, i) => {
    const row: Record<string, number> = { second };
    resolved.series.forEach((s, si) => {
      for (const m of block.metrics) row[`${si}:${m}`] = s.values[m]?.[i] ?? 0;
    });
    return row;
  });
  const visible = resolved.series.map((_, i) => !hidden.has(i));
  const toggle = (i: number) =>
    setHidden((prev) => {
      const next = new Set(prev);
      if (next.has(i)) next.delete(i);
      else next.add(i);
      return next;
    });

  return (
    <section className="flex flex-col gap-3 rounded-lg border border-border p-3">
      {block.title && <h3 className="text-sm font-semibold">{block.title}</h3>}

      {resolved.firstAlerts.length > 0 && (
        <div className="flex flex-col gap-1.5">
          <p className="text-xs text-foreground-muted">아무것도 하지 않을 때 경보가 울리는 순서</p>
          <ol className="flex flex-wrap items-center gap-1.5 text-xs">
            {resolved.firstAlerts.map((a, i) => (
              <li key={a.label} className="flex items-center gap-1.5">
                {i > 0 && <span className="text-foreground-muted" aria-hidden>→</span>}
                <span className={`rounded-full border px-2 py-0.5 ${a.second === null ? "border-border text-foreground-muted" : "border-warning"}`}>
                  <span className="font-mono">{a.second === null ? "울리지 않음" : `+${formatDuration(a.second)}`}</span> {a.label}
                </span>
              </li>
            ))}
          </ol>
        </div>
      )}

      <div className="flex flex-col gap-1.5">
        {resolved.series.map((s, i) => (
          <button
            key={s.label}
            type="button"
            onClick={() => toggle(i)}
            aria-pressed={visible[i]}
            className={`flex flex-wrap items-center gap-x-2 gap-y-0.5 rounded-md border px-2 py-1 text-left text-xs ${visible[i] ? "border-border" : "border-dashed border-border opacity-50"}`}
          >
            <span className="inline-block h-0.5 w-5 shrink-0" style={{ background: TONE_COLOR[s.tone] }} aria-hidden />
            <span className="font-medium">{s.label}</span>
            <span className="text-foreground-muted">{TONE_LABEL[s.tone]}</span>
            {s.actions.length > 0 && (
              <span className="text-foreground-muted">
                · {s.actions.map((a) => `${formatDuration(a.second)} ${actionLabel(a.action)}`).join(" → ")}
              </span>
            )}
          </button>
        ))}
      </div>

      <div className="grid gap-3 md:grid-cols-2">
        {block.metrics.map((metric) => {
          const meta = STATE_METRICS[metric];
          const fmt = meta?.format ?? ((v: number) => v.toFixed(2));
          const alert = block.alerts.find((a) => a.metric === metric);
          return (
            <figure key={metric} className="flex flex-col gap-1" aria-hidden>
              <figcaption className="text-xs text-foreground-muted">{meta?.label ?? metric}</figcaption>
              <div className="h-36">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={rows} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                    <CartesianGrid stroke="var(--border)" strokeDasharray="2 4" vertical={false} />
                    <XAxis dataKey="second" type="number" domain={["dataMin", "dataMax"]} tick={{ fontSize: 10, fill: "var(--foreground-muted)" }} tickFormatter={(s) => `${s}s`} />
                    <YAxis width={44} tick={{ fontSize: 10, fill: "var(--foreground-muted)" }} tickFormatter={(v) => fmt(Number(v))} />
                    <ReferenceLine x={0} stroke="var(--foreground-muted)" strokeDasharray="3 3" />
                    {alert && <ReferenceLine y={alert.threshold} stroke="var(--warning)" strokeDasharray="4 3" />}
                    {resolved.series.flatMap((s, si) =>
                      visible[si] ? s.actions.map((a) => <ReferenceLine key={`${si}-${a.second}`} x={a.second} stroke={TONE_COLOR[s.tone]} strokeOpacity={0.4} />) : [],
                    )}
                    <Tooltip
                      contentStyle={{ background: "var(--surface)", border: "1px solid var(--border)", fontSize: 11 }}
                      labelFormatter={(s) => `${s}초`}
                      formatter={(v, name) => [fmt(Number(v)), resolved.series[Number(String(name).split(":")[0])]?.label ?? name]}
                    />
                    {resolved.series.map((s, si) =>
                      visible[si] ? (
                        <Line key={si} type="linear" dataKey={`${si}:${metric}`} stroke={TONE_COLOR[s.tone]} strokeWidth={s.tone === "none" ? 1.25 : 2} strokeDasharray={s.tone === "none" ? "4 3" : undefined} dot={false} isAnimationActive={false} />
                      ) : null,
                    )}
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </figure>
          );
        })}
      </div>

      <table className="w-full text-xs">
        <caption className="mb-1 text-left text-foreground-muted">{formatDuration(block.durationSeconds)} 뒤 값</caption>
        <thead>
          <tr className="text-left text-foreground-muted">
            <th className="py-1 font-normal">경우</th>
            {block.metrics.map((m) => (
              <th key={m} className="py-1 text-right font-normal">{STATE_METRICS[m as keyof SystemState]?.label ?? m}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {resolved.series.map((s) => (
            <tr key={s.label} className="border-t border-border">
              <td className="py-1.5" style={{ color: TONE_COLOR[s.tone] }}>{s.label}</td>
              {block.metrics.map((m) => {
                const fmt = STATE_METRICS[m]?.format ?? ((v: number) => v.toFixed(2));
                const last = s.values[m]?.at(-1);
                return (
                  <td key={m} className="py-1.5 text-right tabular-nums">{last === undefined ? "—" : fmt(last)}</td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>

      {block.caption && <p className="text-xs text-foreground-muted">{block.caption}</p>}
      <p className="text-[11px] text-foreground-muted">
        시뮬레이션 엔진의 시계로 계산했습니다 — 장애는 90초에 걸쳐 최대치에 이르고, 조치는 적용한 초부터 반영됩니다. 선을 눌러 숨기거나 다시 볼 수 있습니다.
      </p>
    </section>
  );
}
