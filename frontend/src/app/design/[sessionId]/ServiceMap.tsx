"use client";

import { useEffect, useMemo, useState } from "react";
import { Background, Handle, Position, ReactFlow, type Edge, type Node, type NodeProps } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { type SeriesPoint, type SystemState, getSystemTopology } from "@/lib/api";
import { formatMs, formatPercent, utilizationColorClass } from "@/lib/metrics";
import { Card } from "@/components/ui/Card";

/**
 * docs/OBSERVABILITY_UI_PLAN.md O3 — the learner's own design-time canvas, read-only,
 * with each node showing the metrics its kind would expose in production (RED for
 * request-serving nodes, USE for resources).
 *
 * Honest limits, stated in the UI too: the engine computes one system-wide snapshot,
 * not per-node state, so two nodes of the same kind show the same numbers; and a field
 * the scenario's domain doesn't model shows "—" rather than a fake 0.
 *
 * Colour judges each node by **its own** metric through the same bands everywhere —
 * a service isn't painted red because the DB behind it is saturated. Nothing marks a cause.
 */

type MapKind = "client" | "gateway" | "service" | "db" | "cache" | "queue" | "cdn" | "external";

type Field =
  | "traffic" | "p95" | "errors" | "dbRead" | "dbWrite" | "pool" | "cacheHit" | "cacheLatency"
  | "backlog" | "throughput" | "external";

/** Which SystemState fields each domain's formula actually produces (RuleBasedSimulationEngine). */
const MODELED: Record<string, Field[]> = {
  coupon: ["traffic", "p95", "errors", "dbRead", "dbWrite", "pool", "cacheHit", "cacheLatency"],
  notification: ["traffic", "p95", "errors", "backlog", "throughput", "external"],
  "product-browsing": ["traffic", "p95", "errors", "dbRead", "cacheHit"],
  payment: ["traffic", "p95", "errors", "pool", "backlog", "throughput", "external"],
  reservation: ["traffic", "p95", "errors", "dbWrite", "backlog", "throughput"],
  "batch-settlement": ["traffic", "p95", "errors", "dbWrite", "backlog", "throughput", "external"],
  autoscaling: ["traffic", "p95", "errors", "backlog", "throughput"],
};

const FIELD_META: Record<Field, { label: string; value: (s: SystemState, backlog: number) => string }> = {
  traffic: { label: "Rate", value: (s) => `${s.trafficRps.toFixed(0)}/s` },
  errors: { label: "Errors", value: (s) => formatPercent(s.errorRate) },
  p95: { label: "Duration (P95)", value: (s) => formatMs(s.p95LatencyMs) },
  dbRead: { label: "읽기 사용률", value: (s) => formatPercent(s.dbReadLoad) },
  dbWrite: { label: "쓰기 사용률", value: (s) => formatPercent(s.dbWriteLoad) },
  pool: { label: "커넥션 풀", value: (s) => formatPercent(s.connectionPoolUsage) },
  cacheHit: { label: "Hit ratio", value: (s) => formatPercent(s.cacheHitRatio) },
  cacheLatency: { label: "지연", value: (s) => formatMs(s.cacheLatencyMs) },
  backlog: { label: "적체", value: (s, b) => (b > 0 ? b : s.queueLag).toLocaleString() },
  throughput: { label: "처리량", value: (s) => `${s.consumerThroughput.toFixed(1)}/s` },
  external: { label: "응답 지연", value: (s) => formatMs(s.externalDependencyLatencyMs) },
};

const KIND_META: Record<MapKind, { label: string; method: "RED" | "USE" | null; fields: Field[]; color: string }> = {
  client: { label: "Client", method: null, fields: ["traffic"], color: "#f59e0b" },
  cdn: { label: "CDN", method: null, fields: ["traffic"], color: "#22d3ee" },
  gateway: { label: "Gateway", method: "RED", fields: ["traffic", "errors", "p95"], color: "#a855f7" },
  service: { label: "Service", method: "RED", fields: ["traffic", "errors", "p95"], color: "#2f80ff" },
  db: { label: "DB", method: "USE", fields: ["dbRead", "dbWrite", "pool"], color: "#34d399" },
  cache: { label: "Cache", method: "USE", fields: ["cacheHit", "cacheLatency"], color: "#ef4444" },
  queue: { label: "Queue", method: "USE", fields: ["backlog", "throughput"], color: "#f59e0b" },
  external: { label: "External", method: "RED", fields: ["external"], color: "#94a3b8" },
};

