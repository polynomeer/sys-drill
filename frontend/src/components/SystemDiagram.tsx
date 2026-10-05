"use client";

import { useMemo } from "react";
import { Background, Handle, MarkerType, Position, ReactFlow, type Edge, type Node, type NodeProps } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import type { SystemState } from "@/lib/api";
import { type Health, TOPOLOGIES } from "@/lib/systemTopology";

type ComponentData = { label: string; health: Health; detail: string };

const HEALTH_CLASS: Record<Health, string> = {
  success: "border-success",
  warning: "border-warning",
  danger: "border-danger bg-danger/10",
  neutral: "border-border",
};

const HEALTH_LABEL: Record<Health, string> = { success: "정상", warning: "주의", danger: "포화·장애", neutral: "" };

function ComponentNode({ data }: NodeProps<Node<ComponentData>>) {
  return (
    <div className={`w-[150px] rounded-lg border-2 bg-surface px-2 py-1.5 text-xs ${HEALTH_CLASS[data.health]}`}>
      <Handle type="target" position={Position.Left} className="!opacity-0" />
      <p className="font-medium text-foreground">{data.label}</p>
      {data.detail && <p className="mt-0.5 text-[11px] leading-snug text-foreground-muted">{data.detail}</p>}
      <Handle type="source" position={Position.Right} className="!opacity-0" />
    </div>
  );
}

const NODE_TYPES = { component: ComponentNode };
const COL_WIDTH = 200;
const ROW_HEIGHT = 84;

/**
 * docs/LEARNING_DEEPENING_PLAN.md L12 — a domain's system coloured by the engine's state: the
 * bottleneck shows up red, and moving a knob (labs, L15) moves the red. Read-only — no panning
 * or zooming to fight with the page scroll. The text below the picture says the same thing for
 * screen readers.
 */
export function SystemDiagram({ domain, state, caption }: { domain: string; state: SystemState; caption?: string }) {
  const topology = TOPOLOGIES[domain];

  const { nodes, edges, statuses } = useMemo(() => {
    if (!topology) return { nodes: [] as Node[], edges: [] as Edge[], statuses: [] as (ComponentData & { id: string })[] };
    const statuses = topology.nodes.map((n) => {
      const s = n.status?.(state) ?? { health: "neutral" as Health, detail: "" };
      return { id: n.id, label: n.label, health: s.health, detail: s.detail };
    });
    const byId = new Map(statuses.map((s) => [s.id, s]));
    const nodes: Node<ComponentData>[] = topology.nodes.map((n) => ({
      id: n.id,
      type: "component",
      position: { x: n.col * COL_WIDTH, y: n.row * ROW_HEIGHT },
      data: byId.get(n.id)!,
      draggable: false,
      selectable: false,
      focusable: false,
    }));
    const edges: Edge[] = topology.edges.map((e) => {
      const target = byId.get(e.target);
      const hot = target?.health === "danger";
      return {
        id: `${e.source}-${e.target}`,
        source: e.source,
        target: e.target,
        label: e.label,
        animated: hot,
        style: { stroke: hot ? "var(--danger)" : "var(--border)", strokeWidth: hot ? 2 : 1.25 },
        labelStyle: { fill: "var(--foreground-muted)", fontSize: 10 },
        labelBgStyle: { fill: "var(--surface)" },
        markerEnd: { type: MarkerType.ArrowClosed, color: hot ? "var(--danger)" : "var(--border)" },
      };
    });
    return { nodes, edges, statuses };
  }, [topology, state]);

  if (!topology) return null;
  const rows = Math.max(...topology.nodes.map((n) => n.row)) + 1;
  const troubled = statuses.filter((s) => s.health === "danger" || s.health === "warning");

  return (
    <figure className="flex flex-col gap-2">
      <div className="w-full overflow-hidden rounded-lg border border-border bg-background" style={{ height: 70 + rows * ROW_HEIGHT }} aria-hidden>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={NODE_TYPES}
          fitView
          fitViewOptions={{ padding: 0.12 }}
          nodesDraggable={false}
          nodesConnectable={false}
          elementsSelectable={false}
          panOnDrag={false}
          zoomOnScroll={false}
          zoomOnPinch={false}
          zoomOnDoubleClick={false}
          preventScrolling={false}
          proOptions={{ hideAttribution: true }}
        >
          <Background color="var(--border)" gap={18} />
        </ReactFlow>
      </div>
      <figcaption className="flex flex-col gap-1 text-xs text-foreground-muted">
        {caption && <span>{caption}</span>}
        <span>
          {troubled.length === 0
            ? "모든 구성 요소가 정상 범위입니다."
            : troubled.map((s) => `${s.label}: ${HEALTH_LABEL[s.health]} (${s.detail})`).join(" / ")}
        </span>
        <span className="flex flex-wrap gap-x-3">
          <span><span className="text-success">■</span> 정상</span>
          <span><span className="text-warning">■</span> 주의</span>
          <span><span className="text-danger">■</span> 포화·장애</span>
        </span>
      </figcaption>
    </figure>
  );
}
