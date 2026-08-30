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

type ActionDef = { type: SimulationActionType; label: string; effect: string };

const ACTIONS_BY_DOMAIN: Record<string, ActionDef[]> = {
  coupon: [
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
  ],
  notification: [
    {
      type: "ENABLE_CIRCUIT_BREAKER",
      label: "Circuit Breaker 활성화",
      effect: "긍정 효과: 죽은 provider를 기다리지 않아 컨슈머가 빠르게 회복. 부작용: breaker OPEN 동안 해당 provider 메시지 유실/지연 가능.",
    },
    {
      type: "ADD_CONSUMERS",
      label: "컨슈머 증설",
      effect: "긍정 효과: 처리량 증가로 backlog 감소. 부작용: provider 동시 호출 증가.",
    },
    {
      type: "ADJUST_RETRY_BACKOFF",
      label: "Retry Backoff 조정",
      effect: "긍정 효과: 재시도 폭풍(retry storm) 완화. 부작용: 개별 메시지 전달 지연 증가.",
    },
  ],
  "product-browsing": [
    {
      type: "SPLIT_CACHE_POLICY",
      label: "캐시 정책 분리",
      effect: "긍정 효과: 데이터 특성별 TTL 분리로 hit ratio 회복. 부작용: 캐시 정책 복잡도 증가.",
    },
    {
      type: "ENABLE_SINGLE_FLIGHT",
      label: "Single-flight 적용",
      effect: "긍정 효과: 동시 cache miss의 DB 요청 중복(dogpile) 제거. 부작용: 요청 간 대기 발생 가능.",
    },
    {
      type: "ADD_READ_REPLICA",
      label: "Read Replica 추가",
      effect: "긍정 효과: DB read 용량 증가. 부작용: replica lag으로 조회 최신성 저하.",
    },
  ],
  payment: [
    {
      type: "ADD_DISPATCHER_WORKERS",
      label: "디스패처 증설",
      effect: "긍정 효과: outbox 처리량 증가로 backlog 감소. 부작용: 외부 PG에 대한 동시 호출 증가.",
    },
    {
      type: "ENABLE_IDEMPOTENT_PG_RETRY",
      label: "멱등성 키 적용",
      effect: "긍정 효과: 응답 유실로 인한 재시도가 중복 처리를 만들지 않음. 부작용: 멱등성 키 저장·조회 비용 추가.",
    },
    {
      type: "ISOLATE_PAYMENT_POOL",
      label: "결제 커넥션 풀 격리",
      effect: "긍정 효과: outbox backlog가 주문 처리용 풀로 번지지 않음(bulkhead). 부작용: 결제 전용 풀 자체가 포화되면 그 안에서는 여전히 지연.",
    },
  ],
  reservation: [
    {
      type: "ENABLE_FINE_GRAINED_LOCKING",
      label: "락 세분화 (좌석 단위)",
      effect: "긍정 효과: 무관한 좌석 간 경합 제거로 유효 처리 용량 증가. 부작용: 락 구현·관리 복잡도 증가.",
    },
    {
      type: "SHORTEN_HOLD_TIMEOUT",
      label: "홀드 타임아웃 단축",
      effect: "긍정 효과: 결제 미완료로 이탈한 홀드의 자원 점유 시간 감소. 부작용: 정상 사용자가 실제 필요 시간보다 일찍 홀드가 풀릴 위험.",
    },
    {
      type: "ENABLE_ATOMIC_INVENTORY_CHECK",
      label: "원자적 재고 확인",
      effect: "긍정 효과: 재고 확인·확정 사이 경쟁으로 인한 낭비성 재시도 제거. 부작용: 원자적 처리를 위한 락/트랜잭션 범위 확대.",
    },
  ],
  "batch-settlement": [
    {
      type: "ENABLE_CHECKPOINT_RESTART",
      label: "체크포인트 재개 활성화",
      effect: "긍정 효과: 실패 시 처음부터가 아니라 실패한 청크부터 재개해 낭비 작업량 대폭 감소. 부작용: 체크포인트 저장·조회 비용 추가.",
    },
    {
      type: "REDUCE_CHUNK_SIZE",
      label: "청크 크기 축소",
      effect: "긍정 효과: 실패 시 재처리 범위 축소. 부작용: 청크당 커밋 오버헤드 비중 증가로 정상 처리량 감소.",
    },
    {
      type: "ENABLE_IDEMPOTENT_RECONCILIATION",
      label: "멱등한 정산 재처리",
      effect: "긍정 효과: 재처리된 레코드가 중복 반영되지 않아 정산 정합성 유지. 부작용: 레코드별 처리 이력 저장·조회 비용 추가.",
    },
  ],
};

