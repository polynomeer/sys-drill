"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import {
  ApiError,
  ChatMessage,
  SimulationActionType,
  SystemState,
  applySimulationAction,
  getSimulationState,
  getSimulationTimeline,
  listChatMessages,
  postChatMessage,
  startIncident,
} from "@/lib/api";
import { formatMs, formatPercent, utilizationColorClass, utilizationStatus } from "@/lib/metrics";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Gauge } from "@/components/ui/Gauge";
import { Input } from "@/components/ui/Input";
import { LogEntry, LogLevel, LogViewer } from "./LogViewer";

type ActionCategory = "scale" | "cache" | "traffic" | "config";
type ActionDef = { type: SimulationActionType; label: string; effect: string; category: ActionCategory };

/** SysDrill_UIUX_Design_Plan.docx §7 대응 액션 카테고리 — 새 백엔드 액션 타입을
 * 발명하지 않고(PLAN.md UI/UX 리뉴얼 Round 3 스코프 아웃 참고) 기존 21종을
 * 문서의 개념 카테고리로 재분류·재스타일링만 한다. */
const CATEGORY_META: Record<ActionCategory, { label: string; icon: string }> = {
  scale: { label: "스케일", icon: "📈" },
  cache: { label: "캐시", icon: "🗄️" },
  traffic: { label: "트래픽", icon: "🚦" },
  config: { label: "설정", icon: "⚙️" },
};

