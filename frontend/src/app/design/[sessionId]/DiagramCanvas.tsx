"use client";

import { useCallback, useMemo, useRef, useState } from "react";
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

type CanvasNodeData = { label: string; kind: NodeKind };
type CanvasFlowNodeData = CanvasNodeData & { onLabelChange: (value: string) => void };

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

/**
 * SysDrill_UIUX_Design_Plan.docx §5.3 — a node-and-edge canvas (Client/API
 * Gateway/Service/DB/Cache/Queue/CDN) as the primary way to build the design
 * diagram. Per ADR-0036, this never becomes a second source of truth: every
 * change re-serializes the graph to a `flowchart TD` Mermaid block and hands
 * it to `onMermaidChange`, which the parent splices into the same free-text
 * answer that already gets submitted — the canvas is an input method, not a
 * new field.
 */
export function DiagramCanvas({
  sessionId,
  onMermaidChange,
}: {
  sessionId: string;
  onMermaidChange: (mermaidText: string) => void;
}) {
  const [nodes, setNodes] = useState<Node<CanvasNodeData>[]>(() => loadInitialGraph(sessionId).nodes);
  const [edges, setEdges] = useState<Edge[]>(() => loadInitialGraph(sessionId).edges);
  const placementCounterRef = useRef(0);

  const commit = useCallback(
    (nextNodes: Node<CanvasNodeData>[], nextEdges: Edge[]) => {
      saveCanvasDraft(sessionId, JSON.stringify({ nodes: nextNodes, edges: nextEdges }));
      onMermaidChange(serializeToMermaid(nextNodes, nextEdges));
    },
    [onMermaidChange, sessionId],
  );

  const nodesWithHandlers = useMemo<Node<CanvasFlowNodeData>[]>(
    () =>
      nodes.map((n) => ({
        ...n,
        data: {
          ...n.data,
          onLabelChange: (value: string) => {
            setNodes((prev) => {
              const next = prev.map((p) => (p.id === n.id ? { ...p, data: { ...p.data, label: value } } : p));
              commit(next, edges);
              return next;
            });
          },
        },
      })),
    [nodes, edges, commit],
  );

  function addNode(kind: NodeKind) {
    const placement = ++placementCounterRef.current;
    const meta = NODE_KIND_META[kind];
    const newNode: Node<CanvasNodeData> = {
      id: typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `node-${placement}`,
      type: "canvasNode",
      position: { x: 40 + ((placement * 60) % 480), y: 40 + ((placement * 90) % 360) },
      data: { label: meta.label, kind },
    };
    const next = [...nodes, newNode];
    setNodes(next);
    commit(next, edges);
  }

  function onNodesChange(changes: NodeChange<Node<CanvasFlowNodeData>>[]) {
    setNodes((prev) => {
      const next = applyNodeChanges(changes, prev) as Node<CanvasNodeData>[];
      // Commit on drop (dragging:false) and non-position changes (add/remove);
      // skip the many intermediate events fired while a drag is in progress —
      // positions aren't part of the Mermaid output anyway.
      const settled = changes.some((c) => c.type !== "position" || c.dragging === false);
      if (settled) commit(next, edges);
      return next;
    });
  }

  function onEdgesChange(changes: EdgeChange[]) {
    setEdges((prev) => {
      const next = applyEdgeChanges(changes, prev);
      commit(nodes, next);
      return next;
    });
  }

  function onConnect(connection: Connection) {
    setEdges((prev) => {
      const next = addEdge(connection, prev);
      commit(nodes, next);
      return next;
    });
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
