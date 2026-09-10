"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Background,
  Controls,
  Handle,
  Position,
  ReactFlow,
  addEdge,
  applyEdgeChanges,
  applyNodeChanges,
  type Connection,
  type Edge,
  type EdgeChange,
  type Node,
  type NodeChange,
  type NodeProps,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { loadCanvasDraft, saveCanvasDraft } from "@/lib/localSession";

type NodeKind = "client" | "gateway" | "service" | "db" | "cache" | "queue" | "cdn";

type CanvasNodeData = { label: string; kind: NodeKind; traitValues?: Record<string, number> };
type CanvasFlowNodeData = CanvasNodeData & {
  onLabelChange: (value: string) => void;
  onTraitChange: (key: string, value: number) => void;
  traitConfig: TraitField[];
};

const NODE_KIND_META: Record<NodeKind, { label: string; color: string; mermaidWrap: (label: string) => string }> = {
  client: { label: "Client", color: "#f59e0b", mermaidWrap: (l) => `([${l}])` },
  gateway: { label: "API Gateway", color: "#a855f7", mermaidWrap: (l) => `[${l}]` },
  service: { label: "Service", color: "#2f80ff", mermaidWrap: (l) => `[${l}]` },
  db: { label: "DB", color: "#34d399", mermaidWrap: (l) => `[(${l})]` },
  cache: { label: "Cache", color: "#ef4444", mermaidWrap: (l) => `[(${l})]` },
  queue: { label: "Queue", color: "#f59e0b", mermaidWrap: (l) => `{{${l}}}` },
  cdn: { label: "CDN", color: "#22d3ee", mermaidWrap: (l) => `[${l}]` },
};

const PALETTE: NodeKind[] = ["client", "gateway", "service", "db", "cache", "queue", "cdn"];

type TraitField = { key: string; label: string; min: number; max: number; step: number; default: number };

/**
 * ADR-0037 — per domain, which node kind exposes which `DesignTraits` (backend)
 * field as a design-time value, and its default (mirroring `DesignTraits.kt`'s
 * Kotlin defaults). Only numeric capacity/config levers are exposed here —
 * boolean toggles (rateLimitEnabled, circuitBreakerEnabled, ...) stay
 * INCIDENT-action-only, since they read as "the response to an incident," not
 * a design-time starting value.
 */
const NODE_TRAIT_CONFIG: Record<string, Partial<Record<NodeKind, TraitField[]>>> = {
  coupon: {
    cache: [{ key: "cacheTtlSeconds", label: "Cache TTL(초)", min: 1, max: 300, step: 1, default: 10 }],
    db: [{ key: "dbPoolSize", label: "DB Pool Size", min: 10, max: 500, step: 10, default: 50 }],
  },
  notification: {
    queue: [{ key: "consumerCount", label: "Consumer 수", min: 1, max: 32, step: 1, default: 4 }],
  },
  "product-browsing": {
    db: [{ key: "readReplicaCount", label: "Read Replica 수", min: 0, max: 5, step: 1, default: 0 }],
  },
  payment: {
    service: [{ key: "dispatcherWorkers", label: "Dispatcher Worker 수", min: 1, max: 32, step: 1, default: 4 }],
  },
  reservation: {
    service: [{ key: "holdTimeoutSeconds", label: "Hold Timeout(초)", min: 30, max: 600, step: 10, default: 300 }],
  },
  "batch-settlement": {
    service: [{ key: "chunkSize", label: "Chunk Size", min: 1000, max: 50000, step: 1000, default: 10000 }],
  },
  autoscaling: {
    service: [{ key: "podReplicas", label: "Pod Replicas", min: 1, max: 20, step: 1, default: 4 }],
  },
};

function mermaidId(index: number): string {
  return `n${index}`;
}

/** Mermaid's shape syntax (`[...]`, `[(...)]`, `{{...}}`) breaks if the label itself contains those brackets, so they're stripped from the free-typed label before wrapping. */
function escapeMermaidLabel(label: string): string {
  return label.replace(/[[\]{}()]/g, "").trim() || "Node";
}

/** Serializes the canvas graph to a `flowchart TD` Mermaid block — the one artifact this component actually produces; node/edge positions themselves are not persisted anywhere beyond this browser tab's `sysdrill:canvas:*` draft. */
export function serializeToMermaid(nodes: Node<CanvasNodeData>[], edges: Edge[]): string {
  const idByNode = new Map(nodes.map((n, i) => [n.id, mermaidId(i)]));
  const lines = ["flowchart TD"];
  for (const node of nodes) {
    const meta = NODE_KIND_META[node.data.kind];
    const id = idByNode.get(node.id)!;
    lines.push(`    ${id}${meta.mermaidWrap(escapeMermaidLabel(node.data.label))}`);
  }
  for (const edge of edges) {
    const source = idByNode.get(edge.source);
    const target = idByNode.get(edge.target);
    if (source && target) lines.push(`    ${source} --> ${target}`);
  }
  return lines.join("\n");
}

