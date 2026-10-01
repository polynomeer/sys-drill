"use client";

import { useEffect } from "react";
import { type Defense, getDefense } from "@/lib/api";
import { Card } from "@/components/ui/Card";

/**
 * docs/DRILLS_EXPANSION_PLAN.md M4 (PLAN.md Round E10) — defend the design. The INITIAL
 * feedback's follow-up questions used to be read and forgotten; now two of them are
 * answered with the FOLLOWUP design and evaluated with it. Optional, except in
 * interview-timer mode (enforced on submit by the page).
 */
export function DefensePanel({
  sessionId,
  defense,
  onLoad,
  answers,
  onChange,
}: {
  sessionId: string;
  defense: Defense | null;
  onLoad: (defense: Defense | null) => void;
  answers: Record<string, string>;
  onChange: (next: Record<string, string>) => void;
}) {
  useEffect(() => {
    getDefense(sessionId).then(onLoad).catch(() => onLoad(null));
  }, [sessionId, onLoad]);

  if (!defense?.available) return null;

  return (
    <Card as="section" className="text-sm">
      <h2 className="mb-1 text-xs font-semibold uppercase tracking-wide text-foreground-muted">
        설계 방어 {defense.required ? <span className="text-danger">· 필수</span> : <span>· 선택</span>}
      </h2>
      <p className="mb-3 text-xs text-foreground-muted">
        지난 피드백의 꼬리질문입니다. 리뷰어에게 답하듯 짧게 방어해 보세요 — 꼬리설계 답안과 함께 평가됩니다.
      </p>
      <div className="flex flex-col gap-3">
        {defense.questions.map((q) => (
          <label key={q} className="flex flex-col gap-1">
            <span className="text-xs">Q. {q}</span>
            <textarea
              rows={3}
              maxLength={2000}
              value={answers[q] ?? ""}
              onChange={(e) => onChange({ ...answers, [q]: e.target.value })}
              className="w-full rounded border border-border bg-transparent p-2 text-sm"
            />
          </label>
        ))}
      </div>
    </Card>
  );
}
