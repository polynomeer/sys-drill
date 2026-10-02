"use client";

import { useEffect, useState } from "react";
import { type CostEstimate, getCostEstimate } from "@/lib/api";

const REFRESH_DELAY_MS = 1500;

/**
 * docs/DRILLS_EXPANSION_PLAN.md M8 (PLAN.md Round E20) — the canvas's estimated monthly cost
 * against the budget, and ops complexity against the team. Refetched a moment after the canvas
 * changes (it saves itself, debounced). Not a score — the same facts go to the evaluation.
 */
export function CostBar({ sessionId, version }: { sessionId: string; version: number }) {
  const [estimate, setEstimate] = useState<CostEstimate | null>(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => getCostEstimate(sessionId).then(setEstimate).catch(() => undefined), version === 0 ? 0 : REFRESH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [sessionId, version]);

  if (!estimate?.available) return null;
  const over = (estimate.budgetDeltaPct ?? 0) > 0;
  const heavy = estimate.teamCapacity !== null && estimate.complexity > estimate.teamCapacity;

  return (
    <div className="rounded-lg border border-border px-3 py-2 text-xs">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
        {estimate.drawn ? (
          <>
            <span>
              월 비용 <span className="font-mono">${Math.round(estimate.monthlyCost).toLocaleString()}</span>
              {estimate.budgetPerMonth !== null && (
                <span className={over ? "text-warning" : "text-foreground-muted"}>
                  {" "}
                  / 예산 ${estimate.budgetPerMonth.toLocaleString()} ({over ? "+" : ""}
                  {estimate.budgetDeltaPct}%)
                </span>
              )}
            </span>
            <span className={heavy ? "text-warning" : ""}>
              운영 복잡도 <span className="font-mono">{estimate.complexity}</span>
              {estimate.teamCapacity !== null && <span className="text-foreground-muted"> / 팀 역량 {estimate.teamCapacity}</span>}
            </span>
          </>
        ) : (
          <span className="text-foreground-muted">캔버스를 그리면 월 비용과 운영 복잡도를 추정해 드립니다 (예산 ${estimate.budgetPerMonth?.toLocaleString() ?? "-"}, 팀 {estimate.teamSize ?? "-"}명).</span>
        )}
        <span className="text-foreground-muted">추정치</span>
        {estimate.drawn && (
          <button type="button" className="ml-auto text-foreground-muted underline" onClick={() => setOpen((v) => !v)}>
            {open ? "접기" : "내역"}
          </button>
        )}
      </div>
      {open && (
        <div className="mt-2 grid gap-1 border-t border-border pt-2 text-foreground-muted">
          {estimate.lines.map((l) => (
            <div key={l.key} className="flex justify-between gap-2">
              <span>
                {l.label} × {l.units}
              </span>
              <span className="font-mono">${Math.round(l.cost).toLocaleString()}</span>
            </div>
          ))}
          {Object.keys(estimate.opsExperience).length > 0 && (
            <p className="mt-1">팀 운영 경험: {Object.entries(estimate.opsExperience).map(([k, v]) => `${k} ${EXPERIENCE_LABELS[v] ?? v}`).join(" · ")}</p>
          )}
          <p className="mt-1">기술을 많이 쓸수록 좋은 설계가 아닙니다 — 예산과 팀 역량 안에서 고르세요.</p>
        </div>
      )}
    </div>
  );
}

const EXPERIENCE_LABELS: Record<string, string> = { LOW: "낮음", MEDIUM: "보통", HIGH: "높음" };
