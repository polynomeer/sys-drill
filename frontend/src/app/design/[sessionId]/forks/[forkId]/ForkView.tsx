"use client";

import { actionLabel } from "@/lib/actionLabels";
import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ApiError,
  type Fork,
  type ForkComparison,
  type ForkSide,
  type SimulationActionType,
  type SimulationSeries,
  applyForkAction,
  getFork,
  getForkComparison,
  getForkSeries,
} from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { formatDuration, formatPercent } from "@/lib/metrics";
import { DOMAIN_TITLES } from "@/lib/designGuidance";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";
import { MissionControlBar } from "../../MissionControlBar";
import { SeriesCharts } from "../../ObserveViews";
import { ACTIONS_BY_DOMAIN } from "../../WargameLive";

const POLL_INTERVAL_MS = 3000;

/**
 * docs/DRILLS_EXPANSION_PLAN.md M6 (PLAN.md Round E14, ADR-0046) — Counterfactual Replay.
 * The incident replayed from a chosen step with the learner's own different moves, then
 * projected against what actually happened. The fork lives an hour in Redis and is never
 * part of the session's record (no score, no benchmark, no replay).
 */
/**
 * Shared by my own Counterfactual (M6) and Fork My Run on someone else's writeup (C9) —
 * only the way back and what "the original" is called differ.
 */
export function ForkView({
  forkId,
  backHref,
  backLabel,
  originalLabel,
}: {
  forkId: string;
  backHref: string;
  backLabel: string;
  originalLabel: string;
}) {
  const router = useRouter();
  const [fork, setFork] = useState<Fork | null>(null);
  const [series, setSeries] = useState<SimulationSeries | null>(null);
  const [comparison, setComparison] = useState<ForkComparison | null>(null);
  const [applying, setApplying] = useState<SimulationActionType | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [f, s] = await Promise.all([getFork(forkId), getForkSeries(forkId)]);
      setFork(f);
      setSeries(s);
    } catch (err) {
      setError(err instanceof ApiError && err.status === 404 ? "포크가 만료됐거나 없습니다 (포크는 1시간 동안만 남습니다)." : "포크를 불러오지 못했습니다.");
    }
  }, [forkId]);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    // eslint-disable-next-line react-hooks/set-state-in-effect
    refresh();
    getForkComparison(forkId).then(setComparison).catch(() => undefined);
    const timer = setInterval(refresh, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [forkId, refresh, router]);

  async function apply(action: SimulationActionType) {
    setApplying(action);
    setError(null);
    try {
      setFork(await applyForkAction(forkId, action));
      await refresh();
      setComparison(await getForkComparison(forkId));
    } catch {
      setError("조치를 적용하지 못했습니다.");
    } finally {
      setApplying(null);
    }
  }

  if (error && !fork) {
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-3 p-8">
        <p className="text-sm text-danger">{error}</p>
        <Link href={backHref} className="text-sm underline">
          {backLabel}
        </Link>
      </div>
    );
  }
  if (!fork) return <div className="mx-auto max-w-3xl p-8"><LoadingState /></div>;

  const actions = ACTIONS_BY_DOMAIN[fork.domain] ?? [];
  const latest = series?.points.at(-1) ?? null;

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-5 p-6 md:p-8">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-foreground-muted">Counterfactual · 포크</p>
          <h1 className="text-xl font-semibold">여기서 다르게 했다면?</h1>
          <p className="mt-1 text-sm text-foreground-muted">
            원래 인시던트의 {formatDuration(fork.forkedAtSeconds)} 시점(그때까지의 조치:{" "}
            {fork.prefixActions.length === 0 ? "없음" : fork.prefixActions.map(actionLabel).join(", ")})에서 갈라졌습니다. 이 실험은 기록·점수에 남지 않고 1시간 뒤 사라집니다.
          </p>
        </div>
        <Link href={backHref} className="text-sm underline">
          {backLabel}
        </Link>
      </div>

      <MissionControlBar
        title={`${DOMAIN_TITLES[fork.domain] ?? fork.domain} (포크)`}
        latest={latest}
        incidentStartedAt={series?.incidentStartedAt ?? null}
      />

      {comparison && (
        <Card as="section">
          <h2 className="mb-1 text-sm font-semibold text-foreground-muted">{originalLabel} vs 이 포크</h2>
          <p className="mb-3 text-xs text-foreground-muted">
            양쪽 모두 지금까지의 조치를 유지한다고 보고 인시던트 시작부터 {Math.round(comparison.horizonSeconds / 60)}분까지 같은 수식으로 계산합니다.
          </p>
          <div className="grid gap-4 sm:grid-cols-2">
            <SideCard title={originalLabel} side={comparison.original} />
            <SideCard title="이 포크" side={comparison.fork} better={comparison.fork.impactSeconds < comparison.original.impactSeconds} />
          </div>
        </Card>
      )}

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">대응 액션 (포크에만 적용)</h2>
        <div className="grid gap-2 sm:grid-cols-3">
          {actions.map((action) => (
            <button
              key={action.type}
              type="button"
              onClick={() => apply(action.type)}
              disabled={applying !== null}
              title={action.effect}
              className="rounded-lg border border-border px-3 py-2 text-left text-sm hover:border-accent/40 disabled:opacity-50"
            >
              <span className="font-medium">{action.label}</span>
              {fork.actions.filter((a) => a === action.type).length > 0 && (
                <span className="ml-1 text-xs text-success">✓ {fork.actions.filter((a) => a === action.type).length}</span>
              )}
            </button>
          ))}
        </div>
        {error && <p className="mt-2 text-xs text-danger">{error}</p>}
      </Card>

      {series?.incidentStartedAt && <SeriesCharts points={series.points} incidentStartedAt={series.incidentStartedAt} />}
    </div>
  );
}

function SideCard({ title, side, better }: { title: string; side: ForkSide; better?: boolean }) {
  return (
    <div className={`rounded-lg border px-4 py-3 ${better ? "border-success/50" : "border-border"}`}>
      <p className="text-sm font-semibold">{title}</p>
      <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
        <dt className="text-xs text-foreground-muted">회복까지</dt>
        <dd className="font-mono">{side.recoveredAtSeconds === null ? "회복 안 됨" : formatDuration(side.recoveredAtSeconds)}</dd>
        <dt className="text-xs text-foreground-muted">영향 (완전 장애 환산)</dt>
        <dd className="font-mono">{formatDuration(Math.round(side.impactSeconds))}</dd>
        <dt className="text-xs text-foreground-muted">마지막 에러율</dt>
        <dd className="font-mono">{formatPercent(side.finalErrorRate)}</dd>
        <dt className="text-xs text-foreground-muted">남은 적체</dt>
        <dd className="font-mono">{side.finalBacklog.toLocaleString()}</dd>
      </dl>
      <p className="mt-2 text-[11px] text-foreground-muted">조치: {side.actions.length === 0 ? "없음" : side.actions.map(actionLabel).join(" → ")}</p>
    </div>
  );
}
