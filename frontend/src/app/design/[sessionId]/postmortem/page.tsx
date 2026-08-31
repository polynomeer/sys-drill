"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ApiError, Postmortem, getPostmortem, savePostmortem } from "@/lib/api";
import { getStoredUserId } from "@/lib/localSession";
import { formatDuration, formatMs, formatPercent } from "@/lib/metrics";

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
    if (!getStoredUserId()) {
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
        <p className="text-sm text-red-600">{loadError}</p>
      </div>
    );
  }

  if (loading || !postmortem) {
    return (
      <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-4 p-8">
        <p className="text-sm text-zinc-500">불러오는 중...</p>
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

      <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <h2 className="mb-3 text-sm font-semibold text-zinc-500">인시던트 요약 (자동 계산)</h2>
        {postmortem.actionsTimeline.length === 0 ? (
          <p className="text-sm text-zinc-500">이 세션은 인시던트를 시작하지 않아 자동 계산할 데이터가 없습니다.</p>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <div>
                <p className="text-xs text-zinc-500">MTTD (최초 대응까지)</p>
                <p className="font-mono text-lg font-medium">
                  {postmortem.mttdSeconds !== null ? formatDuration(postmortem.mttdSeconds) : "-"}
                </p>
              </div>
              <div>
                <p className="text-xs text-zinc-500">MTTR (마지막 조치까지)</p>
                <p className="font-mono text-lg font-medium">
                  {postmortem.mttrSeconds !== null ? formatDuration(postmortem.mttrSeconds) : "-"}
                </p>
              </div>
              {postmortem.metricsBefore && (
                <div>
                  <p className="text-xs text-zinc-500">Error Rate (발생 → 종료)</p>
                  <p className="font-mono text-lg font-medium">
                    {formatPercent(postmortem.metricsBefore.errorRate)} → {" "}
                    {postmortem.metricsAfter ? formatPercent(postmortem.metricsAfter.errorRate) : "-"}
                  </p>
                </div>
              )}
              {postmortem.metricsBefore && (
                <div>
                  <p className="text-xs text-zinc-500">p95 Latency (발생 → 종료)</p>
                  <p className="font-mono text-lg font-medium">
                    {formatMs(postmortem.metricsBefore.p95LatencyMs)} → {" "}
                    {postmortem.metricsAfter ? formatMs(postmortem.metricsAfter.p95LatencyMs) : "-"}
                  </p>
                </div>
              )}
            </div>

            <ul className="mt-4 flex flex-col gap-1 border-t border-zinc-200 pt-3 text-xs text-zinc-600 dark:border-zinc-800 dark:text-zinc-400">
              {postmortem.actionsTimeline.map((action, i) => (
                <li key={i}>
                  +{formatDuration(action.elapsedSeconds)} — {action.label}
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      <section className="flex flex-col gap-4 rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <h2 className="text-sm font-semibold text-zinc-500">직접 작성</h2>

        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">근본 원인</span>
          <textarea
            value={rootCause}
            onChange={(e) => setRootCause(e.target.value)}
            rows={3}
            className="rounded border border-zinc-300 p-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
            placeholder="지표 변화의 근본 원인을 서술하세요."
          />
        </label>

        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">임시 완화 조치 (한 줄에 하나씩)</span>
          <textarea
            value={mitigationText}
            onChange={(e) => setMitigationText(e.target.value)}
            rows={3}
            className="rounded border border-zinc-300 p-2 font-mono text-xs dark:border-zinc-700 dark:bg-zinc-900"
            placeholder="당장 상황을 막았지만 근본 해결은 아닌 조치"
          />
        </label>

        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">근본 해결 조치 (한 줄에 하나씩)</span>
          <textarea
            value={rootFixText}
            onChange={(e) => setRootFixText(e.target.value)}
            rows={3}
            className="rounded border border-zinc-300 p-2 font-mono text-xs dark:border-zinc-700 dark:bg-zinc-900"
            placeholder="원인 자체를 없앤 조치 (지금 적용했거나 앞으로 적용할 것)"
          />
        </label>

        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">재발 방지 액션 아이템 (한 줄에 하나씩)</span>
          <textarea
            value={preventionText}
            onChange={(e) => setPreventionText(e.target.value)}
            rows={3}
            className="rounded border border-zinc-300 p-2 font-mono text-xs dark:border-zinc-700 dark:bg-zinc-900"
            placeholder="같은 장애가 재발하지 않도록 만들 구조적 개선"
          />
        </label>

        <div className="flex items-center gap-3">
          <button
            onClick={handleSave}
            disabled={saving || rootCause.trim().length === 0}
            className="self-start rounded border border-zinc-300 px-4 py-2 text-sm font-medium disabled:opacity-50 dark:border-zinc-700"
          >
            {saving ? "저장하는 중..." : "저장"}
          </button>
          {savedJustNow && <span className="text-sm text-green-600 dark:text-green-400">저장됨</span>}
          {saveError && <span className="text-sm text-red-600">{saveError}</span>}
        </div>
      </section>
    </div>
  );
}
