"use client";

import { useEffect, useState } from "react";
import {
  type AlertEvent,
  type AlertRule,
  type OpsConfig,
  type SimulationSeries,
  type SloTargets,
  getOpsConfig,
  updateAlertRules,
  updateSlo,
} from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { relativeLabel } from "./ObserveViews";

/**
 * docs/OBSERVABILITY_UI_PLAN.md O5 · docs/DRILLS_EXPANSION_PLAN.md M3 (PLAN.md Round E12) —
 * Alert Center, the learner's alert rules, and their SLO with error budget. Alerts are
 * judged by the server over the incident's time series; a rule only fires from the moment
 * it was written, so write them before the incident (or accept a late detection).
 * Bad rules are shown as such ("7분 뒤 발화", "정상 구간에서 발화") but never scored.
 */

type RuleDraft = { id?: string; metric: string; op: ">" | "<"; threshold: number; forSeconds: number; severity: "WARN" | "CRITICAL" };

function toDraft(rule: AlertRule): RuleDraft {
  return { id: rule.id, metric: rule.metric, op: rule.op, threshold: rule.threshold, forSeconds: rule.forSeconds, severity: rule.severity };
}

function formatValue(value: number, unit: string): string {
  const digits = value >= 100 ? 0 : value >= 1 ? 1 : 2;
  return `${value.toFixed(digits)}${unit === "%" ? "%" : ` ${unit}`}`;
}