const INCIDENT_EVENT_BY_DOMAIN: Record<string, string> = {
  coupon: "인시던트 발생: 트래픽 20배 급증, Redis latency 상승 → DB write hotspot",
  notification: "인시던트 발생: provider timeout → 재시도 폭증 → consumer lag 증가",
  "product-browsing": "인시던트 발생: hot key 트래픽 집중 → cache miss 폭증 → DB read latency 급증",
  payment: "인시던트 발생: PG timeout 급증 → outbox 재시도 폭증 → 주문 처리 지연 전이",
  reservation: "인시던트 발생: 인기 좌석에 예약 시도 집중 → 락 경합 급증 → 락 대기 시간 증가",
  "batch-settlement": "인시던트 발생: 정산 API 응답 지연 급증 → 처리 중이던 청크 실패 → 재처리 범위 및 중복 반영 위험 증가",
};

const POLL_INTERVAL_MS = 3000;

/** PLAN.md step 7's MetricsPanel/ActionPanel — EventStream/Timeline are merged into
 * one client-side log below since there's no backend timeline API yet. Actions and
 * the incident event text are keyed by scenario domain (PLAN.md step 11). */
export function WargameLive({ sessionId, domain }: { sessionId: string; domain: string }) {
  const ACTIONS = ACTIONS_BY_DOMAIN[domain] ?? ACTIONS_BY_DOMAIN.coupon;
  const [state, setState] = useState<SystemState | null>(null);
  const [appliedActions, setAppliedActions] = useState<Set<SimulationActionType>>(new Set());
  const [events, setEvents] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [applying, setApplying] = useState<SimulationActionType | null>(null);
  const started = useRef(false);

  // PLAN.md step 21 — only the coupon domain has a real-infra opt-in, so only it
  // ever shows this pre-start gate; every other domain keeps auto-starting
  // immediately, unchanged.
  const [awaitingStartChoice, setAwaitingStartChoice] = useState(domain === "coupon");
  const [realInfraChoice, setRealInfraChoice] = useState(false);

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
        addEvent(INCIDENT_EVENT_BY_DOMAIN[domain] ?? INCIDENT_EVENT_BY_DOMAIN.coupon);
      }
    }
  }, [sessionId, domain, addEvent]);

  useEffect(() => {
    if (awaitingStartChoice) return;
    // Data fetch on mount, not a cascading render loop.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    refreshState();
    const timer = setInterval(refreshState, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [refreshState, awaitingStartChoice]);

  async function handleManualStart() {
    started.current = true;
    setAwaitingStartChoice(false);
    try {
      const initial = await startIncident(sessionId, realInfraChoice);
      setState(initial);
      addEvent(
        realInfraChoice
          ? "실전 인프라 인시던트 시작: 실제 Postgres 전용 스키마·커넥션 풀, Toxiproxy로 주입한 실제 네트워크 지연, 실제 k6 부하로 지표를 측정합니다."
          : (INCIDENT_EVENT_BY_DOMAIN[domain] ?? INCIDENT_EVENT_BY_DOMAIN.coupon)
      );
    } catch {
      setError("인시던트를 시작하지 못했습니다.");
    }
  }

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

  if (awaitingStartChoice) {
    return (
      <div className="flex flex-col gap-4 rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <h2 className="text-sm font-semibold text-zinc-500">인시던트 시작 방식 선택</h2>
        <label className="flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            checked={realInfraChoice}
            onChange={(e) => setRealInfraChoice(e.target.checked)}
            className="mt-1"
          />
          <span>
            실전 인프라로 시작 (실험적) — 실제 Postgres 전용 스키마·커넥션 풀, 실제 네트워크 지연(Toxiproxy 주입),
            실제 k6 부하로 지표를 측정합니다. 체크하지 않으면 기존과 동일한 규칙 기반 시뮬레이션입니다.
          </span>
        </label>
        <button
          onClick={handleManualStart}
          className="self-start rounded border border-zinc-300 px-4 py-2 text-sm font-medium dark:border-zinc-700"
        >
          인시던트 시작
        </button>
      </div>
    );
  }

  if (!state) {
    return <p className="text-sm text-zinc-500">시뮬레이션을 시작하는 중...</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      <MetricsPanel state={state} domain={domain} />
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

type Metric = { label: string; value: string; colorFor?: number };

function MetricsPanel({ state, domain }: { state: SystemState; domain: string }) {
  const common: Metric[] = [
    { label: "Traffic", value: `${state.trafficRps.toFixed(0)} rps` },
    { label: "p95 Latency", value: formatMs(state.p95LatencyMs) },
    { label: "Error Rate", value: formatPercent(state.errorRate), colorFor: state.errorRate },
    { label: "Availability", value: formatPercent(state.availability) },
  ];
  const domainSpecificByDomain: Record<string, Metric[]> = {
    notification: [
      { label: "Queue Lag", value: `${state.queueLag}`, colorFor: state.queueLag > 0 ? 0.9 : 0 },
      { label: "Consumer Throughput", value: `${state.consumerThroughput.toFixed(1)}/s` },
      { label: "Provider Latency", value: formatMs(state.externalDependencyLatencyMs) },
    ],
    "product-browsing": [
      { label: "DB Read Load", value: formatPercent(state.dbReadLoad), colorFor: state.dbReadLoad },
      { label: "Cache Hit Ratio", value: formatPercent(state.cacheHitRatio) },
    ],
    payment: [
      { label: "Outbox Backlog", value: `${state.queueLag}`, colorFor: state.queueLag > 0 ? 0.9 : 0 },
      { label: "Connection Pool", value: formatPercent(state.connectionPoolUsage), colorFor: state.connectionPoolUsage },
      { label: "PG Latency", value: formatMs(state.externalDependencyLatencyMs) },
    ],
    coupon: [
      { label: "DB Read Load", value: formatPercent(state.dbReadLoad), colorFor: state.dbReadLoad },
      { label: "DB Write Load", value: formatPercent(state.dbWriteLoad), colorFor: state.dbWriteLoad },
      { label: "Cache Hit Ratio", value: formatPercent(state.cacheHitRatio) },
      { label: "Cache Latency", value: formatMs(state.cacheLatencyMs) },
      // Always 0ms for the rule-based engine — only real-infra sessions (PLAN.md
      // step 23) inject a genuine network fault here via Toxiproxy.
      { label: "DB 네트워크 지연 (실전 인프라)", value: formatMs(state.externalDependencyLatencyMs) },
    ],
    reservation: [
      { label: "Lock Wait Queue", value: `${state.queueLag}`, colorFor: state.queueLag > 0 ? 0.9 : 0 },
      { label: "Lock Capacity", value: `${state.consumerThroughput.toFixed(1)}/s` },
      { label: "Lock Utilization", value: formatPercent(state.dbWriteLoad), colorFor: state.dbWriteLoad },
    ],
    "batch-settlement": [
      { label: "재처리 대상 레코드", value: `${state.queueLag}`, colorFor: state.queueLag > 0 ? 0.9 : 0 },
      { label: "처리 처리량", value: `${state.consumerThroughput.toFixed(1)} rec/s` },
      { label: "재처리 부하율", value: formatPercent(state.dbWriteLoad), colorFor: state.dbWriteLoad },
    ],
  };
  const metrics = [...common, ...(domainSpecificByDomain[domain] ?? domainSpecificByDomain.coupon)];

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
