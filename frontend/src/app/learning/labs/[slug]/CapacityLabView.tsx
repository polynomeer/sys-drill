"use client";

import { useEffect, useState } from "react";
import { type CapacityCheckResult, type CapacityLab, type EstimateResult, checkCapacityLab, getCapacityLab } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";
import { EstimateInput, EstimateResultRow } from "@/components/Estimates";

/**
 * docs/LEARNING_EXPANSION_PLAN.md L6 (PLAN.md Round E9) — back-of-the-envelope practice.
 * Fill in the asks from the given inputs, check, then try the variant ("DAU ×10") and
 * redo it. Same judge as the Drill's estimation step; nothing is saved.
 */
export function CapacityLabView({ slug }: { slug: string }) {
  const [lab, setLab] = useState<CapacityLab | null>(null);
  const [variant, setVariant] = useState(0);
  const [answers, setAnswers] = useState<Record<string, number | null>>({});
  const [result, setResult] = useState<CapacityCheckResult | null>(null);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getCapacityLab(slug).then(setLab).catch(() => setError("문제를 불러오지 못했습니다."));
  }, [slug]);

  if (error) return <p className="text-sm text-danger">{error}</p>;
  if (!lab) return <LoadingState />;
  const current = lab.variants[variant];

  function selectVariant(index: number) {
    setVariant(index);
    setAnswers({});
    setResult(null);
  }

  async function check() {
    setChecking(true);
    setError(null);
    try {
      setResult(await checkCapacityLab(slug, variant, answers));
    } catch {
      setError("확인하지 못했습니다.");
    } finally {
      setChecking(false);
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground-muted">Capacity Lab</p>
        <h1 className="mt-1 text-2xl font-semibold">{lab.title}</h1>
        <p className="mt-1 text-sm text-foreground-muted">{lab.summary}</p>
      </div>

      {lab.variants.length > 1 && (
        <div className="flex flex-wrap gap-2" role="group" aria-label="조건">
          {lab.variants.map((v, i) => (
            <Button key={v.label} size="sm" variant={i === variant ? "primary" : "secondary"} onClick={() => selectVariant(i)}>
              {v.label}
            </Button>
          ))}
        </div>
      )}

      <Card as="section">
        <h2 className="mb-2 text-sm font-semibold text-foreground-muted">주어진 조건</h2>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm sm:grid-cols-3">
          {current.inputs.map((input) => (
            <div key={input.key}>
              <dt className="text-xs text-foreground-muted">{input.label}</dt>
              <dd className="font-mono">
                {input.value.toLocaleString()} {input.unit}
              </dd>
            </div>
          ))}
        </dl>
        <p className="mt-3 text-[11px] text-foreground-muted">하루 = 86,400초 · 1 TB = 1,000,000 MB (십진 단위)</p>
      </Card>

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">어림해 보세요</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          {lab.asks.map((ask) => (
            <EstimateInput
              key={`${variant}-${ask.key}`}
              label={ask.label}
              unit={ask.unit}
              value={answers[ask.key] ?? null}
              onChange={(v) => setAnswers((prev) => ({ ...prev, [ask.key]: v }))}
            />
          ))}
        </div>
        <Button className="mt-4" onClick={check} disabled={checking}>
          {checking ? "확인하는 중..." : "확인하기"}
        </Button>
      </Card>

      {result && (
        <Card as="section">
          <div className="mb-1 flex items-baseline justify-between">
            <h2 className="text-sm font-semibold text-foreground-muted">결과</h2>
            <span className="font-mono text-sm">
              적중 {result.results.filter((r) => r.result.onTarget).length} / {result.results.length}
            </span>
          </div>
          <p className="mb-1 text-xs text-foreground-muted">실제 값의 0.5~2배 안이면 적중입니다.</p>
          <ul>
            {result.results.map((r) => (
              <EstimateResultRow key={r.key} label={r.label} unit={r.unit} result={r.result} formula={r.formula} />
            ))}
          </ul>
          <MagnitudeBars results={result.results} />
          {variant + 1 < lab.variants.length && (
            <Button size="sm" variant="secondary" className="mt-3" onClick={() => selectVariant(variant + 1)}>
              다음 조건: {lab.variants[variant + 1].label} →
            </Button>
          )}
        </Card>
      )}
    </div>
  );
}