export function AlertsView({
  sessionId,
  series,
  isOwner,
  onInvestigate,
}: {
  sessionId: string;
  series: SimulationSeries | null;
  isOwner: boolean;
  /** PLAN.md Round E17 — open the logs around this alert (the start of an investigation). */
  onInvestigate: (alert: AlertEvent) => void;
}) {
  const [ops, setOps] = useState<OpsConfig | null>(null);
  const [draft, setDraft] = useState<RuleDraft>({ metric: "errorRatePct", op: ">", threshold: 5, forSeconds: 60, severity: "WARN" });
  const [slo, setSlo] = useState<SloTargets | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getOpsConfig(sessionId)
      .then((o) => {
        setOps(o);
        setSlo(o.slo ?? o.sloDefaults);
      })
      .catch(() => setError("알림 설정을 불러오지 못했습니다."));
  }, [sessionId]);

  const incidentStartedAt = series?.incidentStartedAt ?? null;
  const alerts = series?.alerts ?? [];
  const firing = alerts.filter((a) => !a.resolvedAt);
  const metricLabel = (key: string) => ops?.metrics.find((m) => m.key === key)?.label ?? key;
  const metricUnit = (key: string) => ops?.metrics.find((m) => m.key === key)?.unit ?? "";

  async function saveRules(next: RuleDraft[]) {
    setError(null);
    try {
      setOps(await updateAlertRules(sessionId, next));
    } catch {
      setError("규칙을 저장하지 못했습니다.");
    }
  }

  async function saveSlo() {
    if (!slo) return;
    setError(null);
    try {
      setOps(await updateSlo(sessionId, slo));
    } catch {
      setError("SLO를 저장하지 못했습니다(가용성 50~99.9999%, 지연·에러율은 양수).");
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {/* Alert Center */}
      <Card as="section" className="min-w-0">
        <div className="mb-2 flex items-baseline justify-between gap-2">
          <h2 className="text-sm font-semibold text-foreground-muted">Alert Center</h2>
          <span className="text-xs text-foreground-muted">발화 중 {firing.length} · 전체 {alerts.length}</span>
        </div>
        {alerts.length === 0 ? (
          <p className="text-sm text-foreground-muted">
            {ops && ops.alertRules.length === 0 ? "알림 규칙이 없습니다 — 아래에서 만들어 보세요." : "아직 발화한 알림이 없습니다."}
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {[...alerts].reverse().map((a: AlertEvent, i) => (
              <li key={`${a.ruleId}-${a.firedAt}-${i}`} className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-border px-3 py-2 text-sm">
                <span className={`font-mono text-xs font-semibold ${a.severity === "CRITICAL" ? "text-danger" : "text-warning"}`}>
                  {a.resolvedAt ? "○" : "●"} {a.severity}
                </span>
                <span className="min-w-0 flex-1">
                  {a.label} {a.op} {formatValue(a.threshold, a.unit)}
                  <span className="ml-2 text-xs text-foreground-muted">현재 {formatValue(a.value, a.unit)}</span>
                </span>
                {incidentStartedAt && (
                  <span className="font-mono text-xs text-foreground-muted">
                    {relativeLabel(a.firedAt, incidentStartedAt)}
                    {a.resolvedAt ? ` → ${relativeLabel(a.resolvedAt, incidentStartedAt)}` : " ~"}
                  </span>
                )}
                {a.falseAlarm && <span className="text-xs text-warning">정상 구간에서 발화(과민)</span>}
                <Button size="sm" variant="ghost" onClick={() => onInvestigate(a)}>
                  조사하기
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {/* SLO */}
      {series?.slo && slo && (
        <Card as="section" className="min-w-0">
          <h2 className="mb-2 text-sm font-semibold text-foreground-muted">SLO · 에러 버짓</h2>
          <div className="grid gap-3 sm:grid-cols-3">
            {(
              [
                ["availabilityPct", "가용성 ≥", "%", series.slo.availabilityMet],
                ["p95Ms", "P95 ≤", "ms", series.slo.p95Met],
                ["errorRatePct", "에러율 ≤", "%", series.slo.errorRateMet],
              ] as const
            ).map(([key, label, unit, met]) => (
              <label key={key} className="flex flex-col gap-1 text-xs">
                <span className="flex justify-between">
                  <span className="text-foreground-muted">{label}</span>
                  <span className={met ? "text-success" : "text-danger"}>{met ? "✓ 충족" : "✕ 위반"}</span>
                </span>
                <span className="flex items-center gap-1">
                  <input
                    type="number"
                    step="any"
                    value={slo[key]}
                    disabled={!isOwner}
                    onChange={(e) => setSlo({ ...slo, [key]: Number(e.target.value) })}
                    className="w-full min-w-0 rounded border border-border bg-transparent px-2 py-1 font-mono text-sm"
                  />
                  <span className="text-foreground-muted">{unit}</span>
                </span>
              </label>
            ))}
          </div>
          {isOwner && (
            <Button size="sm" variant="secondary" className="mt-3" onClick={saveSlo}>
              SLO 저장
            </Button>
          )}
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
            <div>
              <dt className="text-xs text-foreground-muted">인시던트 중 소진한 에러 버짓</dt>
              <dd className="font-mono">
                {(series.slo.budgetSpentSeconds / 60).toFixed(1)}분 / 월 {(series.slo.monthlyBudgetSeconds / 60).toFixed(1)}분
                <span className="ml-1 text-xs text-foreground-muted">
                  ({Math.round((series.slo.budgetSpentSeconds / series.slo.monthlyBudgetSeconds) * 100)}%)
                </span>
              </dd>
            </div>
            <div>
              <dt className="text-xs text-foreground-muted">Burn rate</dt>
              <dd className={`font-mono ${series.slo.burnRate > 1 ? "text-danger" : ""}`}>{series.slo.burnRate.toFixed(1)}×</dd>
            </div>
          </dl>
          <p className="mt-2 text-[11px] text-foreground-muted">
            에러 버짓 = 30일 × (1 − 가용성 목표). 소진량은 인시던트 동안의 에러율을 시간으로 적분한 값(완전 장애 환산)입니다.
          </p>
        </Card>
      )}

      {/* Rules */}
      {ops && (
        <Card as="section" className="min-w-0">
          <h2 className="mb-1 text-sm font-semibold text-foreground-muted">알림 규칙</h2>
          <p className="mb-3 text-xs text-foreground-muted">규칙은 만든 시점부터만 발화합니다. 장애가 오기 전에 준비해 두세요.</p>
          {ops.alertRules.length > 0 && (
            <ul className="mb-3 flex flex-col gap-1.5 text-sm">
              {ops.alertRules.map((rule) => (
                <li key={rule.id} className="flex flex-wrap items-center gap-2">
                  <span className={`font-mono text-xs ${rule.severity === "CRITICAL" ? "text-danger" : "text-warning"}`}>{rule.severity}</span>
                  <span>
                    {metricLabel(rule.metric)} {rule.op} {formatValue(rule.threshold, metricUnit(rule.metric))} · {rule.forSeconds}초 지속
                  </span>
                  {rule.createdAt && incidentStartedAt && new Date(rule.createdAt) > new Date(incidentStartedAt) && (
                    <span className="text-xs text-foreground-muted">(인시던트 {relativeLabel(rule.createdAt, incidentStartedAt)}에 생성)</span>
                  )}
                  {isOwner && (
                    <button
                      type="button"
                      className="ml-auto text-xs text-foreground-muted underline"
                      onClick={() => saveRules(ops.alertRules.filter((r) => r.id !== rule.id).map(toDraft))}
                    >
                      삭제
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
          {isOwner && (
            <>
              <div className="mb-3 flex flex-wrap gap-1.5">
                {ops.suggestedRules
                  .filter((s) => !ops.alertRules.some((r) => r.metric === s.metric && r.op === s.op && r.threshold === s.threshold))
                  .map((s) => (
                    <button
                      key={s.id}
                      type="button"
                      onClick={() => saveRules([...ops.alertRules.map(toDraft), { ...toDraft(s), id: undefined }])}
                      className="rounded-full border border-dashed border-border px-2.5 py-0.5 text-xs text-foreground-muted hover:border-accent hover:text-foreground"
                    >
                      + {metricLabel(s.metric)} {s.op} {formatValue(s.threshold, metricUnit(s.metric))} ({s.forSeconds}초)
                    </button>
                  ))}
              </div>
              <div className="flex flex-wrap items-end gap-2 text-xs">
                <select
                  value={draft.metric}
                  onChange={(e) => setDraft({ ...draft, metric: e.target.value })}
                  className="min-w-0 rounded border border-border bg-transparent px-2 py-1"
                  aria-label="지표"
                >
                  {ops.metrics.map((m) => (
                    <option key={m.key} value={m.key}>
                      {m.label} ({m.unit})
                    </option>
                  ))}
                </select>
                <select
                  value={draft.op}
                  onChange={(e) => setDraft({ ...draft, op: e.target.value as ">" | "<" })}
                  className="rounded border border-border bg-transparent px-2 py-1"
                  aria-label="조건"
                >
                  <option value=">">&gt;</option>
                  <option value="<">&lt;</option>
                </select>
                <input
                  type="number"
                  step="any"
                  value={draft.threshold}
                  onChange={(e) => setDraft({ ...draft, threshold: Number(e.target.value) })}
                  className="w-24 rounded border border-border bg-transparent px-2 py-1 font-mono"
                  aria-label="임계값"
                />
                <label className="flex items-center gap-1">
                  <input
                    type="number"
                    min={0}
                    max={600}
                    value={draft.forSeconds}
                    onChange={(e) => setDraft({ ...draft, forSeconds: Number(e.target.value) })}
                    className="w-16 rounded border border-border bg-transparent px-2 py-1 font-mono"
                    aria-label="지속 시간"
                  />
                  초 지속
                </label>
                <select
                  value={draft.severity}
                  onChange={(e) => setDraft({ ...draft, severity: e.target.value as "WARN" | "CRITICAL" })}
                  className="rounded border border-border bg-transparent px-2 py-1"
                  aria-label="심각도"
                >
                  <option value="WARN">WARN</option>
                  <option value="CRITICAL">CRITICAL</option>
                </select>
                <Button size="sm" onClick={() => saveRules([...ops.alertRules.map(toDraft), draft])}>
                  규칙 추가
                </Button>
              </div>
            </>
          )}
          {error && <p className="mt-2 text-xs text-danger">{error}</p>}
        </Card>
      )}
    </div>
  );
}
