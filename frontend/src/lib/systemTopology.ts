import type { SystemState } from "@/lib/api";
import { formatMs, formatPercent, utilizationStatus } from "@/lib/metrics";

/**
 * docs/LEARNING_DEEPENING_PLAN.md L12 — each simulation domain drawn as the handful of components
 * its rule-engine formula actually models, coloured from that formula's SystemState. A component
 * only gets a status from a metric the domain populates (RuleBasedSimulationEngine — the others are
 * constant 0 there); the rest stay neutral. Labels follow the design canvas's vocabulary.
 */

export type Health = "success" | "warning" | "danger" | "neutral";

export interface TopologyNode {
  id: string;
  label: string;
  /** Grid position: column (left → right in request order) and row. */
  col: number;
  row: number;
  status?: (s: SystemState) => { health: Health; detail: string };
}

export interface Topology {
  nodes: TopologyNode[];
  edges: { source: string; target: string; label?: string }[];
}

const util = (value: number, label: string) => ({ health: utilizationStatus(value) as Health, detail: `${label} ${formatPercent(value)}` });

/** Worse of two statuses — a component is as unhealthy as its worst signal. */
function worst(...items: { health: Health; detail: string }[]) {
  const rank: Record<Health, number> = { neutral: 0, success: 1, warning: 2, danger: 3 };
  const top = items.reduce((a, b) => (rank[b.health] > rank[a.health] ? b : a));
  return { health: top.health, detail: items.map((i) => i.detail).join(" · ") };
}

function latency(ms: number, label = "P95") {
  const health: Health = ms < 300 ? "success" : ms < 1000 ? "warning" : "danger";
  return { health, detail: `${label} ${formatMs(ms)}` };
}

function errors(rate: number) {
  const health: Health = rate < 0.01 ? "success" : rate < 0.05 ? "warning" : "danger";
  return { health, detail: `에러 ${formatPercent(rate)}` };
}

function hitRatio(ratio: number) {
  const health: Health = ratio >= 0.8 ? "success" : ratio >= 0.5 ? "warning" : "danger";
  return { health, detail: `hit ${formatPercent(ratio)}` };
}

function backlog(count: number, label: string, warnAt = 1, dangerAt = 1000) {
  const health: Health = count < warnAt ? "success" : count < dangerAt ? "warning" : "danger";
  return { health, detail: `${label} ${Math.round(count).toLocaleString()}` };
}

const traffic = (s: SystemState) => ({ health: "neutral" as Health, detail: `${s.trafficRps.toFixed(0)} req/s` });
const service = (s: SystemState) => worst(latency(s.p95LatencyMs), errors(s.errorRate));

