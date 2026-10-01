"use client";

import type { EstimateResult } from "@/lib/api";

/**
 * docs/DRILLS_EXPANSION_PLAN.md M2 · docs/LEARNING_EXPANSION_PLAN.md L6 (PLAN.md Round E9) —
 * the same input and the same verdict in the Drill's estimation step and the Learning
 * Capacity Lab: practise in one, use it in the other. Judged on order of magnitude
 * (within 2× = on target), never on the exact number.
 */
export function EstimateInput({
  label,
  unit,
  hint,
  value,
  onChange,
  disabled,
}: {
  label: string;
  unit: string;
  hint?: string | null;
  value: number | null;
  onChange: (value: number | null) => void;
  disabled?: boolean;
}) {
  return (
    <label className="flex flex-col gap-1 text-xs">
      <span className="text-foreground-muted">{label}</span>
      <span className="flex items-center gap-2">
        <input
          type="number"
          min={0}
          inputMode="decimal"
          value={value ?? ""}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value === "" ? null : Number(e.target.value))}
          className="w-full min-w-0 rounded border border-border bg-transparent px-2 py-1 font-mono text-sm"
        />
        <span className="shrink-0 text-foreground-muted">{unit}</span>
      </span>
      {hint && <span className="text-[11px] text-foreground-muted">{hint}</span>}
    </label>
  );
}

function compact(n: number): string {
  if (n >= 1e9) return `${(n / 1e9).toFixed(1)}B`;
  if (n >= 1e6) return `${(n / 1e6).toFixed(1)}M`;
  if (n >= 1e4) return `${(n / 1e3).toFixed(1)}K`;
  if (n >= 100) return n.toFixed(0);
  return n.toFixed(n >= 10 ? 1 : 2);
}

const VERDICT: Record<EstimateResult["direction"], { text: string; className: string }> = {
  ON_TARGET: { text: "적중", className: "text-success" },
  UNDER: { text: "과소", className: "text-warning" },
  OVER: { text: "과대", className: "text-warning" },
  MISSING: { text: "미입력", className: "text-foreground-muted" },
};

export function EstimateResultRow({ label, unit, result, formula }: { label: string; unit: string; result: EstimateResult; formula?: string }) {
  const verdict = VERDICT[result.direction];
  const ratio =
    result.ratio === null ? null : result.ratio < 1 ? `실제의 ${Math.round(result.ratio * 100)}%` : `실제의 ${result.ratio.toFixed(1)}배`;
  return (
    <li className="flex flex-col gap-0.5 border-b border-border py-2 text-sm last:border-b-0">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <span>{label}</span>
        <span className={`text-xs font-semibold ${verdict.className}`}>{verdict.text}</span>
      </div>
      <div className="flex flex-wrap gap-x-4 font-mono text-xs text-foreground-muted">
        <span>
          내 추정 {result.estimate === null ? "—" : compact(result.estimate)} {unit}
        </span>
        <span>
          실제 {compact(result.truth)} {unit}
        </span>
        {ratio && <span>{ratio}</span>}
      </div>
      {formula && <span className="font-mono text-[11px] text-foreground-muted">= {formula}</span>}
    </li>
  );
}
