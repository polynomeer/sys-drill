"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ApiError, Postmortem, getPostmortem, savePostmortem } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { formatDuration, formatMs, formatPercent } from "@/lib/metrics";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
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
      <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-4 p-8">
        <p className="text-sm text-danger">{loadError}</p>
      </div>
    );
  }

  if (loading || !postmortem) {
    return (
      <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-4 p-8">
        <LoadingState />
      </div>
    );
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-6 p-8">
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
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <div>
                <p className="text-xs text-foreground-muted">MTTD (최초 대응까지)</p>
                <p className="font-mono text-lg font-medium">
                  {postmortem.mttdSeconds !== null ? formatDuration(postmortem.mttdSeconds) : "-"}
                </p>
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

            <ul className="mt-4 flex flex-col gap-1 border-t border-border pt-3 text-xs text-foreground-muted  dark:text-foreground-muted">
              {postmortem.actionsTimeline.map((action, i) => (
                <li key={i}>
                  +{formatDuration(action.elapsedSeconds)} — {action.label}
                </li>
              ))}
            </ul>
          </>
        )}
      </Card>

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
  );
}
