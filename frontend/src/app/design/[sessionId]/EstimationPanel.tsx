"use client";

import { useEffect, useState } from "react";
import { type Estimation, getEstimation } from "@/lib/api";
import { Card } from "@/components/ui/Card";
import { EstimateInput } from "@/components/Estimates";

/**
 * docs/DRILLS_EXPANSION_PLAN.md M2 (PLAN.md Round E9) — estimate the scale before
 * designing. The numbers go out with the INITIAL submission (they're fixed at submit
 * time) and are judged afterwards; nothing is graded while typing (Hidden Score).
 * Renders nothing for a scenario without estimation fields.
 */
export function EstimationPanel({
  sessionId,
  values,
  onChange,
}: {
  sessionId: string;
  values: Record<string, number | null>;
  onChange: (next: Record<string, number | null>) => void;
}) {
  const [data, setData] = useState<Estimation | null>(null);

  useEffect(() => {
    getEstimation(sessionId).then(setData).catch(() => setData(null));
  }, [sessionId]);

  if (!data?.available || !data.open) return null;

  return (
    <Card as="section" className="text-sm">
      <h2 className="mb-1 text-xs font-semibold uppercase tracking-wide text-foreground-muted">규모 추정</h2>
      <p className="mb-3 text-xs text-foreground-muted">
        설계하기 전에 규모를 어림해 보세요. 정확한 숫자가 아니라 자릿수가 맞는지를 봅니다 — 답안과 함께 제출됩니다.
      </p>
      <div className="grid gap-3">
        {data.fields.map((field) => (
          <EstimateInput
            key={field.key}
            label={field.label}
            unit={field.unit}
            hint={field.hint}
            value={values[field.key] ?? null}
            onChange={(v) => onChange({ ...values, [field.key]: v })}
          />
        ))}
      </div>
    </Card>
  );
}