/**
 * docs/LEARNING_DEEPENING_PLAN.md L15 — "my answer vs the computed value" on a log scale, one row per
 * ask: the shaded band is the 0.5–2× window that counts as on target, so being off by 10× reads as a
 * whole decade, not a slightly longer bar.
 */
function MagnitudeBars({ results }: { results: CapacityCheckResult["results"] }) {
  const rows = results.filter((r) => r.result.truth > 0);
  if (rows.length === 0) return null;
  return (
    <section className="mt-4 flex flex-col gap-3" aria-label="자릿수 비교">
      <h3 className="text-xs font-semibold text-foreground-muted">자릿수로 보기 (로그 눈금)</h3>
      {rows.map((r) => (
        <MagnitudeRow key={r.key} label={r.label} unit={r.unit} result={r.result} />
      ))}
      <p className="flex flex-wrap gap-x-3 text-[11px] text-foreground-muted">
        <span><span className="inline-block h-2 w-3 rounded-sm bg-success/30 align-middle" /> 적중 구간(0.5~2배)</span>
        <span><span className="inline-block h-3 w-0.5 bg-foreground align-middle" /> 실제 값</span>
        <span><span className="inline-block h-2.5 w-2.5 rounded-full bg-accent align-middle" /> 내 추정</span>
      </p>
    </section>
  );
}

function MagnitudeRow({ label, unit, result }: { label: string; unit: string; result: EstimateResult }) {
  const truth = result.truth;
  const estimate = result.estimate !== null && result.estimate > 0 ? result.estimate : null;
  const lo = Math.floor(Math.log10(Math.min(truth, estimate ?? truth) / 10));
  const hi = Math.ceil(Math.log10(Math.max(truth, estimate ?? truth) * 10));
  const pos = (v: number) => `${((Math.log10(v) - lo) / (hi - lo)) * 100}%`;
  const decades = Array.from({ length: hi - lo + 1 }, (_, i) => lo + i);
  return (
    <div className="flex flex-col gap-1">
      <div className="flex justify-between text-xs">
        <span>{label}</span>
        <span className="text-foreground-muted">{unit}</span>
      </div>
      <div className="relative h-6 rounded bg-background" role="img" aria-label={`${label}: 실제 ${truth.toLocaleString()}, 내 추정 ${estimate === null ? "없음" : estimate.toLocaleString()}`}>
        <div className="absolute inset-y-0 bg-success/30" style={{ left: pos(truth * 0.5), width: `calc(${pos(truth * 2)} - ${pos(truth * 0.5)})` }} />
        {decades.map((d) => (
          <div key={d} className="absolute inset-y-0 w-px bg-border" style={{ left: pos(10 ** d) }} />
        ))}
        <div className="absolute inset-y-0 w-0.5 bg-foreground" style={{ left: pos(truth) }} />
        {estimate !== null && <div className="absolute top-1/2 h-3 w-3 -translate-x-1/2 -translate-y-1/2 rounded-full bg-accent" style={{ left: pos(estimate) }} />}
      </div>
      <div className="relative h-3 text-[10px] text-foreground-muted" aria-hidden>
        {decades.map((d) => (
          <span key={d} className="absolute -translate-x-1/2" style={{ left: pos(10 ** d) }}>
            {decadeLabel(d)}
          </span>
        ))}
      </div>
    </div>
  );
}

/** 10^d as a short label: 0.01, 1, 100, 10K, 1M, 100B. */
function decadeLabel(d: number): string {
  if (d < 0) return (10 ** d).toFixed(-d);
  const units = ["", "K", "M", "B", "T"];
  const group = Math.min(Math.floor(d / 3), units.length - 1);
  return `${10 ** (d - group * 3)}${units[group]}`;
}
