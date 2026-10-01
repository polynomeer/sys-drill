"use client";

import { useEffect, useState } from "react";
import { type CapacityCheckResult, type CapacityLab, checkCapacityLab, getCapacityLab } from "@/lib/api";
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
