"use client";

import { Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { PuzzlePoint } from "@/lib/api";

const METRICS: { key: keyof PuzzlePoint; label: string; unit: string }[] = [
  { key: "trafficRps", label: "트래픽", unit: "req/s" },
  { key: "p95LatencyMs", label: "P95 지연", unit: "ms" },
  { key: "errorRatePct", label: "에러율", unit: "%" },
  { key: "dbWriteLoadPct", label: "DB 쓰기 부하", unit: "%" },
  { key: "dbReadLoadPct", label: "DB 읽기 부하", unit: "%" },
  { key: "poolUsagePct", label: "커넥션 풀 사용률", unit: "%" },
  { key: "cacheHitPct", label: "캐시 적중률", unit: "%" },
  { key: "cacheLatencyMs", label: "캐시 지연", unit: "ms" },
  { key: "queueLag", label: "큐 적체", unit: "건" },
  { key: "externalLatencyMs", label: "외부 의존성 지연", unit: "ms" },
];

/**
 * PLAN.md Round E28 (L9 / C12) — the puzzle's metrics as small multiples. A metric the system
 * doesn't have (flat zero throughout) is shown as "—" rather than hidden: absence is a clue too.
 */
export function PuzzleCharts({ points }: { points: PuzzlePoint[] }) {
  const last = points.at(-1);
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
      {METRICS.map((m) => {
        const values = points.map((p) => Number(p[m.key]));
        const flat = values.every((v) => v === 0);
        return (
          <div key={m.key} className="rounded-lg border border-border p-2">
            <p className="text-[11px] text-foreground-muted">{m.label}</p>
            <p className="font-mono text-sm">{flat || !last ? "—" : `${format(Number(last[m.key]))}${m.unit === "%" ? "%" : ` ${m.unit}`}`}</p>
            <div className="h-10">
              {!flat && (
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={points} margin={{ top: 2, right: 2, bottom: 2, left: 2 }}>
                    <XAxis dataKey="second" hide />
                    <YAxis hide domain={["auto", "auto"]} />
                    <ReferenceLine x={0} stroke="var(--border)" strokeDasharray="3 3" />
                    <Tooltip formatter={(v) => format(Number(v))} labelFormatter={(s) => `${s}초`} />
                    <Line type="monotone" dataKey={m.key} stroke="var(--accent)" dot={false} strokeWidth={1.5} isAnimationActive={false} />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function format(v: number): string {
  return v >= 100 ? Math.round(v).toLocaleString() : v.toFixed(1);
}

/** A row of pill choices; after answering the accepted ones turn green and a wrong pick red. */
export function Choices({
  options,
  value,
  onChange,
  disabled,
  result,
}: {
  options: { key: string; label: string }[];
  value: string | null;
  onChange: (key: string) => void;
  disabled: boolean;
  /** After answering: the accepted keys, highlighted. */
  result: string[] | null;
}) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {options.map((o) => {
        const right = result?.includes(o.key);
        const picked = value === o.key;
        return (
          <button
            key={o.key}
            type="button"
            disabled={disabled}
            onClick={() => onChange(o.key)}
            className={`rounded-full border px-3 py-1 text-xs ${
              right ? "border-success text-success" : picked ? (result ? "border-danger text-danger" : "border-accent text-foreground") : "border-border text-foreground-muted"
            }`}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