export const TOPOLOGIES: Record<string, Topology> = {
  coupon: {
    nodes: [
      { id: "client", label: "사용자", col: 0, row: 1, status: traffic },
      { id: "api", label: "API 서버", col: 1, row: 1, status: service },
      { id: "cache", label: "Redis 캐시", col: 2, row: 0, status: (s) => worst(hitRatio(s.cacheHitRatio), latency(s.cacheLatencyMs, "지연")) },
      { id: "db", label: "DB (쿠폰 재고)", col: 2, row: 2, status: (s) => worst(util(s.dbWriteLoad, "쓰기"), util(s.connectionPoolUsage, "풀")) },
    ],
    edges: [
      { source: "client", target: "api" },
      { source: "api", target: "cache", label: "재고 조회" },
      { source: "api", target: "db", label: "발급(쓰기)" },
    ],
  },
  notification: {
    nodes: [
      { id: "producer", label: "이벤트 발행", col: 0, row: 0, status: traffic },
      { id: "queue", label: "메시지 큐", col: 1, row: 0, status: (s) => backlog(s.queueLag, "적체") },
      { id: "consumer", label: "컨슈머", col: 2, row: 0, status: (s) => worst(errors(s.errorRate), { health: "neutral", detail: `처리 ${s.consumerThroughput.toFixed(0)}/s` }) },
      { id: "provider", label: "외부 provider", col: 3, row: 0, status: (s) => latency(s.externalDependencyLatencyMs, "응답") },
    ],
    edges: [
      { source: "producer", target: "queue" },
      { source: "queue", target: "consumer" },
      { source: "consumer", target: "provider", label: "발송" },
    ],
  },
  "product-browsing": {
    nodes: [
      { id: "client", label: "사용자", col: 0, row: 1, status: traffic },
      { id: "api", label: "상품 API", col: 1, row: 1, status: service },
      { id: "cache", label: "캐시", col: 2, row: 0, status: (s) => hitRatio(s.cacheHitRatio) },
      { id: "db", label: "DB (primary + replica)", col: 2, row: 2, status: (s) => util(s.dbReadLoad, "읽기") },
    ],
    edges: [
      { source: "client", target: "api" },
      { source: "api", target: "cache", label: "조회" },
      { source: "api", target: "db", label: "miss 시 읽기" },
    ],
  },
  payment: {
    nodes: [
      { id: "client", label: "사용자", col: 0, row: 0, status: traffic },
      { id: "api", label: "주문 API", col: 1, row: 0, status: service },
      { id: "db", label: "주문 DB · 커넥션 풀", col: 2, row: 0, status: (s) => util(s.connectionPoolUsage, "풀") },
      { id: "outbox", label: "Outbox", col: 2, row: 1, status: (s) => backlog(s.queueLag, "적체") },
      { id: "dispatcher", label: "Dispatcher", col: 3, row: 1, status: (s) => ({ health: "neutral", detail: `처리 ${s.consumerThroughput.toFixed(0)}/s` }) },
      { id: "pg", label: "PG (결제대행)", col: 4, row: 1, status: (s) => latency(s.externalDependencyLatencyMs, "응답") },
    ],
    edges: [
      { source: "client", target: "api" },
      { source: "api", target: "db", label: "주문 저장" },
      { source: "db", target: "outbox", label: "같은 트랜잭션" },
      { source: "outbox", target: "dispatcher" },
      { source: "dispatcher", target: "pg", label: "결제 요청" },
    ],
  },
  reservation: {
    nodes: [
      { id: "client", label: "사용자", col: 0, row: 0, status: traffic },
      { id: "api", label: "예약 API", col: 1, row: 0, status: (s) => worst(service(s), backlog(s.queueLag, "락 대기")) },
      { id: "db", label: "예약 DB (락)", col: 2, row: 0, status: (s) => util(s.dbWriteLoad, "쓰기") },
    ],
    edges: [
      { source: "client", target: "api" },
      { source: "api", target: "db", label: "홀드 · 확정" },
    ],
  },
  "batch-settlement": {
    nodes: [
      { id: "scheduler", label: "스케줄러", col: 0, row: 0 },
      { id: "worker", label: "배치 워커", col: 1, row: 0, status: (s) => worst(errors(s.errorRate), backlog(s.queueLag, "재처리 레코드")) },
      { id: "db", label: "정산 DB", col: 2, row: 0, status: (s) => util(s.dbWriteLoad, "복구 오버헤드") },
      { id: "external", label: "외부 정산 API", col: 2, row: 1, status: (s) => latency(s.externalDependencyLatencyMs, "응답") },
    ],
    edges: [
      { source: "scheduler", target: "worker", label: "청크 단위" },
      { source: "worker", target: "db", label: "반영" },
      { source: "worker", target: "external", label: "조회" },
    ],
  },
  autoscaling: {
    nodes: [
      { id: "client", label: "사용자", col: 0, row: 0, status: traffic },
      { id: "lb", label: "로드밸런서", col: 1, row: 0 },
      { id: "pods", label: "Pod (HPA)", col: 2, row: 0, status: (s) => worst(service(s), backlog(s.queueLag, "재시작 중 Pod", 1, 2)) },
    ],
    edges: [
      { source: "client", target: "lb" },
      { source: "lb", target: "pods" },
    ],
  },
  deployment: {
    nodes: [
      { id: "client", label: "사용자", col: 0, row: 1, status: traffic },
      { id: "gateway", label: "게이트웨이", col: 1, row: 1, status: service },
      { id: "stable", label: "서비스 (안정 버전)", col: 2, row: 0 },
      { id: "canary", label: "서비스 (카나리)", col: 2, row: 2, status: (s) => errors(s.errorRate) },
      { id: "db", label: "DB", col: 3, row: 1, status: (s) => util(s.connectionPoolUsage, "풀") },
    ],
    edges: [
      { source: "client", target: "gateway" },
      { source: "gateway", target: "stable" },
      { source: "gateway", target: "canary", label: "일부 트래픽" },
      { source: "stable", target: "db" },
      { source: "canary", target: "db" },
    ],
  },
};
