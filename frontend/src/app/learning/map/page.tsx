"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Background, Handle, MarkerType, Position, ReactFlow, type Edge, type Node, type NodeProps } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { KnowledgeMap, MasteryLevel, getKnowledgeMap } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { MASTERY_META } from "@/lib/mastery";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

type MapNodeData = { label: string; mastery: MasteryLevel; dimmed: boolean };

const TONE_CLASS: Record<MasteryLevel, string> = {
  NOT_STARTED: "border-border text-foreground-muted",
  WEAK: "border-danger text-foreground",
  PRACTICED: "border-warning text-foreground",
  CONFIDENT: "border-success text-foreground",
};

function ConceptNode({ data }: NodeProps<Node<MapNodeData>>) {
  const meta = MASTERY_META[data.mastery];
  return (
    <div
      className={`w-[160px] rounded-lg border-2 bg-surface px-2 py-1 text-xs transition-opacity ${TONE_CLASS[data.mastery]} ${data.dimmed ? "opacity-30" : ""}`}
      title={`${meta.label} — ${meta.hint}`}
    >
      <Handle type="target" position={Position.Left} className="!opacity-0" />
      <span className="mr-1">{meta.symbol}</span>
      {data.label}
      <Handle type="source" position={Position.Right} className="!opacity-0" />
    </div>
  );
}

function HeaderNode({ data }: NodeProps<Node<{ label: string }>>) {
  return <div className="w-[160px] text-[11px] font-semibold uppercase tracking-wide text-foreground-muted">{data.label}</div>;
}

const NODE_TYPES = { concept: ConceptNode, header: HeaderNode };
const COLUMNS_PER_BAND = 3;
const BAND_GAP = 56;
const COLUMN_WIDTH = 230;
const ROW_HEIGHT = 52;

/**
 * PLAN.md Round E18 (docs/LEARNING_EXPANSION_PLAN.md L7) — the 25 concepts the scoring engine
 * knows, categories in two bands of three columns, with prerequisite (arrow) and related (dashed) edges.
 * Hovering a node lights up its neighbours; clicking opens the concept.
 */
