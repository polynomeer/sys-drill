"use client";

import { useEffect, useState } from "react";
import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import {
  type EngineKnob,
  type EngineLab,
  type LabGoal,
  type SystemState,
  type TimelineContentBlock,
  replayEngineLab,
  runEngineLab,
  sweepEngineLab,
} from "@/lib/api";
import { STATE_METRICS, directionOf, formatDuration } from "@/lib/metrics";
import { SystemDiagram } from "@/components/SystemDiagram";
import { TimelineView } from "@/components/TimelineView";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

/**
 * docs/LEARNING_DEEPENING_PLAN.md L15 (PLAN.md Round E39) — the engine lab's other ways to
 * experiment, all on the same knobs: live (the picture repaints as you turn a knob, pin A and
 * compare), goals, replay on the incident's clock, and a sweep across one knob's range. Every
 * number is the backend engine's; nothing here is recorded.
 */

export type KnobValues = Record<string, number | boolean>;

const ARROW = { UP: "↑", SAME: "≈", DOWN: "↓" } as const;

function fmtMetric(metric: keyof SystemState, value: number): string {
  return STATE_METRICS[metric]?.format(value) ?? value.toFixed(2);
}

/** The engine state for these settings, re-run shortly after the knobs stop moving. */
function useLiveState(slug: string, values: KnobValues, incident: boolean): SystemState | null {
  const [state, setState] = useState<SystemState | null>(null);
  const key = JSON.stringify(values);
  useEffect(() => {
    let cancelled = false;
    const timer = setTimeout(() => {
      runEngineLab(slug, JSON.parse(key), incident)
        .then((s) => !cancelled && setState(s))
        .catch(() => !cancelled && setState(null));
    }, 200);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [slug, key, incident]);
  return state;
}

// ---- 자유 실험 · 비교 · 목표 ----

export function FreePanel({ lab, values, incident }: { lab: EngineLab; values: KnobValues; incident: boolean }) {
  const live = useLiveState(lab.slug, values, incident);
  const [pinned, setPinned] = useState<{ values: KnobValues; state: SystemState; incident: boolean } | null>(null);
  if (!live) return <p className="text-sm text-foreground-muted">계산하는 중…</p>;

  const comparing = pinned && pinned.incident === incident ? pinned : null;
  return (
    <div className="flex flex-col gap-4">
      <Card as="section">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-semibold text-foreground-muted">
            {comparing ? "A와 지금 비교" : "손잡이를 움직이면 바로 다시 계산합니다"}
          </h2>
          <div className="flex gap-2">
            <Button size="sm" variant="secondary" onClick={() => setPinned({ values, state: live, incident })}>
              {comparing ? "지금 값을 A로 다시 고정" : "지금 값을 A로 고정"}
            </Button>
            {comparing && (
              <Button size="sm" variant="secondary" onClick={() => setPinned(null)}>
                비교 끝내기
              </Button>
            )}
          </div>
        </div>
        <div className={`grid gap-3 ${comparing ? "lg:grid-cols-2" : ""}`}>
          {comparing && <SystemDiagram domain={lab.domain} state={comparing.state} caption={`A — ${settingsLabel(lab.knobs, comparing.values)}`} />}
          <SystemDiagram domain={lab.domain} state={live} caption={comparing ? `지금 — ${settingsLabel(lab.knobs, values)}` : undefined} />
        </div>
        <table className="mt-3 w-full text-sm">
          <thead>
            <tr className="text-xs text-foreground-muted">
              <th className="text-left font-normal">지표</th>
              {comparing && <th className="text-right font-normal">A</th>}
              <th className="text-right font-normal">지금</th>
              {comparing && <th className="w-10 text-center font-normal">차이</th>}
            </tr>
          </thead>
          <tbody>
            {lab.watch.map((m) => {
              const now = Number(live[m]);
              const a = comparing ? Number(comparing.state[m]) : null;
              const dir = a === null ? null : directionOf(a, now);
              return (
                <tr key={m} className="border-t border-border">
                  <td className="py-1.5">{STATE_METRICS[m]?.label ?? m}</td>
                  {a !== null && <td className="text-right font-mono text-foreground-muted">{fmtMetric(m, a)}</td>}
                  <td className="text-right font-mono">{fmtMetric(m, now)}</td>
                  {dir && (
                    <td className="text-center text-foreground-muted" aria-label={{ UP: "증가", SAME: "변화 없음", DOWN: "감소" }[dir]}>
                      {ARROW[dir]}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>
      {lab.goals.length > 0 && <GoalsCard lab={lab} values={values} incident={incident} live={live} />}
    </div>
  );
}

function settingsLabel(knobs: EngineKnob[], values: KnobValues): string {
  return knobs.map((k) => (k.type === "boolean" ? `${k.label} ${values[k.trait] ? "켬" : "끔"}` : `${k.label} ${values[k.trait]}`)).join(", ");
}

/** Mirrors backend GoalCondition.holds. */
function holds(op: LabGoal["conditions"][number]["op"], actual: number | boolean | undefined, value: number | boolean): boolean {
  if (actual === undefined) return false;
  if (op === "==") return actual === value;
  const a = Number(actual);
  const v = Number(value);
  return op === "<=" ? a <= v : op === ">=" ? a >= v : op === "<" ? a < v : a > v;
}

const OP_TEXT: Record<LabGoal["conditions"][number]["op"], string> = { "<=": "이하", ">=": "이상", "<": "미만", ">": "초과", "==": "" };

function GoalsCard({ lab, values, incident, live }: { lab: EngineLab; values: KnobValues; incident: boolean; live: SystemState }) {
  const [hints, setHints] = useState<Set<string>>(new Set());
  const knobByTrait = Object.fromEntries(lab.knobs.map((k) => [k.trait, k]));
  return (
    <Card as="section">
      <h2 className="mb-1 text-sm font-semibold text-foreground-muted">목표 과제</h2>
      <p className="mb-3 text-xs text-foreground-muted">손잡이만으로 조건을 모두 만족시켜 보세요. 엔진으로 풀리는 것이 확인된 목표만 있습니다.</p>
      <ul className="flex flex-col gap-3">
        {lab.goals.map((goal) => {
          const judged = goal.incident === incident;
          const checks = goal.conditions.map((c) => {
            const actual = c.metric ? Number(live[c.metric]) : c.trait ? values[c.trait] : undefined;
            const label = c.metric
              ? `${STATE_METRICS[c.metric]?.label ?? c.metric} ${fmtMetric(c.metric, Number(c.value))} ${OP_TEXT[c.op]}`
              : typeof c.value === "boolean"
                ? `${knobByTrait[c.trait!]?.label ?? c.trait} ${c.value ? "켬" : "끔"}`
                : `${knobByTrait[c.trait!]?.label ?? c.trait} ${c.value} ${OP_TEXT[c.op]}`;
            return { label, ok: judged && holds(c.op, actual, c.value) };
          });
          const done = checks.every((c) => c.ok);
          return (
            <li key={goal.id} className={`rounded-lg border p-3 ${done ? "border-success" : "border-border"}`}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm font-medium">{goal.label}</p>
                <span className={`text-xs font-semibold ${done ? "text-success" : "text-foreground-muted"}`}>{done ? "달성 ✓" : "미달성"}</span>
              </div>
              <ul className="mt-2 flex flex-wrap gap-1.5 text-xs">
                {checks.map((c) => (
                  <li key={c.label} className={`rounded-full border px-2 py-0.5 ${c.ok ? "border-success text-success" : "border-border text-foreground-muted"}`}>
                    {c.ok ? "✓" : "○"} {c.label}
                  </li>
                ))}
              </ul>
              {!judged && <p className="mt-1 text-[11px] text-foreground-muted">{goal.incident ? "장애 상황" : "평상시"}에서 판정합니다 — 위의 장애 상황 스위치를 바꿔 보세요.</p>}
              {goal.hint && !done && (
                <button
                  type="button"
                  className="mt-2 text-xs text-accent hover:underline"
                  onClick={() => setHints((h) => new Set(h).add(goal.id))}
                  hidden={hints.has(goal.id)}
                >
                  힌트 보기
                </button>
              )}
              {hints.has(goal.id) && !done && <p className="mt-1 text-xs text-foreground-muted">힌트: {goal.hint}</p>}
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

// ---- 시간 재생 ----

export function ReplayPanel({ lab, values, defaults }: { lab: EngineLab; values: KnobValues; defaults: KnobValues }) {
  const [at, setAt] = useState(120);
  const [compareLate, setCompareLate] = useState(true);
  const [late, setLate] = useState(240);
  const [block, setBlock] = useState<TimelineContentBlock | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const unchanged = JSON.stringify(values) === JSON.stringify(defaults);

  async function play() {
    setBusy(true);
    setError(null);
    try {
      const applyAt = compareLate && late !== at ? [at, late] : [at];
      const duration = Math.max(300, Math.max(...applyAt) + 150);
      const result = await replayEngineLab(lab.slug, values, applyAt, duration);
      setBlock({ ...result, title: "", caption: "" });
    } catch {
      setError("재생하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <Card as="section">
        <h2 className="mb-1 text-sm font-semibold text-foreground-muted">장애가 시작된 뒤, 언제 바꾸나</h2>
        <p className="mb-3 text-xs text-foreground-muted">
          위 손잡이 값이 그 초에 한꺼번에 적용됩니다. 장애는 90초에 걸쳐 최대치에 이릅니다. 늦게 적용한 경우와 나란히 보면, 기다린 대가(쌓인 적체·길어진 장애)가 보입니다.
        </p>
        {unchanged && <p className="mb-3 text-xs text-warning">아직 손잡이가 기본값 그대로입니다 — 먼저 값을 바꿔 보세요.</p>}
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1 text-sm">
            <span className="flex justify-between">
              <span>적용 시점</span>
              <span className="font-mono">{formatDuration(at)}</span>
            </span>
            <input type="range" min={0} max={300} step={10} value={at} onChange={(e) => setAt(Number(e.target.value))} />
          </label>
          <div className="flex flex-col gap-1 text-sm">
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={compareLate} onChange={(e) => setCompareLate(e.target.checked)} />
              늦게 적용한 경우도 비교
            </label>
            {compareLate && (
              <label className="flex flex-col gap-1">
                <span className="flex justify-between text-xs text-foreground-muted">
                  <span>늦은 적용 시점</span>
                  <span className="font-mono">{formatDuration(late)}</span>
                </span>
                <input type="range" min={0} max={300} step={10} value={late} onChange={(e) => setLate(Number(e.target.value))} />
              </label>
            )}
          </div>
        </div>
        <Button className="mt-4" onClick={play} disabled={busy}>
          {busy ? "계산하는 중…" : "재생하기"}
        </Button>
        {error && <p className="mt-2 text-xs text-danger">{error}</p>}
      </Card>
      {block && <TimelineView block={block} toneLabels={{ good: "먼저 적용", bad: "늦게 적용" }} />}
    </div>
  );
}

// ---- 스윕 ----

const LOAD_METRICS = new Set<keyof SystemState>(["dbReadLoad", "dbWriteLoad", "connectionPoolUsage", "cpuUtilization"]);
/** Metrics where more is better — the sweep's caption names their highest point instead of the lowest. */
const HIGHER_IS_BETTER = new Set<keyof SystemState>(["consumerThroughput", "cacheHitRatio", "availability"]);

export function SweepPanel({ lab, values, incident }: { lab: EngineLab; values: KnobValues; incident: boolean }) {
  const numeric = lab.knobs.filter((k) => k.type === "number");
  const [knob, setKnob] = useState(numeric[0]?.trait ?? "");
  const [points, setPoints] = useState<{ value: number; state: SystemState }[] | null>(null);
  const [logScale, setLogScale] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const others = JSON.stringify(Object.fromEntries(Object.entries(values).filter(([k]) => k !== knob)));

  useEffect(() => {
    if (!knob) return;
    let cancelled = false;
    sweepEngineLab(lab.slug, JSON.parse(others), incident, knob)
      .then((r) => {
        if (cancelled) return;
        setPoints(r.points);
        setLogScale(r.logScale);
        setError(null);
      })
      .catch(() => !cancelled && setError("스윕하지 못했습니다."));
    return () => {
      cancelled = true;
    };
  }, [lab.slug, knob, others, incident]);

  if (numeric.length === 0) return <p className="text-sm text-foreground-muted">이 랩에는 범위로 돌려 볼 숫자 손잡이가 없습니다.</p>;
  const knobMeta = numeric.find((k) => k.trait === knob)!;
  const current = Number(values[knob]);

  return (
    <Card as="section" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground-muted">손잡이 하나를 끝에서 끝까지 — 어디서 꺾이나</h2>
        <label className="flex items-center gap-2 text-sm">
          돌릴 손잡이
          <select className="rounded border border-border bg-surface px-2 py-1 text-sm" value={knob} onChange={(e) => setKnob(e.target.value)}>
            {numeric.map((k) => (
              <option key={k.trait} value={k.trait}>
                {k.label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <p className="text-xs text-foreground-muted">
        다른 손잡이는 위에서 정한 값 그대로입니다. 세로 선이 지금 값입니다.{logScale && " 범위가 넓어 가로축은 로그 눈금입니다."} 사용률 차트의 점선은 지연이 커지기 시작하는 60%와 에러가 급증하는 95%입니다.
      </p>
      {error && <p className="text-xs text-danger">{error}</p>}
      {points && (
        <div className="grid gap-3 md:grid-cols-2">
          {lab.watch.map((m) => {
            const series = points.map((p) => ({ x: p.value, y: Number(p.state[m]) }));
            const higher = HIGHER_IS_BETTER.has(m);
            const best = series.reduce((a, b) => ((higher ? b.y > a.y : b.y < a.y) ? b : a));
            return (
              <figure key={m} className="flex flex-col gap-1">
                <figcaption className="flex justify-between text-xs text-foreground-muted">
                  <span>{STATE_METRICS[m]?.label ?? m}</span>
                  <span>
                    {higher ? "최고" : "최저"} {fmtMetric(m, best.y)} @ {best.x.toLocaleString()}
                  </span>
                </figcaption>
                <div className="h-36" aria-hidden>
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={series} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                      <CartesianGrid stroke="var(--border)" strokeDasharray="2 4" vertical={false} />
                      <XAxis
                        dataKey="x"
                        type="number"
                        scale={logScale ? "log" : "auto"}
                        domain={["dataMin", "dataMax"]}
                        tick={{ fontSize: 10, fill: "var(--foreground-muted)" }}
                        tickFormatter={(v) => Number(v).toLocaleString()}
                      />
                      <YAxis width={44} tick={{ fontSize: 10, fill: "var(--foreground-muted)" }} tickFormatter={(v) => fmtMetric(m, Number(v))} />
                      {LOAD_METRICS.has(m) && <ReferenceLine y={0.6} stroke="var(--warning)" strokeDasharray="4 3" />}
                      {LOAD_METRICS.has(m) && <ReferenceLine y={0.95} stroke="var(--danger)" strokeDasharray="4 3" />}
                      <ReferenceLine x={current} stroke="var(--accent)" />
                      <Tooltip
                        contentStyle={{ background: "var(--surface)", border: "1px solid var(--border)", fontSize: 11 }}
                        labelFormatter={(x) => `${knobMeta.label} ${x}`}
                        formatter={(v) => [fmtMetric(m, Number(v)), STATE_METRICS[m]?.label ?? m]}
                      />
                      <Line type="linear" dataKey="y" stroke="var(--accent)" strokeWidth={2} dot={false} isAnimationActive={false} />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </figure>
            );
          })}
        </div>
      )}
      {points && (
        <details className="text-xs">
          <summary className="cursor-pointer text-foreground-muted">표로 보기</summary>
          <div className="mt-2 overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="text-left text-foreground-muted">
                  <th className="py-1 font-normal">{knobMeta.label}</th>
                  {lab.watch.map((m) => (
                    <th key={m} className="py-1 text-right font-normal">{STATE_METRICS[m]?.label ?? m}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {points.map((p) => (
                  <tr key={p.value} className={`border-t border-border ${p.value === current ? "text-accent" : ""}`}>
                    <td className="py-1 font-mono">{p.value}</td>
                    {lab.watch.map((m) => (
                      <td key={m} className="py-1 text-right font-mono">{fmtMetric(m, Number(p.state[m]))}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>
      )}
    </Card>
  );
}
