"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  ApiError,
  SimulationActionType,
  SystemState,
  applySimulationAction,
  getSimulationState,
  startIncident,
} from "@/lib/api";
import { formatMs, formatPercent, utilizationColorClass } from "@/lib/metrics";

const ACTIONS: { type: SimulationActionType; label: string; effect: string }[] = [
  {
    type: "STRENGTHEN_RATE_LIMIT",
    label: "Rate Limit 강화",
    effect: "긍정 효과: DB/다운스트림 보호. 부작용: 일부 사용자 거절, UX 저하.",
  },
  {
    type: "INCREASE_CACHE_TTL",
    label: "Cache TTL 조정",
    effect: "긍정 효과: DB 부하·latency 감소. 부작용: stale data 위험.",
  },
  {
    type: "INCREASE_DB_POOL",
    label: "DB Pool 증가",
    effect: "긍정 효과: 대기 요청 일부 감소. 부작용: DB 자체 한계 초과 가능.",
  },
];

const POLL_INTERVAL_MS = 3000;

/** PLAN.md step 7's MetricsPanel/ActionPanel — EventStream/Timeline are merged into
 * one client-side log below since there's no backend timeline API yet. */
export function WargameLive({ sessionId }: { sessionId: string }) {
  const [state, setState] = useState<SystemState | null>(null);
  const [appliedActions, setAppliedActions] = useState<Set<SimulationActionType>>(new Set());
  const [events, setEvents] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [applying, setApplying] = useState<SimulationActionType | null>(null);
  const started = useRef(false);

  const addEvent = useCallback((message: string) => {
    setEvents((prev) => [...prev, `${new Date().toLocaleTimeString()} — ${message}`]);
  }, []);

  const refreshState = useCallback(async () => {
    try {
      const current = await getSimulationState(sessionId);
      setState(current);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404 && !started.current) {
        started.current = true;
        const initial = await startIncident(sessionId);
        setState(initial);
        addEvent("인시던트 발생: 트래픽 20배 급증, Redis latency 상승 → DB write hotspot");
      }
    }
  }, [sessionId, addEvent]);

  useEffect(() => {
    // Data fetch on mount, not a cascading render loop.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    refreshState();
    const timer = setInterval(refreshState, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [refreshState]);

  async function handleApply(actionType: SimulationActionType) {
    setApplying(actionType);
    setError(null);
    try {
      const updated = await applySimulationAction(sessionId, actionType);
      setState(updated);
      setAppliedActions((prev) => new Set(prev).add(actionType));
      addEvent(`조치 적용: ${ACTIONS.find((a) => a.type === actionType)?.label}`);
    } catch {
      setError("조치를 적용하지 못했습니다.");
    } finally {
      setApplying(null);
    }
  }

  if (!state) {
    return <p className="text-sm text-zinc-500">시뮬레이션을 시작하는 중...</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      <MetricsPanel state={state} />
      <div className="grid gap-4 md:grid-cols-2">
        <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
          <h2 className="mb-3 text-sm font-semibold text-zinc-500">대응 액션</h2>
          <div className="flex flex-col gap-2">
            {ACTIONS.map((action) => (
              <button
                key={action.type}
                onClick={() => handleApply(action.type)}
                disabled={applying !== null || appliedActions.has(action.type)}
                title={action.effect}
                className="rounded border border-zinc-300 px-3 py-2 text-left text-sm disabled:opacity-50 dark:border-zinc-700"
              >
                <span className="font-medium">
                  {appliedActions.has(action.type) ? "✓ " : ""}
                  {action.label}
                </span>
                <span className="mt-0.5 block text-xs text-zinc-500">{action.effect}</span>
              </button>
            ))}
          </div>
        </section>

        <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
          <h2 className="mb-3 text-sm font-semibold text-zinc-500">타임라인</h2>
          <ul className="flex flex-col gap-1 text-xs text-zinc-600 dark:text-zinc-400">
            {events.map((event, i) => (
              <li key={i}>{event}</li>
            ))}
          </ul>
        </section>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}
    </div>
  );
}

function MetricsPanel({ state }: { state: SystemState }) {
  const metrics: { label: string; value: string; colorFor?: number }[] = [
    { label: "Traffic", value: `${state.trafficRps.toFixed(0)} rps` },
    { label: "p95 Latency", value: formatMs(state.p95LatencyMs) },
    { label: "Error Rate", value: formatPercent(state.errorRate), colorFor: state.errorRate },
    { label: "Availability", value: formatPercent(state.availability) },
    { label: "DB Read Load", value: formatPercent(state.dbReadLoad), colorFor: state.dbReadLoad },
    { label: "DB Write Load", value: formatPercent(state.dbWriteLoad), colorFor: state.dbWriteLoad },
    { label: "Cache Hit Ratio", value: formatPercent(state.cacheHitRatio) },
    { label: "Cache Latency", value: formatMs(state.cacheLatencyMs) },
  ];

  return (
    <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
      <h2 className="mb-3 text-sm font-semibold text-zinc-500">실시간 지표</h2>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {metrics.map((metric) => (
          <div key={metric.label}>
            <p className="text-xs text-zinc-500">{metric.label}</p>
            <p
              className={`font-mono text-lg font-medium ${
                metric.colorFor !== undefined ? utilizationColorClass(metric.colorFor) : ""
              }`}
            >
              {metric.value}
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}