export default function KnowledgeMapPage() {
  const router = useRouter();
  const [map, setMap] = useState<KnowledgeMap | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [focus, setFocus] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    getKnowledgeMap()
      .then(setMap)
      .catch(() => setError("지식 맵을 불러오지 못했습니다."));
  }, [router]);

  const categories = useMemo(() => {
    const seen = new Map<string, string>();
    map?.nodes.forEach((n) => seen.set(n.category, n.categoryLabel));
    return [...seen.entries()];
  }, [map]);

  const { nodes, edges } = useMemo(() => {
    if (!map) return { nodes: [] as Node<MapNodeData>[], edges: [] as Edge[] };
    const neighbours = new Set<string>();
    if (focus) {
      neighbours.add(focus);
      map.edges.forEach((e) => {
        if (e.source === focus) neighbours.add(e.target);
        if (e.target === focus) neighbours.add(e.source);
      });
    }
    // Categories in bands of three columns — six side by side are too wide to read once fitted.
    const rowsByCategory = new Map<string, number>();
    map.nodes.forEach((n) => rowsByCategory.set(n.category, (rowsByCategory.get(n.category) ?? 0) + 1));
    const bandTops: number[] = [];
    categories.forEach(([category], i) => {
      const band = Math.floor(i / COLUMNS_PER_BAND);
      const height = 28 + (rowsByCategory.get(category) ?? 0) * ROW_HEIGHT;
      bandTops[band + 1] = Math.max(bandTops[band + 1] ?? 0, (bandTops[band] ?? 0) + height + BAND_GAP);
    });
    const origin = (category: string) => {
      const i = categories.findIndex(([c]) => c === category);
      return { x: (i % COLUMNS_PER_BAND) * COLUMN_WIDTH, y: bandTops[Math.floor(i / COLUMNS_PER_BAND)] ?? 0 };
    };
    const headers: Node[] = categories.map(([category, label]) => ({
      id: `header:${category}`,
      type: "header",
      position: origin(category),
      data: { label },
      selectable: false,
    }));
    const rowInColumn = new Map<string, number>();
    const conceptNodes: Node<MapNodeData>[] = map.nodes.map((n) => {
      const row = rowInColumn.get(n.category) ?? 0;
      rowInColumn.set(n.category, row + 1);
      const { x, y } = origin(n.category);
      return {
        id: n.riskKey,
        type: "concept",
        position: { x, y: y + 28 + row * ROW_HEIGHT },
        data: { label: n.label, mastery: n.mastery, dimmed: focus !== null && !neighbours.has(n.riskKey) },
      };
    });
    const flowNodes = [...headers, ...conceptNodes] as Node<MapNodeData>[];
    const flowEdges: Edge[] = map.edges.map((e) => {
      const lit = focus !== null && (e.source === focus || e.target === focus);
      const stroke = lit ? "var(--accent)" : "var(--border)";
      return {
        id: `${e.source}-${e.target}`,
        source: e.source,
        target: e.target,
        style: { stroke, strokeWidth: lit ? 2 : 1.25, strokeDasharray: e.relation === "RELATED" ? "4 4" : undefined, opacity: focus && !lit ? 0.25 : 1 },
        markerEnd: e.relation === "PREREQUISITE" ? { type: MarkerType.ArrowClosed, color: lit ? "var(--accent)" : "var(--border)" } : undefined,
      };
    });
    return { nodes: flowNodes, edges: flowEdges };
  }, [map, categories, focus]);

  const counts = useMemo(() => {
    const c: Record<MasteryLevel, number> = { NOT_STARTED: 0, WEAK: 0, PRACTICED: 0, CONFIDENT: 0 };
    map?.nodes.forEach((n) => (c[n.mastery] += 1));
    return c;
  }, [map]);

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-4 p-4 sm:p-8">
      <div>
        <Link href="/learning" className="text-sm text-foreground-muted hover:text-foreground">
          ← Learning
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">지식 맵</h1>
        <p className="mt-1 text-sm text-foreground-muted">
          채점 엔진이 아는 {map?.nodes.length ?? 25}개 개념입니다. 화살표는 “먼저 알면 좋은 개념”, 점선은 함께 보면 좋은 개념입니다.
        </p>
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
      {!map && !error && <LoadingState />}
      {map && (
        <>
          <p className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-foreground-muted">
            {(Object.keys(MASTERY_META) as MasteryLevel[]).map((level) => (
              <span key={level} title={MASTERY_META[level].hint}>
                {MASTERY_META[level].symbol} {MASTERY_META[level].label} {counts[level]}
              </span>
            ))}
          </p>
          <Card as="section" className="min-w-0 !p-0">
            <div className="h-[620px] w-full">
              <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={NODE_TYPES}
                nodesDraggable={false}
                nodesConnectable={false}
                fitView
                fitViewOptions={{ padding: 0.04 }}
                minZoom={0.3}
                proOptions={{ hideAttribution: true }}
                onNodeMouseEnter={(_, node) => node.type === "concept" && setFocus(node.id)}
                onNodeMouseLeave={() => setFocus(null)}
                onNodeClick={(_, node) => node.type === "concept" && router.push(`/learning/${node.id}`)}
              >
                <Background color="var(--border)" gap={20} />
              </ReactFlow>
            </div>
          </Card>
          {counts.WEAK > 0 && (
            <Card as="section">
              <h2 className="mb-2 text-sm font-semibold">지금 약점인 개념</h2>
              <div className="flex flex-wrap gap-2 text-sm">
                {map.nodes
                  .filter((n) => n.mastery === "WEAK")
                  .map((n) => (
                    <Link key={n.riskKey} href={`/learning/${n.riskKey}`} className="rounded-full border border-danger/50 px-2.5 py-0.5 hover:border-danger">
                      ⚠ {n.label}
                    </Link>
                  ))}
              </div>
            </Card>
          )}
        </>
      )}
    </div>
  );
}