const ACTIONS_BY_DOMAIN: Record<string, ActionDef[]> = {
  coupon: [
    {
      type: "STRENGTHEN_RATE_LIMIT",
      label: "Rate Limit 강화",
      effect: "긍정 효과: DB/다운스트림 보호. 부작용: 일부 사용자 거절, UX 저하.",
      category: "traffic",
    },
    {
      type: "INCREASE_CACHE_TTL",
      label: "Cache TTL 조정",
      effect: "긍정 효과: DB 부하·latency 감소. 부작용: stale data 위험.",
      category: "cache",
    },
    {
      type: "INCREASE_DB_POOL",
      label: "DB Pool 증가",
      effect: "긍정 효과: 대기 요청 일부 감소. 부작용: DB 자체 한계 초과 가능.",
      category: "scale",
    },
  ],
  notification: [
    {
      type: "ENABLE_CIRCUIT_BREAKER",
      label: "Circuit Breaker 활성화",
      effect: "긍정 효과: 죽은 provider를 기다리지 않아 컨슈머가 빠르게 회복. 부작용: breaker OPEN 동안 해당 provider 메시지 유실/지연 가능.",
      category: "traffic",
    },
    {
      type: "ADD_CONSUMERS",
      label: "컨슈머 증설",
      effect: "긍정 효과: 처리량 증가로 backlog 감소. 부작용: provider 동시 호출 증가.",
      category: "scale",
    },
    {
      type: "ADJUST_RETRY_BACKOFF",
      label: "Retry Backoff 조정",
      effect: "긍정 효과: 재시도 폭풍(retry storm) 완화. 부작용: 개별 메시지 전달 지연 증가.",
      category: "traffic",
    },
  ],
  "product-browsing": [
    {
      type: "SPLIT_CACHE_POLICY",
      label: "캐시 정책 분리",
      effect: "긍정 효과: 데이터 특성별 TTL 분리로 hit ratio 회복. 부작용: 캐시 정책 복잡도 증가.",
      category: "cache",
    },
    {
      type: "ENABLE_SINGLE_FLIGHT",
      label: "Single-flight 적용",
      effect: "긍정 효과: 동시 cache miss의 DB 요청 중복(dogpile) 제거. 부작용: 요청 간 대기 발생 가능.",
      category: "cache",
    },
    {
      type: "ADD_READ_REPLICA",
      label: "Read Replica 추가",
      effect: "긍정 효과: DB read 용량 증가. 부작용: replica lag으로 조회 최신성 저하.",
      category: "scale",
    },
  ],
  payment: [
    {
      type: "ADD_DISPATCHER_WORKERS",
      label: "디스패처 증설",
      effect: "긍정 효과: outbox 처리량 증가로 backlog 감소. 부작용: 외부 PG에 대한 동시 호출 증가.",
      category: "scale",
    },
    {
      type: "ENABLE_IDEMPOTENT_PG_RETRY",
      label: "멱등성 키 적용",
      effect: "긍정 효과: 응답 유실로 인한 재시도가 중복 처리를 만들지 않음. 부작용: 멱등성 키 저장·조회 비용 추가.",
      category: "config",
    },
    {
      type: "ISOLATE_PAYMENT_POOL",
      label: "결제 커넥션 풀 격리",
      effect: "긍정 효과: outbox backlog가 주문 처리용 풀로 번지지 않음(bulkhead). 부작용: 결제 전용 풀 자체가 포화되면 그 안에서는 여전히 지연.",
      category: "config",
    },
  ],
  reservation: [
    {
      type: "ENABLE_FINE_GRAINED_LOCKING",
      label: "락 세분화 (좌석 단위)",
      effect: "긍정 효과: 무관한 좌석 간 경합 제거로 유효 처리 용량 증가. 부작용: 락 구현·관리 복잡도 증가.",
      category: "config",
    },
    {
      type: "SHORTEN_HOLD_TIMEOUT",
      label: "홀드 타임아웃 단축",
      effect: "긍정 효과: 결제 미완료로 이탈한 홀드의 자원 점유 시간 감소. 부작용: 정상 사용자가 실제 필요 시간보다 일찍 홀드가 풀릴 위험.",
      category: "config",
    },
    {
      type: "ENABLE_ATOMIC_INVENTORY_CHECK",
      label: "원자적 재고 확인",
      effect: "긍정 효과: 재고 확인·확정 사이 경쟁으로 인한 낭비성 재시도 제거. 부작용: 원자적 처리를 위한 락/트랜잭션 범위 확대.",
      category: "config",
    },
  ],
  "batch-settlement": [
    {
      type: "ENABLE_CHECKPOINT_RESTART",
      label: "체크포인트 재개 활성화",
      effect: "긍정 효과: 실패 시 처음부터가 아니라 실패한 청크부터 재개해 낭비 작업량 대폭 감소. 부작용: 체크포인트 저장·조회 비용 추가.",
      category: "config",
    },
    {
      type: "REDUCE_CHUNK_SIZE",
      label: "청크 크기 축소",
      effect: "긍정 효과: 실패 시 재처리 범위 축소. 부작용: 청크당 커밋 오버헤드 비중 증가로 정상 처리량 감소.",
      category: "config",
    },
    {
      type: "ENABLE_IDEMPOTENT_RECONCILIATION",
      label: "멱등한 정산 재처리",
      effect: "긍정 효과: 재처리된 레코드가 중복 반영되지 않아 정산 정합성 유지. 부작용: 레코드별 처리 이력 저장·조회 비용 추가.",
      category: "config",
    },
  ],
  autoscaling: [
    {
      type: "SCALE_OUT_REPLICAS",
      label: "Pod 증설",
      effect: "긍정 효과: Pod 수 증가로 이론적 처리 용량 확대. 부작용: 리소스 제한·롤아웃 안전장치가 없으면 늘어난 Pod도 똑같이 불안정.",
      category: "scale",
    },
    {
      type: "TUNE_RESOURCE_LIMITS",
      label: "리소스 제한 조정",
      effect: "긍정 효과: 메모리 사용량이 request/limit에 맞게 조정돼 OOM kill로 인한 재시작 반복 해소. 부작용: limit을 너무 낮게 잡으면 정상 부하에서도 스로틀링.",
      category: "config",
    },
    {
      type: "ENABLE_ROLLOUT_SAFEGUARD",
      label: "무중단 배포 안전장치 활성화",
      effect: "긍정 효과: readiness probe/PodDisruptionBudget으로 배포 중에도 가용 용량 유지. 부작용: 배포 자체의 소요 시간 증가.",
      category: "config",
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
  autoscaling: "인시던트 발생: 트래픽 10배 급증 + 롤링 배포 겹침 → Pod OOM kill 재시작 반복, 가용 용량 붕괴",
};

const POLL_INTERVAL_MS = 3000;
const HISTORY_LIMIT = 40;

// PLAN.md step 21/27 — domains with a real-infra opt-in path, and the
// domain-specific description/event text for each one's pre-start gate.
const REAL_INFRA_DOMAINS = new Set(["coupon", "notification"]);

const REAL_INFRA_GATE_DESCRIPTION: Record<string, string> = {
  coupon:
    "실제 Postgres 전용 스키마·커넥션 풀, 실제 네트워크 지연(Toxiproxy 주입), 실제 k6 부하로 지표를 측정합니다.",
  notification:
    "실제 Kafka 토픽·컨슈머 그룹으로 실제 프로듀서/컨슈머를 띄우고, 실제 처리 지연·백로그(consumer lag)·지연 도착 실패율로 지표를 측정합니다.",
};

const REAL_INFRA_START_EVENT: Record<string, string> = {
  coupon: "실전 인프라 인시던트 시작: 실제 Postgres 전용 스키마·커넥션 풀, Toxiproxy로 주입한 실제 네트워크 지연, 실제 k6 부하로 지표를 측정합니다.",
  notification: "실전 인프라 인시던트 시작: 실제 Kafka 토픽·컨슈머 그룹, 실제 프로듀서/컨슈머로 지표를 측정합니다.",
};

/** The same "worst load signal" the backend derives cpuUtilization from (SystemState.kt) — reused here purely to classify a log line's severity, not re-sent anywhere. */
function deriveLevel(state: SystemState): LogLevel {
  const status = utilizationStatus(state.cpuUtilization);
  if (status === "danger") return "ERROR";
  if (status === "warning") return "WARN";
  return "INFO";
}

type HistoryPoint = { t: string; rps: number; errorRate: number };

/** PLAN.md UI/UX 리뉴얼 Round 3 — EventStream/Timeline are merged into
 * one client-side log below (see LogViewer.tsx), seeded once from the
 * backend's `GET .../simulation/timeline` (previously unused) and then
 * appended to on real state-level changes. Actions and the incident event
 * text are keyed by scenario domain (PLAN.md step 11). */
export function WargameLive({
  sessionId,
  domain,
  isOwner = true,
  initialTraits,
}: {
  sessionId: string;
  domain: string;
  /** PLAN.md step 36 — Game Day spectator: hides the incident-start gate and action panel, read-only metrics only. */
  isOwner?: boolean;
  /** ADR-0037 — the Architecture Canvas's node config, forwarded to `startIncident` as this session's starting DesignTraits. */
  initialTraits?: Record<string, number>;
}) {
  const ACTIONS = ACTIONS_BY_DOMAIN[domain] ?? ACTIONS_BY_DOMAIN.coupon;
  const [state, setState] = useState<SystemState | null>(null);
  const [history, setHistory] = useState<HistoryPoint[]>([]);
  const [appliedActions, setAppliedActions] = useState<Set<SimulationActionType>>(new Set());
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [applying, setApplying] = useState<SimulationActionType | null>(null);
  const [notStarted, setNotStarted] = useState(false);
  const started = useRef(false);
  const timelineSeededRef = useRef(false);
  const lastLevelRef = useRef<LogLevel | null>(null);

  // PLAN.md step 21/27 — only domains with a real-infra opt-in (REAL_INFRA_DOMAINS)
  // ever show this pre-start gate; every other domain keeps auto-starting
  // immediately, unchanged. A spectator never sees this gate — only the owner starts the incident.
  const [awaitingStartChoice, setAwaitingStartChoice] = useState(isOwner && REAL_INFRA_DOMAINS.has(domain));
  const [realInfraChoice, setRealInfraChoice] = useState(false);

  const pushLog = useCallback(
    (message: string, forState: SystemState) => {
      setLogs((prev) => [...prev, { time: new Date(), level: deriveLevel(forState), service: domain, message }]);
    },
    [domain],
  );

  const refreshState = useCallback(async () => {
    try {
      const current = await getSimulationState(sessionId);
      setState(current);
      setNotStarted(false);
      const level = deriveLevel(current);
      if (lastLevelRef.current !== null && lastLevelRef.current !== level) {
        pushLog(`지표 상태 변화: ${lastLevelRef.current} → ${level}`, current);
      }
      lastLevelRef.current = level;
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        if (!isOwner) {
          setNotStarted(true);
          return;
        }
        if (!started.current) {
          started.current = true;
          const initial = await startIncident(sessionId, false, initialTraits);
          setState(initial);
          pushLog(INCIDENT_EVENT_BY_DOMAIN[domain] ?? INCIDENT_EVENT_BY_DOMAIN.coupon, initial);
          lastLevelRef.current = deriveLevel(initial);
        }
      }
    }
  }, [sessionId, domain, isOwner, pushLog, initialTraits]);

  useEffect(() => {
    if (awaitingStartChoice) return;
    // Data fetch on mount, not a cascading render loop.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    refreshState();
    const timer = setInterval(refreshState, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [refreshState, awaitingStartChoice]);

  // Feed the RPS/Error-rate charts a rolling window every time a fresh state arrives.
  useEffect(() => {
    if (!state) return;
    setHistory((prev) => [...prev, { t: new Date().toLocaleTimeString(), rps: state.trafficRps, errorRate: state.errorRate * 100 }].slice(-HISTORY_LIMIT));
  }, [state]);

  // Seed the log viewer once from the incident's real timeline (previously unused endpoint) instead of starting blank.
  useEffect(() => {
    if (!state || timelineSeededRef.current) return;
    timelineSeededRef.current = true;
    getSimulationTimeline(sessionId)
      .then((steps) => {
        if (steps.length === 0) return;
        setLogs(
          steps.map((step) => ({
            time: new Date(step.appliedAt),
            level: deriveLevel(step.systemState),
            service: domain,
            message: step.label,
          })),
        );
      })
      .catch(() => {
        // best-effort — the panel still works with only live-appended entries
      });
  }, [state, sessionId, domain]);

  async function handleManualStart() {
    started.current = true;
    setAwaitingStartChoice(false);
    try {
      const initial = await startIncident(sessionId, realInfraChoice, initialTraits);
      setState(initial);
      lastLevelRef.current = deriveLevel(initial);
      pushLog(
        realInfraChoice
          ? (REAL_INFRA_START_EVENT[domain] ?? REAL_INFRA_START_EVENT.coupon)
          : (INCIDENT_EVENT_BY_DOMAIN[domain] ?? INCIDENT_EVENT_BY_DOMAIN.coupon),
        initial,
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
      lastLevelRef.current = deriveLevel(updated);
      setAppliedActions((prev) => new Set(prev).add(actionType));
      pushLog(`조치 적용: ${ACTIONS.find((a) => a.type === actionType)?.label}`, updated);
    } catch {
      setError("조치를 적용하지 못했습니다.");
    } finally {
      setApplying(null);
    }
  }

  if (awaitingStartChoice) {
    return (
      <Card className="flex flex-col gap-4">
        <h2 className="text-sm font-semibold text-foreground-muted">인시던트 시작 방식 선택</h2>
        <label className="flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            checked={realInfraChoice}
            onChange={(e) => setRealInfraChoice(e.target.checked)}
            className="mt-1"
          />
          <span>
            실전 인프라로 시작 (실험적) — {REAL_INFRA_GATE_DESCRIPTION[domain] ?? REAL_INFRA_GATE_DESCRIPTION.coupon}
            체크하지 않으면 기존과 동일한 규칙 기반 시뮬레이션입니다.
          </span>
        </label>
        <Button onClick={handleManualStart} variant="secondary" className="self-start">
          인시던트 시작
        </Button>
      </Card>
    );
  }

  if (notStarted) {
    return (
      <div className="flex flex-col gap-4">
        <p className="text-sm text-foreground-muted">아직 인시던트가 시작되지 않았습니다. 곧 시작되면 여기에 표시됩니다.</p>
        <SessionChat sessionId={sessionId} />
      </div>
    );
  }

  if (!state) {
    return <p className="text-sm text-foreground-muted">시뮬레이션을 시작하는 중...</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      <MetricsPanel state={state} domain={domain} />
      <MetricsHistoryCharts history={history} />
      {isOwner && <LogViewer entries={logs} />}

      <div className="grid gap-4 md:grid-cols-2">
        {isOwner && (
          <Card as="section">
            <h2 className="mb-3 text-sm font-semibold text-foreground-muted">대응 액션</h2>
            <div className="flex flex-col gap-2">
              {ACTIONS.map((action) => (
                <button
                  key={action.type}
                  onClick={() => handleApply(action.type)}
                  disabled={applying !== null || appliedActions.has(action.type)}
                  title={action.effect}
                  className="rounded-lg border border-border px-3 py-2 text-left text-sm disabled:opacity-50"
                >
                  <span className="font-medium">
                    {appliedActions.has(action.type) ? "✓ " : ""}
                    <span aria-hidden>{CATEGORY_META[action.category].icon}</span> {action.label}
                    <Badge variant="neutral" className="ml-2 align-middle">
                      {CATEGORY_META[action.category].label}
                    </Badge>
                  </span>
                  <span className="mt-0.5 block text-xs text-foreground-muted">{action.effect}</span>
                </button>
              ))}
            </div>
          </Card>
        )}

        <SessionChat sessionId={sessionId} />
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}
    </div>
  );
}

const CHAT_POLL_INTERVAL_MS = 3000;

/** PLAN.md step 36 — Game Day's spectate-and-chat channel, shown to both the owner and any spectator once the Wargame view is visible. */
function SessionChat({ sessionId }: { sessionId: string }) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);

  const refresh = useCallback(async () => {
    try {
      setMessages(await listChatMessages(sessionId));
    } catch {
      // transient failure — the next poll tick may succeed
    }
  }, [sessionId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    refresh();
    const timer = setInterval(refresh, CHAT_POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [refresh]);

  async function handleSend(e: React.FormEvent) {
    e.preventDefault();
    if (!draft.trim()) return;
    setSending(true);
    try {
      await postChatMessage(sessionId, draft.trim());
      setDraft("");
      await refresh();
    } catch {
      // leave the draft in place so the user can retry
    } finally {
      setSending(false);
    }
  }

  return (
    <Card as="section">
      <h2 className="mb-3 text-sm font-semibold text-foreground-muted">채팅</h2>
      <ul className="mb-3 flex max-h-48 flex-col gap-1 overflow-y-auto text-sm">
        {messages.length === 0 && <li className="text-xs text-foreground-muted">아직 메시지가 없습니다.</li>}
        {messages.map((m) => (
          <li key={m.id}>
            <span className="font-medium">{m.authorNickname}</span>
            <span className="text-foreground-muted">: </span>
            {m.body}
          </li>
        ))}
      </ul>
      <form onSubmit={handleSend} className="flex gap-2">
        <Input className="flex-1" value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="메시지를 입력하세요" />
        <Button type="submit" disabled={sending} size="sm">
          보내기
        </Button>
      </form>
    </Card>
  );
}

/** Real-time RPS/error-rate line charts fed by WargameLive's rolling history window — not used by the replay screen, which scrubs one snapshot at a time instead of a live-accumulating series. */
function MetricsHistoryCharts({ history }: { history: HistoryPoint[] }) {
  if (history.length < 2) return null;
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Card as="section">
        <h2 className="mb-2 text-sm font-semibold text-foreground-muted">요청 수 (RPS)</h2>
        <ResponsiveContainer width="100%" height={140}>
          <LineChart data={history}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
            <XAxis dataKey="t" hide />
            <YAxis width={40} tick={{ fill: "var(--foreground-muted)", fontSize: 10 }} stroke="var(--border)" />
            <Tooltip contentStyle={{ background: "var(--surface-elevated)", border: "1px solid var(--border)", fontSize: 12 }} />
            <Line type="monotone" dataKey="rps" stroke="#2f80ff" strokeWidth={2} dot={false} />
          </LineChart>
        </ResponsiveContainer>
      </Card>
      <Card as="section">
        <h2 className="mb-2 text-sm font-semibold text-foreground-muted">에러율 (%)</h2>
        <ResponsiveContainer width="100%" height={140}>
          <LineChart data={history}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
            <XAxis dataKey="t" hide />
            <YAxis width={40} tick={{ fill: "var(--foreground-muted)", fontSize: 10 }} stroke="var(--border)" />
            <Tooltip contentStyle={{ background: "var(--surface-elevated)", border: "1px solid var(--border)", fontSize: 12 }} />
            <Line type="monotone" dataKey="errorRate" stroke="#ef4444" strokeWidth={2} dot={false} />
          </LineChart>
        </ResponsiveContainer>
      </Card>
    </div>
  );
}

type Metric = { label: string; value: string; colorFor?: number };

/** Snapshot-only metric tiles + CPU/Memory gauges — used both by the live WargameLive view above and by the replay screen (`replay/page.tsx`), which has no rolling history to chart (it scrubs one recorded step at a time). */
export function MetricsPanel({ state, domain }: { state: SystemState; domain: string }) {
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
    autoscaling: [
      { label: "재시작 중인 Pod 수", value: `${state.queueLag}`, colorFor: state.queueLag > 0 ? 0.9 : 0 },
      { label: "가용 처리 용량", value: `${state.consumerThroughput.toFixed(0)} rps` },
    ],
  };
  const metrics = [...common, ...(domainSpecificByDomain[domain] ?? domainSpecificByDomain.coupon)];

  return (
    <Card as="section">
      <h2 className="mb-3 text-sm font-semibold text-foreground-muted">실시간 지표</h2>
      <div className="flex flex-col items-start gap-6 sm:flex-row">
        <div className="flex shrink-0 gap-4">
          <Gauge label="CPU" value={state.cpuUtilization} status={utilizationStatus(state.cpuUtilization)} />
          <Gauge label="Memory" value={state.memoryUtilization} status={utilizationStatus(state.memoryUtilization)} />
        </div>
        <div className="grid w-full min-w-0 flex-1 grid-cols-2 gap-3 lg:grid-cols-4">
          {metrics.map((metric) => (
            <div key={metric.label}>
              <p className="text-xs text-foreground-muted">{metric.label}</p>
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
      </div>
    </Card>
  );
}