/** Each node's own signal → 0..1, coloured by the shared utilization bands. */
function severityOf(kind: MapKind, s: SystemState, backlog: number, modeled: Field[]): number {
  switch (kind) {
    case "gateway":
    case "service":
      return s.errorRate < 0.005 ? 0 : s.errorRate < 0.05 ? 0.7 : 1;
    case "db":
      return Math.max(
        modeled.includes("dbRead") ? s.dbReadLoad : 0,
        modeled.includes("dbWrite") ? s.dbWriteLoad : 0,
        modeled.includes("pool") ? s.connectionPoolUsage : 0,
      );
    case "cache":
      return modeled.includes("cacheHit") ? 1 - s.cacheHitRatio : 0;
    case "queue":
      return modeled.includes("backlog") ? Math.min(1, Math.max(backlog, s.queueLag) / 100) : 0;
    case "external":
      return modeled.includes("external") ? Math.min(1, s.externalDependencyLatencyMs / 300) : 0;
    default:
      return 0;
  }
}

type MapNodeData = { label: string; kind: MapKind; lines: [string, string][]; severity: number; measured: boolean };

function MapNode({ data, selected }: NodeProps<Node<MapNodeData>>) {
  const meta = KIND_META[data.kind];
  return (
    <div
      className={`min-w-[150px] rounded-lg border bg-surface px-3 py-2 text-left text-xs shadow ${selected ? "border-accent" : "border-border"}`}
      style={{ borderTopColor: meta.color, borderTopWidth: 3 }}
    >
      <Handle type="target" position={Position.Left} className="!h-1 !w-1 !border-0 !bg-border" />
      <div className="flex items-center justify-between gap-2">
        <span className="font-semibold text-foreground">{data.label}</span>
        {data.measured && <span className={`text-base leading-none ${utilizationColorClass(data.severity)}`}>●</span>}
      </div>
      <p className="text-[10px] text-foreground-muted">{meta.label}{meta.method ? ` · ${meta.method}` : ""}</p>
      {data.measured ? (
        <dl className="mt-1 space-y-0.5">
          {data.lines.map(([label, value]) => (
            <div key={label} className="flex justify-between gap-3">
              <dt className="text-foreground-muted">{label}</dt>
              <dd className="font-mono text-foreground">{value}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p className="mt-1 text-[10px] text-foreground-muted">이 시나리오에서 측정되지 않음</p>
      )}
      <Handle type="source" position={Position.Right} className="!h-1 !w-1 !border-0 !bg-border" />
    </div>
  );
}

const NODE_TYPES = { svc: MapNode };

type Spec = { nodes: { id: string; label: string; kind: MapKind; x: number; y: number }[]; edges: [string, string][] };

/** Used when the learner never drew a canvas — a plain picture of the scenario's system (display only). */
const DEFAULT_TOPOLOGY: Record<string, Spec> = {
  coupon: {
    nodes: [
      { id: "c", label: "사용자", kind: "client", x: 0, y: 60 },
      { id: "g", label: "API Gateway", kind: "gateway", x: 200, y: 60 },
      { id: "s", label: "쿠폰 API", kind: "service", x: 400, y: 60 },
      { id: "r", label: "Redis", kind: "cache", x: 620, y: 0 },
      { id: "d", label: "쿠폰 DB", kind: "db", x: 620, y: 140 },
    ],
    edges: [["c", "g"], ["g", "s"], ["s", "r"], ["s", "d"]],
  },
  notification: {
    nodes: [
      { id: "o", label: "주문 서비스", kind: "service", x: 0, y: 60 },
      { id: "q", label: "이벤트 큐", kind: "queue", x: 210, y: 60 },
      { id: "w", label: "알림 컨슈머", kind: "service", x: 420, y: 60 },
      { id: "p", label: "Provider", kind: "external", x: 640, y: 60 },
    ],
    edges: [["o", "q"], ["q", "w"], ["w", "p"]],
  },
  "product-browsing": {
    nodes: [
      { id: "c", label: "사용자", kind: "client", x: 0, y: 60 },
      { id: "s", label: "상품 조회 API", kind: "service", x: 210, y: 60 },
      { id: "r", label: "캐시", kind: "cache", x: 430, y: 0 },
      { id: "d", label: "상품 DB", kind: "db", x: 430, y: 140 },
    ],
    edges: [["c", "s"], ["s", "r"], ["s", "d"]],
  },
  payment: {
    nodes: [
      { id: "c", label: "사용자", kind: "client", x: 0, y: 0 },
      { id: "s", label: "주문 API", kind: "service", x: 200, y: 0 },
      { id: "d", label: "주문 DB", kind: "db", x: 420, y: 0 },
      { id: "q", label: "Outbox", kind: "queue", x: 200, y: 150 },
      { id: "w", label: "디스패처", kind: "service", x: 420, y: 150 },
      { id: "p", label: "PG", kind: "external", x: 640, y: 150 },
    ],
    edges: [["c", "s"], ["s", "d"], ["s", "q"], ["q", "w"], ["w", "p"]],
  },
  reservation: {
    nodes: [
      { id: "c", label: "사용자", kind: "client", x: 0, y: 60 },
      { id: "s", label: "예약 API", kind: "service", x: 210, y: 60 },
      { id: "q", label: "락 대기열", kind: "queue", x: 430, y: 0 },
      { id: "d", label: "좌석 DB", kind: "db", x: 430, y: 140 },
    ],
    edges: [["c", "s"], ["s", "q"], ["s", "d"]],
  },
  "batch-settlement": {
    nodes: [
      { id: "b", label: "정산 배치", kind: "service", x: 0, y: 60 },
      { id: "q", label: "재처리 대상", kind: "queue", x: 210, y: 0 },
      { id: "d", label: "정산 DB", kind: "db", x: 210, y: 140 },
      { id: "p", label: "정산 API", kind: "external", x: 430, y: 60 },
    ],
    edges: [["b", "q"], ["b", "d"], ["b", "p"]],
  },
  autoscaling: {
    nodes: [
      { id: "c", label: "사용자", kind: "client", x: 0, y: 60 },
      { id: "g", label: "Ingress", kind: "gateway", x: 200, y: 60 },
      { id: "s", label: "추천 API (Pods)", kind: "service", x: 400, y: 60 },
      { id: "q", label: "재시작 중 Pod", kind: "queue", x: 620, y: 60 },
    ],
    edges: [["c", "g"], ["g", "s"], ["s", "q"]],
  },
};

type SavedNode = { id: string; position?: { x: number; y: number }; data?: { label?: string; kind?: MapKind } };

function parseSaved(graph: string): Spec | null {
  try {
    const parsed = JSON.parse(graph) as { nodes?: SavedNode[]; edges?: { source: string; target: string }[] };
    const nodes = (parsed.nodes ?? [])
      .filter((n) => n.data?.kind && n.data.kind in KIND_META)
      .map((n) => ({
        id: n.id,
        label: n.data?.label || KIND_META[n.data!.kind!].label,
        kind: n.data!.kind!,
        x: n.position?.x ?? 0,
        y: n.position?.y ?? 0,
      }));
    if (nodes.length === 0) return null;
    return { nodes, edges: (parsed.edges ?? []).map((e) => [e.source, e.target] as [string, string]) };
  } catch {
    return null;
  }
}

export function ServiceMap({
  sessionId,
  domain,
  latest,
  onInspect,
}: {
  sessionId: string;
  domain: string;
  latest: SeriesPoint | null;
  /** O0-b — called when the learner opens a node's detail (recorded as an investigation). */
  onInspect?: (nodeLabel: string) => void;
}) {
  const [spec, setSpec] = useState<{ spec: Spec; drawn: boolean } | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const fallback = DEFAULT_TOPOLOGY[domain] ?? DEFAULT_TOPOLOGY.coupon;

  useEffect(() => {
    let cancelled = false;
    getSystemTopology(sessionId)
      .then((t) => {
        const saved = t.saved ? parseSaved(t.graph) : null;
        if (!cancelled) setSpec(saved ? { spec: saved, drawn: true } : { spec: fallback, drawn: false });
      })
      .catch(() => !cancelled && setSpec({ spec: fallback, drawn: false }));
    return () => {
      cancelled = true;
    };
  }, [sessionId, fallback]);

  const modeled = useMemo(() => MODELED[domain] ?? MODELED.coupon, [domain]);

  const { nodes, edges } = useMemo(() => {
    if (!spec || !latest) return { nodes: [] as Node<MapNodeData>[], edges: [] as Edge[] };
    const flowNodes: Node<MapNodeData>[] = spec.spec.nodes.map((n) => {
      const fields = KIND_META[n.kind].fields.filter((f) => modeled.includes(f));
      return {
        id: n.id,
        type: "svc",
        position: { x: n.x, y: n.y },
        data: {
          label: n.label,
          kind: n.kind,
          lines: fields.map((f) => [FIELD_META[f].label, FIELD_META[f].value(latest.state, latest.backlog)]),
          severity: severityOf(n.kind, latest.state, latest.backlog, modeled),
          measured: fields.length > 0,
        },
        selected: n.id === selectedId,
      };
    });
    const flowEdges: Edge[] = spec.spec.edges.map(([source, target]) => ({
      id: `${source}-${target}`,
      source,
      target,
      animated: true,
      style: { stroke: "var(--border)" },
    }));
    return { nodes: flowNodes, edges: flowEdges };
  }, [spec, latest, modeled, selectedId]);

  if (!spec || !latest) return null;
  const selected = nodes.find((n) => n.id === selectedId);

  return (
    <div className="flex flex-col gap-3">
      <Card as="section" className="min-w-0 !p-0">
        <div className="h-[340px] w-full">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={NODE_TYPES}
            nodesDraggable={false}
            nodesConnectable={false}
            elementsSelectable
            fitView
            proOptions={{ hideAttribution: true }}
            onNodeClick={(_, node) => {
              setSelectedId(node.id);
              onInspect?.((node.data as MapNodeData).label);
            }}
          >
            <Background color="var(--border)" gap={20} />
          </ReactFlow>
        </div>
      </Card>
      <p className="text-[11px] text-foreground-muted">
        {spec.drawn ? "설계 단계에서 그린 캔버스입니다." : "캔버스를 그리지 않은 세션이라 시나리오 기본 구성을 보여줍니다."} 엔진은 노드별이 아니라 시스템 전체
        스냅샷 하나를 계산하므로 같은 종류의 노드는 같은 값을 보입니다. 색은 각 노드 자신의 지표만으로 정합니다.
      </p>
      {selected && (
        <Card as="section" className="min-w-0">
          <p className="text-sm font-semibold">
            {selected.data.label}{" "}
            <span className="text-xs font-normal text-foreground-muted">
              {KIND_META[selected.data.kind].label}
              {KIND_META[selected.data.kind].method ? ` · ${KIND_META[selected.data.kind].method}` : ""}
            </span>
          </p>
          {selected.data.measured ? (
            <dl className="mt-2 grid grid-cols-2 gap-x-6 gap-y-1 text-sm sm:grid-cols-3">
              {selected.data.lines.map(([label, value]) => (
                <div key={label}>
                  <dt className="text-xs text-foreground-muted">{label}</dt>
                  <dd className="font-mono">{value}</dd>
                </div>
              ))}
            </dl>
          ) : (
            <p className="mt-2 text-sm text-foreground-muted">이 시나리오의 시뮬레이션은 이 종류의 노드에 대한 지표를 계산하지 않습니다.</p>
          )}
        </Card>
      )}
    </div>
  );
}
