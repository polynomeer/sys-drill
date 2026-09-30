"use client";

import Link from "next/link";
import type { LearningPath, LearningPathStep, LearningStepStatus } from "@/lib/api";
import { Card } from "@/components/ui/Card";
import { DOMAIN_TITLES } from "@/lib/designGuidance";

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §5.3 — 개인 학습 경로.
 *
 * "추천"이 아니라 **왜 추천하는지가 보이는 것**이 요점이라, 각 단계에 서버가
 * 계산한 근거(evidence)를 그대로 붙인다.
 */
const STATUS_META: Record<LearningStepStatus, { mark: string; label: string; className: string }> = {
  ADDRESSED: { mark: "✓", label: "해결됨", className: "text-success" },
  IN_PROGRESS: { mark: "▶", label: "진행 중", className: "text-warning" },
  NOT_STARTED: { mark: "○", label: "미시작", className: "text-foreground-muted" },
};

function Step({ step, index }: { step: LearningPathStep; index: number }) {
  const meta = STATUS_META[step.status];
  return (
    <li className="flex gap-3 py-3">
      <span className={`mt-0.5 shrink-0 font-mono text-sm ${meta.className}`} aria-hidden>
        {meta.mark}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline gap-x-2">
          <span className="text-xs text-foreground-muted">{index + 1}</span>
          <Link href={`/learning/${step.riskKey}`} className="font-medium underline-offset-2 hover:underline">
            {step.label}
          </Link>
          <span className={`text-xs ${meta.className}`}>{meta.label}</span>
          {step.weaknessCount > 0 && (
            <span className="text-xs text-foreground-muted">· 누적 {step.weaknessCount}회 지적</span>
          )}
        </div>
        <p className="mt-0.5 text-sm text-foreground-muted">{step.summary}</p>
        <p className="mt-1 text-xs text-foreground-muted">{step.evidence}</p>
        {step.relatedDomains.length > 0 && (
          <p className="mt-1 text-xs text-foreground-muted">
            관련 시나리오: {step.relatedDomains.map((d) => DOMAIN_TITLES[d] ?? d).join(" · ")}
          </p>
        )}
      </div>
    </li>
  );
}

export function LearningPathPanel({ path }: { path: LearningPath }) {
  return (
    <Card as="section">
      <div className="mb-1 flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold">내 학습 경로</h2>
        {path.categoryLabel && (
          <span className="text-xs text-foreground-muted">가장 약한 역량: {path.categoryLabel}</span>
        )}
      </div>
      <p className="text-xs text-foreground-muted">{path.rationale}</p>

      {path.steps.length > 0 && (
        <ul className="mt-2 divide-y divide-border">
          {path.steps.map((step, i) => (
            <Step key={step.riskKey} step={step} index={i} />
          ))}
        </ul>
      )}
    </Card>
  );
}
