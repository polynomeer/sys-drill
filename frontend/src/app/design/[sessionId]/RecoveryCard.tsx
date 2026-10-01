"use client";

import { useEffect, useState } from "react";
import { type RecoveryReport, getRecovery, resolveIncident } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

/**
 * docs/DRILLS_EXPANSION_PLAN.md M5 (PLAN.md Round E13) — "지표가 돌아온 것"과 "복구된 것"은 다르다.
 * The learner declares recovery explicitly, after checking: symptoms back to normal, backlog
 * drained, data integrity. Declaring with work left is allowed — the postmortem then says
 * Partial Recovery. The checklist values come from the server; nothing is scored here.
 */
const STATUS_TEXT: Record<RecoveryReport["status"], { text: string; className: string }> = {
  RECOVERED: { text: "복구 완료", className: "text-success" },
  PARTIAL: { text: "부분 복구 (Partial Recovery)", className: "text-warning" },
  NOT_RECOVERED: { text: "복구되지 않음", className: "text-danger" },
  NOT_STARTED: { text: "인시던트 전", className: "text-foreground-muted" },
};

export function RecoveryCard({ sessionId, onResolved }: { sessionId: string; onResolved?: () => void }) {
  const [report, setReport] = useState<RecoveryReport | null>(null);
  const [checking, setChecking] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getRecovery(sessionId).then(setReport).catch(() => setReport(null));
  }, [sessionId]);

  if (!report?.started) return null;

  async function openChecklist() {
    setError(null);
    try {
      setReport(await getRecovery(sessionId));
      setChecking(true);
    } catch {
      setError("상태를 확인하지 못했습니다.");
    }
  }

  async function declare() {
    setSubmitting(true);
    setError(null);
    try {
      setReport(await resolveIncident(sessionId));
      setChecking(false);
      onResolved?.();
    } catch {
      setError("복구를 선언하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  const items: { label: string; ok: boolean; detail: string }[] = [
    { label: "에러율·지연 정상", ok: report.symptomsOk, detail: report.symptomsOk ? "증상 지표가 정상 범위로 돌아왔습니다" : "아직 에러·지연이 정상 범위가 아닙니다" },
    {
      label: "적체 해소",
      ok: report.backlog === 0,
      detail: report.backlog === 0 ? "남은 적체 없음" : `아직 ${report.backlog.toLocaleString()}건이 남아 있습니다`,
    },
    ...report.integrity.map((i) => ({ label: `정합성 — ${i.label}`, ok: i.ok, detail: i.detail })),
  ];

  return (
    <Card as="section" className={report.resolved ? "" : "border-accent/40"}>
      <h2 className="mb-1 text-sm font-semibold text-foreground-muted">복구 선언</h2>
      {report.resolved ? (
        <>
          <p className={`text-sm font-semibold ${STATUS_TEXT[report.status].className}`}>{STATUS_TEXT[report.status].text}</p>
          <ul className="mt-2 flex flex-col gap-1 text-xs">
            {items.map((i) => (
              <li key={i.label}>
                <span className={i.ok ? "text-success" : "text-warning"}>{i.ok ? "✓" : "⚠"}</span> {i.label} — {i.detail}
              </li>
            ))}
          </ul>
        </>
      ) : checking ? (
        <>
          <p className="mb-2 text-xs text-foreground-muted">선언하기 전에 확인하세요. 남은 일이 있어도 선언할 수는 있지만, 포스트모템에 부분 복구로 남습니다.</p>
          <ul className="flex flex-col gap-1.5 text-sm">
            {items.map((i) => (
              <li key={i.label}>
                <span className={i.ok ? "text-success" : "text-warning"}>{i.ok ? "✓" : "□"}</span> {i.label}
                <span className="block pl-5 text-xs text-foreground-muted">{i.detail}</span>
              </li>
            ))}
          </ul>
          <div className="mt-3 flex gap-2">
            <Button size="sm" onClick={declare} disabled={submitting}>
              {submitting ? "선언하는 중..." : "복구 선언"}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setChecking(false)}>
              계속 대응하기
            </Button>
          </div>
        </>
      ) : (
        <>
          <p className="mb-2 text-xs text-foreground-muted">장애가 끝났다고 판단되면 복구를 선언하세요. 지표가 돌아온 것과 시스템이 복구된 것은 다릅니다.</p>
          <Button size="sm" variant="secondary" onClick={openChecklist}>
            복구 점검하기
          </Button>
        </>
      )}
      {error && <p className="mt-2 text-xs text-danger">{error}</p>}
    </Card>
  );
}
