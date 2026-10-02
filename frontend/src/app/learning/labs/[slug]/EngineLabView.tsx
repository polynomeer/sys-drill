"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { type EngineLab, type SystemState, getEngineLab, runEngineLab } from "@/lib/api";
import { formatMs, formatPercent } from "@/lib/metrics";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

/**
 * docs/LEARNING_EXPANSION_PLAN.md L5 (PLAN.md Round E11, ADR-0047) — Predict → Experiment → Break.
 * Change a knob, predict which way each metric moves, run the domain's own formula, and
 * compare. "장애 상황" off shows the same settings without the incident. The truth is the
 * engine's output — there is no stored answer key, and nothing here is recorded.
 */

type Direction = "UP" | "SAME" | "DOWN";

const METRIC_META: Partial<Record<keyof SystemState, { label: string; format: (v: number) => string }>> = {
  trafficRps: { label: "트래픽", format: (v) => `${v.toFixed(0)}/s` },
  p95LatencyMs: { label: "P95 지연", format: formatMs },
  errorRate: { label: "에러율", format: formatPercent },
  availability: { label: "가용성", format: formatPercent },
  dbReadLoad: { label: "DB 읽기 사용률", format: formatPercent },
  dbWriteLoad: { label: "DB 쓰기 사용률", format: formatPercent },
  connectionPoolUsage: { label: "커넥션 풀 사용률", format: formatPercent },
  cacheHitRatio: { label: "캐시 hit ratio", format: formatPercent },
  queueLag: { label: "적체 / 대기", format: (v) => v.toLocaleString() },
  consumerThroughput: { label: "처리 용량", format: (v) => `${v.toFixed(1)}/s` },
};

/** Within 2% of the previous value counts as "about the same". */
function directionOf(before: number, after: number): Direction {
  const scale = Math.max(Math.abs(before), 1e-9);
  const change = (after - before) / scale;
  if (Math.abs(change) < 0.02) return "SAME";
  return change > 0 ? "UP" : "DOWN";
}

const ARROW: Record<Direction, string> = { UP: "↑", SAME: "≈", DOWN: "↓" };