function CanvasNode({ data }: NodeProps<Node<CanvasFlowNodeData>>) {
  const meta = NODE_KIND_META[data.kind];
  return (
    <div
      className="rounded-lg border-2 px-3 py-2 text-xs font-medium text-foreground shadow-sm"
      style={{ borderColor: meta.color, background: "var(--surface-elevated)", minWidth: 120 }}
    >
      <Handle type="target" position={Position.Top} />
      <p className="mb-1 text-[10px] uppercase tracking-wide" style={{ color: meta.color }}>
        {meta.label}
      </p>
      <input
        className="nodrag w-full bg-transparent text-sm font-semibold text-foreground outline-none"
        value={data.label}
        onChange={(e) => data.onLabelChange(e.target.value)}
      />
      {data.traitConfig.length > 0 && (
        <div className="nodrag mt-2 flex flex-col gap-1 border-t border-border pt-2">
          {data.traitConfig.map((field) => (
            <label key={field.key} className="flex items-center justify-between gap-2 text-[10px] text-foreground-muted">
              <span>{field.label}</span>
              <input
                type="number"
                min={field.min}
                max={field.max}
                step={field.step}
                className="w-16 rounded border border-border bg-transparent px-1 py-0.5 text-right text-foreground outline-none"
                value={data.traitValues?.[field.key] ?? field.default}
                onChange={(e) => data.onTraitChange(field.key, Number(e.target.value))}
              />
            </label>
          ))}
        </div>
      )}
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

const NODE_TYPES = { canvasNode: CanvasNode };

function loadInitialGraph(sessionId: string): { nodes: Node<CanvasNodeData>[]; edges: Edge[] } {
  const raw = loadCanvasDraft(sessionId);
  if (!raw) return { nodes: [], edges: [] };
  try {
    const parsed = JSON.parse(raw) as { nodes: Node<CanvasNodeData>[]; edges: Edge[] };
    return { nodes: parsed.nodes ?? [], edges: parsed.edges ?? [] };
  } catch {
    return { nodes: [], edges: [] };
  }
}

/** Flattens every node's `traitValues` into one object (last node of a given kind wins), for `onTraitsChange`/`startIncident`. */
function collectTraits(nodes: Node<CanvasNodeData>[]): Record<string, number> {
  const result: Record<string, number> = {};
  for (const node of nodes) {
    if (node.data.traitValues) Object.assign(result, node.data.traitValues);
  }
  return result;
}

/**
 * SysDrill_UIUX_Design_Plan.docx §5.3 — a node-and-edge canvas (Client/API
 * Gateway/Service/DB/Cache/Queue/CDN) as the primary way to build the design
 * diagram. Per ADR-0036, diagram *shape* never becomes a second source of
 * truth: every change re-serializes the graph to a `flowchart TD` Mermaid
 * block and hands it to `onMermaidChange`, which the parent splices into the
 * same free-text answer that already gets submitted.
 *
 * Per ADR-0037, node *config* (e.g. a DB node's pool size) is different: it
 * becomes this session's starting `DesignTraits` via `onTraitsChange`, sent
 * to `startIncident` when the incident starts. Which fields a node exposes
 * depends on the session's `domain` (`NODE_TRAIT_CONFIG`) — most node kinds
 * expose none and render unchanged from before.
 */
export function DiagramCanvas({
  sessionId,
  domain,
  onMermaidChange,
  onTraitsChange,
}: {
  sessionId: string;
  domain: string;
  onMermaidChange: (mermaidText: string) => void;
  onTraitsChange?: (traits: Record<string, number>) => void;
}) {
  const [nodes, setNodes] = useState<Node<CanvasNodeData>[]>(() => loadInitialGraph(sessionId).nodes);
  const [edges, setEdges] = useState<Edge[]>(() => loadInitialGraph(sessionId).edges);
  const placementCounterRef = useRef(0);
  const traitConfigForDomain = useMemo(() => NODE_TRAIT_CONFIG[domain] ?? {}, [domain]);

  const commit = useCallback(
    (nextNodes: Node<CanvasNodeData>[], nextEdges: Edge[]) => {
      saveCanvasDraft(sessionId, JSON.stringify({ nodes: nextNodes, edges: nextEdges }));
      onMermaidChange(serializeToMermaid(nextNodes, nextEdges));
      onTraitsChange?.(collectTraits(nextNodes));
    },
    [onMermaidChange, onTraitsChange, sessionId],
  );

  // React Flow can invoke onNodesChange synchronously during its own
  // render/measurement pass (observed for the "dimensions" auto-change it
  // fires the first time a newly added node is measured) — calling `commit`
  // (which cascades into the parent's setAnswer) from inside a setState
  // updater is unsafe in exactly that case ("Cannot update a component
  // while rendering a different component"). So state updates below stay
  // pure, and this effect is the only place `commit` runs, after render.
  // `skipCommitRef` preserves the original "don't commit every intermediate
  // drag-position tick" behavior without putting a side effect in the
  // updater; `isMountRef` preserves "don't commit on initial mount" (a
  // restored draft shouldn't immediately re-splice into the answer).
  //
  // `commit` itself is read through a ref rather than listed as an effect
  // dependency: `onMermaidChange`/`onTraitsChange` are plain (non-memoized)
  // functions in the parent, recreated every parent render — including the
  // render `commit`'s own setAnswer call causes. Depending on `commit`
  // directly re-fired this effect on every one of those renders, an
  // infinite loop (confirmed live: "Maximum update depth exceeded"). Only
  // `nodes`/`edges` actually changing should trigger a commit.
  const isMountRef = useRef(true);
  const skipCommitRef = useRef(false);
  const commitRef = useRef(commit);
  useEffect(() => {
    commitRef.current = commit;
  });
  useEffect(() => {
    if (isMountRef.current) {
      isMountRef.current = false;
      return;
    }
    if (skipCommitRef.current) {
      skipCommitRef.current = false;
      return;
    }
    commitRef.current(nodes, edges);
  }, [nodes, edges]);

  const nodesWithHandlers = useMemo<Node<CanvasFlowNodeData>[]>(
    () =>
      nodes.map((n) => ({
        ...n,
        data: {
          ...n.data,
          traitConfig: traitConfigForDomain[n.data.kind] ?? [],
          onLabelChange: (value: string) => {
            setNodes((prev) => prev.map((p) => (p.id === n.id ? { ...p, data: { ...p.data, label: value } } : p)));
          },
          onTraitChange: (key: string, value: number) => {
            setNodes((prev) =>
              prev.map((p) =>
                p.id === n.id ? { ...p, data: { ...p.data, traitValues: { ...p.data.traitValues, [key]: value } } } : p,
              ),
            );
          },
        },
      })),
    [nodes, traitConfigForDomain],
  );

  function addNode(kind: NodeKind) {
    const placement = ++placementCounterRef.current;
    const meta = NODE_KIND_META[kind];
    const fields = traitConfigForDomain[kind] ?? [];
    const traitValues = Object.fromEntries(fields.map((f) => [f.key, f.default]));
    const newNode: Node<CanvasNodeData> = {
      id: typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `node-${placement}`,
      type: "canvasNode",
      position: { x: 40 + ((placement * 60) % 480), y: 40 + ((placement * 90) % 360) },
      data: { label: meta.label, kind, traitValues },
    };
    setNodes((prev) => [...prev, newNode]);
  }

  function onNodesChange(changes: NodeChange<Node<CanvasFlowNodeData>>[]) {
    // Skip committing on the many intermediate events fired while a drag is
    // in progress — positions aren't part of the Mermaid/traits output
    // anyway. Only the drop (dragging:false) or a non-position change
    // (add/remove/dimensions) triggers a commit.
    const settled = changes.some((c) => c.type !== "position" || c.dragging === false);
    skipCommitRef.current = !settled;
    setNodes((prev) => applyNodeChanges(changes, prev) as Node<CanvasNodeData>[]);
  }

  function onEdgesChange(changes: EdgeChange[]) {
    setEdges((prev) => applyEdgeChanges(changes, prev));
  }

  function onConnect(connection: Connection) {
    setEdges((prev) => addEdge(connection, prev));
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-1.5">
        {PALETTE.map((kind) => (
          <button
            key={kind}
            type="button"
            onClick={() => addNode(kind)}
            className="rounded-lg border px-2 py-1 text-xs text-foreground-muted hover:text-foreground"
            style={{ borderColor: `${NODE_KIND_META[kind].color}55` }}
          >
            + {NODE_KIND_META[kind].label}
          </button>
        ))}
      </div>
      <div className="h-[360px] overflow-hidden rounded-lg border border-border bg-surface" style={{ colorScheme: "dark" }}>
        <ReactFlow
          nodes={nodesWithHandlers}
          edges={edges}
          nodeTypes={NODE_TYPES}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          deleteKeyCode={["Backspace", "Delete"]}
          fitView
          proOptions={{ hideAttribution: true }}
        >
          <Background color="var(--border)" gap={16} />
          <Controls showInteractive={false} />
        </ReactFlow>
      </div>
      {nodes.length === 0 && (
        <p className="text-xs text-foreground-muted">
          위 팔레트에서 노드를 추가하고, 노드 아래쪽 점을 드래그해 다른 노드에 연결하세요. 선택 후 Delete로 삭제합니다.
        </p>
      )}
    </div>
  );
}
