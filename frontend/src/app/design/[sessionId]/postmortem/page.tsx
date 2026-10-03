"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ApiError, Benchmark, Postmortem, PostmortemInvestigation, RunbookStep, getBenchmark, getPostmortem, savePostmortem } from "@/lib/api";
import { RunbookPanel } from "@/components/RunbookPanel";
import { ChangeReviewCard } from "@/components/ChangeReviewCard";
import { getStoredToken } from "@/lib/localSession";
import { formatDuration, formatMs, formatPercent } from "@/lib/metrics";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { BenchmarkRow } from "@/components/BenchmarkRow";
import { LoadingState } from "@/components/ui/LoadingState";

const toLines = (value: string): string[] =>
  value
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

const toText = (values: string[]): string => values.join("\n");

/** PLAN.md step 26 — MTTD/MTTR/조치 타임라인/전후 지표는 항상 서버가 AppliedAction으로부터
 * 다시 계산해 내려준다(ADR-0011 계보); 이 페이지가 들고 있는 상태는 사용자가 직접 쓰는
 * 서술 필드(근본 원인/완화·근본 조치/재발 방지 항목)뿐이다. */
export default function PostmortemPage() {
  const params = useParams<{ sessionId: string }>();
  const router = useRouter();
  const sessionId = params.sessionId;

  const [postmortem, setPostmortem] = useState<Postmortem | null>(null);
  const [benchmark, setBenchmark] = useState<Benchmark | null>(null);
  const [rootCause, setRootCause] = useState("");
  const [mitigationText, setMitigationText] = useState("");
  const [rootFixText, setRootFixText] = useState("");
  const [preventionText, setPreventionText] = useState("");

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [savedJustNow, setSavedJustNow] = useState(false);

  const load = useCallback(async () => {
    const data = await getPostmortem(sessionId);
    setPostmortem(data);
    // 벤치마크는 부가 정보다 — 실패해도 포스트모템 화면 자체는 떠야 한다.
    getBenchmark(sessionId).then(setBenchmark).catch(() => setBenchmark(null));
    setRootCause(data.rootCause ?? "");
    setMitigationText(toText(data.mitigationActions));
    setRootFixText(toText(data.rootFixActions));
    setPreventionText(toText(data.preventionItems));
  }, [sessionId]);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }

    load()
      .catch(() => setLoadError("포스트모템을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  async function handleSave() {
    setSaving(true);
    setSaveError(null);
    setSavedJustNow(false);
    try {
      const updated = await savePostmortem(sessionId, {
        rootCause,
        mitigationActions: toLines(mitigationText),
        rootFixActions: toLines(rootFixText),
        preventionItems: toLines(preventionText),
      });
      setPostmortem(updated);
      setSavedJustNow(true);
    } catch (err) {
      setSaveError(
        err instanceof ApiError && err.status === 409
          ? "세션이 완료된 뒤에만 포스트모템을 저장할 수 있습니다."
          : "포스트모템을 저장하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  if (loadError) {
    return (
      <div className="mx-auto flex min-h-screen w-full max-w-7xl flex-col gap-4 p-8">
        <p className="text-sm text-danger">{loadError}</p>
      </div>
    );
  }

  if (loading || !postmortem) {
    return (
      <div className="mx-auto flex min-h-screen w-full max-w-7xl flex-col gap-4 p-8">
        <LoadingState />
      </div>
    );
  }

  return (
    <div className="mx-auto flex min-h-screen w-full max-w-7xl flex-col gap-6 p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">포스트모템</h1>
        <Link href={`/report/${sessionId}`} className="text-sm underline">
          리포트로
        </Link>
      </div>

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">인시던트 요약 (자동 계산)</h2>
        {postmortem.actionsTimeline.length === 0 ? (
          <p className="text-sm text-foreground-muted">이 세션은 인시던트를 시작하지 않아 자동 계산할 데이터가 없습니다.</p>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
              {/* docs/OBSERVABILITY_UI_PLAN.md O5 (PLAN.md Round E12) — detection, separate from the first action (MTTD). */}
              <div>
                <p className="text-xs text-foreground-muted">첫 알림까지 (탐지)</p>
                <p className="font-mono text-lg font-medium">
                  {postmortem.firstAlertSeconds !== null ? formatDuration(postmortem.firstAlertSeconds) : "-"}
                </p>
                <p className="text-[11px] text-foreground-muted">
                  {postmortem.alertRuleCount === 0
                    ? "알림 규칙 없음"
                    : `규칙 ${postmortem.alertRuleCount}개${postmortem.falseAlarmCount > 0 ? ` · 정상 구간 발화 ${postmortem.falseAlarmCount}회` : ""}`}
                </p>
              </div>
              <div>
                <p className="text-xs text-foreground-muted">MTTD (최초 대응까지)</p>
                <p className="font-mono text-lg font-medium">
                  {postmortem.mttdSeconds !== null ? formatDuration(postmortem.mttdSeconds) : "-"}
                </p>
              </div>
              {/* docs/DRILLS_EXPANSION_PLAN.md M5 (PLAN.md Round E13) — declared recovery, next to MTTR (unchanged). */}
              <div>
                <p className="text-xs text-foreground-muted">복구 선언까지</p>
                <p className="font-mono text-lg font-medium">
                  {postmortem.resolvedSeconds !== null ? formatDuration(postmortem.resolvedSeconds) : "-"}
                </p>
                {postmortem.recoveryStatus && (
                  <p className={`text-[11px] ${postmortem.recoveryStatus === "RECOVERED" ? "text-success" : "text-warning"}`}>
                    {postmortem.recoveryStatus === "RECOVERED"
                      ? "복구 완료"
                      : postmortem.recoveryStatus === "PARTIAL"
                        ? `⚠ 부분 복구${postmortem.residualBacklog > 0 ? ` — 적체 ${postmortem.residualBacklog.toLocaleString()}건 남음` : ""}`
                        : "복구되지 않은 채 선언"}
                  </p>
                )}
              </div>
              <div>
                <p className="text-xs text-foreground-muted">MTTR (마지막 조치까지)</p>
                <p className="font-mono text-lg font-medium">
                  {postmortem.mttrSeconds !== null ? formatDuration(postmortem.mttrSeconds) : "-"}
                </p>
              </div>
              {postmortem.metricsBefore && (
                <div>
                  <p className="text-xs text-foreground-muted">Error Rate (발생 → 종료)</p>
                  <p className="font-mono text-lg font-medium">
                    {formatPercent(postmortem.metricsBefore.errorRate)} → {" "}
                    {postmortem.metricsAfter ? formatPercent(postmortem.metricsAfter.errorRate) : "-"}
                  </p>
                </div>
              )}
              {postmortem.metricsBefore && (
                <div>
                  <p className="text-xs text-foreground-muted">p95 Latency (발생 → 종료)</p>
                  <p className="font-mono text-lg font-medium">
                    {formatMs(postmortem.metricsBefore.p95LatencyMs)} → {" "}
                    {postmortem.metricsAfter ? formatMs(postmortem.metricsAfter.p95LatencyMs) : "-"}
                  </p>
                </div>
              )}
            </div>

            {postmortem.integrity.length > 0 && (
              <ul className="mt-4 flex flex-col gap-1 border-t border-border pt-3 text-xs">
                {postmortem.integrity.map((item) => (
                  <li key={item.key}>
                    <span className={item.ok ? "text-success" : "text-warning"}>{item.ok ? "✓" : "⚠"}</span> 정합성 — {item.label}:{" "}
                    <span className="text-foreground-muted">{item.detail}</span>
                  </li>
                ))}
              </ul>
            )}

            <ul className="mt-4 flex flex-col gap-1 border-t border-border pt-3 text-xs text-foreground-muted  dark:text-foreground-muted">
              {[
                ...postmortem.actionsTimeline.map((a) => ({ at: a.elapsedSeconds, look: false, text: a.label })),
                ...(postmortem.investigations ?? []).map((v) => ({ at: v.elapsedSeconds, look: true, text: investigationLabel(v) })),
              ]
                .sort((a, b) => a.at - b.at)
                .map((row, i) => (
                  <li key={i} className={row.look ? "opacity-70" : "text-foreground"}>
                    {row.at < 0 ? "-" : "+"}
                    {formatDuration(Math.abs(row.at))} — {row.look ? "🔍 " : ""}
                    {row.text}
                  </li>
                ))}
            </ul>
            {(postmortem.investigations?.length ?? 0) > 0 && (
              <p className="mt-2 text-xs text-foreground-muted">🔍 표시는 조치 전후에 무엇을 보고 판단했는지(조사 기록)입니다. 점수에는 반영되지 않습니다.</p>
            )}
          </>
        )}
      </Card>

      <div className="flex flex-col gap-6 lg:grid lg:grid-cols-[minmax(0,1fr)_360px] lg:items-start">
        <aside className="flex min-w-0 flex-col gap-6 lg:col-start-2 lg:row-start-1">
          {benchmark && postmortem.actionsTimeline.length > 0 && (
            <Card as="section">
              <div className="mb-1 flex items-baseline justify-between">
                <h2 className="text-sm font-semibold text-foreground-muted">커뮤니티 비교</h2>
                <span className="text-xs text-foreground-muted">
                  같은 시나리오 완료 {benchmark.sampleSize}명
                </span>
              </div>
              <p className="mb-2 text-xs text-foreground-muted">
                같은 시나리오 버전을 완료한 세션만 비교합니다 — 버전이 다르면 인시던트가 달라집니다.
              </p>
              <div className="divide-y divide-border">
                <BenchmarkRow label="MTTD (최초 대응까지)" metric={benchmark.mttdSeconds} format={formatDuration} />
                <BenchmarkRow label="MTTR (마지막 조치까지)" metric={benchmark.mttrSeconds} format={formatDuration} />
                <BenchmarkRow label="세션 평균 점수" metric={benchmark.score} format={(v) => `${v}점`} />
              </div>
            </Card>
          )}

          <ChangeReviewCard sessionId={sessionId} />

          {/* docs/DRILLS_EXPANSION_PLAN.md M12 (PLAN.md Round E27) */}
          {postmortem.actionsTimeline.length > 0 && <RunbookPanel sessionId={sessionId} suggestions={runbookSuggestions(postmortem)} />}
        </aside>

        <div className="flex min-w-0 flex-col gap-6 lg:col-start-1 lg:row-start-1">
          <Card as="section" className="flex flex-col gap-4">
            <h2 className="text-sm font-semibold text-foreground-muted">직접 작성</h2>

            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium">근본 원인</span>
              <textarea
                value={rootCause}
                onChange={(e) => setRootCause(e.target.value)}
                rows={3}
                className="rounded border border-border p-2 text-sm  "
                placeholder="지표 변화의 근본 원인을 서술하세요."
              />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium">임시 완화 조치 (한 줄에 하나씩)</span>
              <textarea
                value={mitigationText}
                onChange={(e) => setMitigationText(e.target.value)}
                rows={3}
                className="rounded border border-border p-2 font-mono text-xs  "
                placeholder="당장 상황을 막았지만 근본 해결은 아닌 조치"
              />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium">근본 해결 조치 (한 줄에 하나씩)</span>
              <textarea
                value={rootFixText}
                onChange={(e) => setRootFixText(e.target.value)}
                rows={3}
                className="rounded border border-border p-2 font-mono text-xs  "
                placeholder="원인 자체를 없앤 조치 (지금 적용했거나 앞으로 적용할 것)"
              />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span className="font-medium">재발 방지 액션 아이템 (한 줄에 하나씩)</span>
              <textarea
                value={preventionText}
                onChange={(e) => setPreventionText(e.target.value)}
                rows={3}
                className="rounded border border-border p-2 font-mono text-xs  "
                placeholder="같은 장애가 재발하지 않도록 만들 구조적 개선"
              />
            </label>

            <div className="flex items-center gap-3">
              <Button variant="secondary" onClick={handleSave} disabled={saving || rootCause.trim().length === 0}>
                {saving ? "저장하는 중..." : "저장"}
              </Button>
              {savedJustNow && <span className="text-sm text-success">저장됨</span>}
              {saveError && <span className="text-sm text-danger">{saveError}</span>}
            </div>
          </Card>

          {postmortem.saved &&
            (postmortem.coachStrengths.length > 0 || postmortem.coachGaps.length > 0 || postmortem.coachFollowupQuestions.length > 0) && (
              <Card as="section" className="flex flex-col gap-4">
                <h2 className="text-sm font-semibold text-foreground-muted">AI 코치 피드백</h2>
                {postmortem.coachStrengths.length > 0 && (
                  <div>
                    <p className="mb-1 text-sm font-medium">잘한 점</p>
                    <ul className="list-disc space-y-1 pl-5 text-sm text-foreground-muted">
                      {postmortem.coachStrengths.map((item, i) => (
                        <li key={i}>{item}</li>
                      ))}
                    </ul>
                  </div>
                )}
                {postmortem.coachGaps.length > 0 && (
                  <div>
                    <p className="mb-1 text-sm font-medium">보완할 점</p>
                    <ul className="list-disc space-y-1 pl-5 text-sm text-foreground-muted">
                      {postmortem.coachGaps.map((item, i) => (
                        <li key={i}>{item}</li>
                      ))}
                    </ul>
                  </div>
                )}
                {postmortem.coachFollowupQuestions.length > 0 && (
                  <div>
                    <p className="mb-1 text-sm font-medium">추가로 생각해볼 질문</p>
                    <ul className="list-disc space-y-1 pl-5 text-sm text-foreground-muted">
                      {postmortem.coachFollowupQuestions.map((item, i) => (
                        <li key={i}>{item}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </Card>
            )}
        </div>
      </div>
    </div>
  );
}

const PANEL_LABELS: Record<string, string> = { map: "서비스 맵", metrics: "지표", alerts: "알림", logs: "로그", changes: "변경 이력" };

/** PLAN.md Round E17 (O0-b) — one investigation as a timeline line. */
function investigationLabel(v: PostmortemInvestigation): string {
  switch (v.kind) {
    case "OPEN_PANEL":
      return `${PANEL_LABELS[v.target ?? ""] ?? v.target ?? ""} 탭 열람`;
    case "INSPECT_NODE":
      return `서비스 맵에서 ${v.target ?? ""} 확인`;
    case "QUERY_LOGS":
      return `로그 검색 "${v.target ?? ""}"`;
    case "OPEN_TRACE":
      return `트레이스 ${v.target ?? ""} 열람`;
  }
}

/** M12 — this session's own looks and actions, in order, as one-click runbook steps (repeats dropped). */
function runbookSuggestions(p: Postmortem): RunbookStep[] {
  const rows = [
    ...(p.investigations ?? []).filter((v) => v.elapsedSeconds >= 0).map((v) => ({ at: v.elapsedSeconds, step: { type: v.kind, target: v.target, text: "" } as RunbookStep })),
    ...p.actionsTimeline.map((a) => ({ at: a.elapsedSeconds, step: { type: "ACTION", target: a.actionType, text: "" } as RunbookStep })),
  ].sort((a, b) => a.at - b.at);
  const seen = new Set<string>();
  return rows
    .map((r) => r.step)
    .filter((s) => {
      const key = `${s.type}:${s.target}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}