export function EngineLabView({ slug }: { slug: string }) {
  const [lab, setLab] = useState<EngineLab | null>(null);
  const [values, setValues] = useState<Record<string, number | boolean>>({});
  const [incident, setIncident] = useState(true);
  const [baseline, setBaseline] = useState<SystemState | null>(null);
  const [baselineValues, setBaselineValues] = useState<Record<string, number | boolean>>({});
  const [result, setResult] = useState<SystemState | null>(null);
  const [predictions, setPredictions] = useState<Record<string, Direction>>({});
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getEngineLab(slug)
      .then(async (loaded) => {
        const defaults = Object.fromEntries(loaded.knobs.map((k) => [k.trait, k.default]));
        setLab(loaded);
        setValues(defaults);
        setBaselineValues(defaults);
        setBaseline(await runEngineLab(slug, defaults, true));
      })
      .catch(() => setError("랩을 불러오지 못했습니다."));
  }, [slug]);

  if (error && !lab) return <p className="text-sm text-danger">{error}</p>;
  if (!lab || !baseline) return <LoadingState />;

  const changed = JSON.stringify(values) !== JSON.stringify(baselineValues);
  const predicted = lab.predict.every((m) => predictions[m]);

  async function run() {
    setRunning(true);
    setError(null);
    try {
      setResult(await runEngineLab(slug, values, incident));
    } catch {
      setError("실행하지 못했습니다.");
    } finally {
      setRunning(false);
    }
  }

  function keepGoing() {
    if (!result) return;
    setBaseline(result);
    setBaselineValues(values);
    setResult(null);
    setPredictions({});
  }

  async function toggleIncident(next: boolean) {
    setIncident(next);
    setResult(null);
    setPredictions({});
    setBaseline(await runEngineLab(slug, baselineValues, next));
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground-muted">시뮬레이션 랩</p>
        <h1 className="mt-1 text-2xl font-semibold">{lab.title}</h1>
        <p className="mt-1 text-sm text-foreground-muted">{lab.summary}</p>
        {lab.riskKey && (
          <Link href={`/learning/${lab.riskKey}`} className="mt-1 inline-block text-xs text-accent hover:underline">
            관련 개념 보기 →
          </Link>
        )}
      </div>

      <Card as="section">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-semibold text-foreground-muted">1. 값을 바꿔 보세요</h2>
          <label className="flex items-center gap-2 text-xs">
            <input type="checkbox" checked={incident} onChange={(e) => toggleIncident(e.target.checked)} />
            장애 상황 (Break)
          </label>
        </div>
        <div className="flex flex-col gap-4">
          {lab.knobs.map((knob) =>
            knob.type === "boolean" ? (
              <label key={knob.trait} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={Boolean(values[knob.trait])}
                  onChange={(e) => setValues((v) => ({ ...v, [knob.trait]: e.target.checked }))}
                />
                {knob.label}
              </label>
            ) : (
              <label key={knob.trait} className="flex flex-col gap-1 text-sm">
                <span className="flex justify-between">
                  <span>{knob.label}</span>
                  <span className="font-mono">{String(values[knob.trait])}</span>
                </span>
                <input
                  type="range"
                  min={knob.min ?? 0}
                  max={knob.max ?? 100}
                  step={knob.step ?? 1}
                  value={Number(values[knob.trait])}
                  onChange={(e) => setValues((v) => ({ ...v, [knob.trait]: Number(e.target.value) }))}
                />
              </label>
            ),
          )}
        </div>
      </Card>

      {changed && !result && (
        <Card as="section" className="border-accent/40">
          <h2 className="mb-1 text-sm font-semibold text-foreground-muted">2. 실행하기 전에 예측하세요</h2>
          <p className="mb-3 text-xs text-foreground-muted">지금 값과 비교해 각 지표는 어느 쪽으로 움직일까요?</p>
          <div className="flex flex-col gap-2">
            {lab.predict.map((metric) => (
              <div key={metric} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <span>{METRIC_META[metric]?.label ?? metric}</span>
                <span className="flex gap-1" role="group" aria-label={`${METRIC_META[metric]?.label ?? metric} 예측`}>
                  {(["UP", "SAME", "DOWN"] as const).map((d) => (
                    <button
                      key={d}
                      type="button"
                      onClick={() => setPredictions((p) => ({ ...p, [metric]: d }))}
                      aria-pressed={predictions[metric] === d}
                      className={`rounded border px-2.5 py-0.5 ${predictions[metric] === d ? "border-accent text-accent" : "border-border text-foreground-muted"}`}
                    >
                      {ARROW[d]}
                    </button>
                  ))}
                </span>
              </div>
            ))}
          </div>
          <Button className="mt-4" onClick={run} disabled={!predicted || running}>
            {running ? "실행 중..." : "3. 실행하기"}
          </Button>
        </Card>
      )}

      <Card as="section">
        <h2 className="mb-2 text-sm font-semibold text-foreground-muted">
          {result ? "결과 — 바꾸기 전과 비교" : incident ? "지금 상태 (장애 중)" : "지금 상태 (평상시)"}
        </h2>
        <table className="w-full text-sm">
          <thead>
            <tr className="text-xs text-foreground-muted">
              <th className="text-left font-normal">지표</th>
              <th className="text-right font-normal">{result ? "전" : "값"}</th>
              {result && <th className="text-right font-normal">후</th>}
              {result && <th className="text-right font-normal">내 예측</th>}
            </tr>
          </thead>
          <tbody>
            {lab.watch.map((metric) => {
              const meta = METRIC_META[metric];
              const before = Number(baseline[metric]);
              const after = result ? Number(result[metric]) : null;
              const actual = after === null ? null : directionOf(before, after);
              const guess = predictions[metric];
              return (
                <tr key={metric} className="border-t border-border">
                  <td className="py-1.5">{meta?.label ?? metric}</td>
                  <td className="text-right font-mono">{meta ? meta.format(before) : before}</td>
                  {after !== null && (
                    <td className="text-right font-mono">
                      {meta ? meta.format(after) : after} <span className="text-foreground-muted">{actual && ARROW[actual]}</span>
                    </td>
                  )}
                  {after !== null && (
                    <td className="text-right">
                      {guess ? (
                        <span className={guess === actual ? "text-success" : "text-danger"}>
                          {ARROW[guess]} {guess === actual ? "✓" : "✗"}
                        </span>
                      ) : (
                        <span className="text-foreground-muted">—</span>
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
        {result && (
          <Button size="sm" variant="secondary" className="mt-3" onClick={keepGoing}>
            이 값에서 계속 실험하기
          </Button>
        )}
        {error && <p className="mt-2 text-xs text-danger">{error}</p>}
      </Card>
      <p className="text-[11px] text-foreground-muted">
        Drill 인시던트와 같은 수식으로 계산합니다. 2% 안의 변화는 ≈로 봅니다. 랩 기록은 저장되지 않습니다.
      </p>
    </div>
  );
}
