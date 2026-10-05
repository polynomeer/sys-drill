"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Background, Handle, MarkerType, Position, ReactFlow, type Edge, type Node, type NodeProps } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { KnowledgeMap, LabSummary, MasteryLevel, getKnowledgeMap, listLabs } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { MASTERY_META } from "@/lib/mastery";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { LoadingState } from "@/components/ui/LoadingState";
import { ConceptPanel } from "./ConceptPanel";

type MapNode = KnowledgeMap["nodes"][number];
type MapNodeData = { label: string; mastery: MasteryLevel; riskKey: string; onSelect: (riskKey: string) => void };

const TONE_CLASS: Record<MasteryLevel, string> = {
  NOT_STARTED: "border-border text-foreground-muted",
  WEAK: "border-danger text-foreground",
  PRACTICED: "border-warning text-foreground",
  CONFIDENT: "border-success text-foreground",
};

/** A button, so the map is keyboard-reachable (Tab → Enter) and the click target is unmistakable. */
function ConceptNode({ data }: NodeProps<Node<MapNodeData>>) {
  const meta = MASTERY_META[data.mastery];
  return (
    <button
      type="button"
      onClick={() => data.onSelect(data.riskKey)}
      className={`kmap-card w-[160px] rounded-lg border-2 bg-surface px-2 py-1 text-left text-xs ${TONE_CLASS[data.mastery]}`}
      title={`${data.label} — ${meta.label}. 클릭하면 요약, 두 번 클릭하면 개념 페이지`}
    >
      <Handle type="target" position={Position.Left} className="!opacity-0" />
      <span className="mr-1">{meta.symbol}</span>
      {data.label}
      <Handle type="source" position={Position.Right} className="!opacity-0" />
    </button>
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
/** Leaving a node waits this long before un-highlighting, so crossing the gap to the next node doesn't flash the whole map. */
const LEAVE_DELAY_MS = 120;

/** Concepts that must come before [target], earliest first — a walk back along PREREQUISITE edges. */
function prerequisiteChain(map: KnowledgeMap, target: string): string[] {
  const parents = new Map<string, string[]>();
  map.edges
    .filter((e) => e.relation === "PREREQUISITE")
    .forEach((e) => parents.set(e.target, [...(parents.get(e.target) ?? []), e.source]));
  const order: string[] = [];
  const seen = new Set<string>([target]);
  const visit = (key: string) => {
    for (const parent of parents.get(key) ?? []) {
      if (seen.has(parent)) continue;
      seen.add(parent);
      visit(parent);
      order.push(parent);
    }
  };
  visit(target);
  return order;
}

/**
 * PLAN.md Round E18 (docs/LEARNING_EXPANSION_PLAN.md L7), reworked in Round E33
 * (docs/LEARNING_DEEPENING_PLAN.md L11). The concepts the scoring engine knows, categories in
 * bands of three columns, prerequisite (arrow) and related (dashed) edges.
 *
 * Hover highlighting never goes through React state: rebuilding every node and edge on each
 * hover change is what made the map flicker (one sweep across five nodes re-created 118 DOM
 * nodes). Each node carries its neighbours as classes, hover only sets `data-focus` on the
 * wrapper, and one generated stylesheet dims everything else. Clicking selects a concept and
 * opens its panel; double-click opens the concept page.
 */
export default function KnowledgeMapPage() {
  const router = useRouter();
  const [map, setMap] = useState<KnowledgeMap | null>(null);
  const [labs, setLabs] = useState<LabSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState<string>("ALL");
  const [weakOnly, setWeakOnly] = useState(false);
  const wrapper = useRef<HTMLDivElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const leaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    getKnowledgeMap()
      .then(setMap)
      .catch(() => setError("지식 맵을 불러오지 못했습니다."));
    listLabs().then(setLabs).catch(() => setLabs([]));
  }, [router]);

  /** Hover focus = the hovered node, falling back to the selected one. Written straight to the DOM. */
  const setFocus = useCallback(
    (riskKey: string | null) => {
      if (leaveTimer.current) clearTimeout(leaveTimer.current);
      leaveTimer.current = null;
      const focus = riskKey ?? selected;
      if (focus) wrapper.current?.setAttribute("data-focus", focus);
      else wrapper.current?.removeAttribute("data-focus");
    },
    [selected],
  );
  const releaseFocus = useCallback(() => {
    if (leaveTimer.current) clearTimeout(leaveTimer.current);
    leaveTimer.current = setTimeout(() => setFocus(null), LEAVE_DELAY_MS);
  }, [setFocus]);

  // A new selection becomes the resting focus.
  useEffect(() => setFocus(null), [setFocus]);
  useEffect(() => () => {
    if (leaveTimer.current) clearTimeout(leaveTimer.current);
  }, []);

  const select = useCallback((riskKey: string) => {
    setSelected(riskKey);
    // On narrow screens the panel sits below the map — bring it into view. Its wrapper is always
    // rendered and its top doesn't move when the content swaps, so no need to wait for the render.
    if (window.matchMedia("(max-width: 1023px)").matches) {
      panel.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, []);

  const categories = useMemo(() => {
    const seen = new Map<string, string>();
    map?.nodes.forEach((n) => seen.set(n.category, n.categoryLabel));
    return [...seen.entries()];
  }, [map]);

  const path = useMemo(() => (map && selected ? prerequisiteChain(map, selected) : []), [map, selected]);

  const matches = useMemo(() => {
    const q = query.trim().toLowerCase();
    const filtering = q !== "" || category !== "ALL" || weakOnly;
    if (!map || !filtering) return null;
    return new Set(
      map.nodes
        .filter((n) => (q === "" || n.label.toLowerCase().includes(q)) && (category === "ALL" || n.category === category) && (!weakOnly || n.mastery === "WEAK"))
        .map((n) => n.riskKey),
    );
  }, [map, query, category, weakOnly]);

  // Positions and neighbour classes — rebuilt only when the data, the selection or a filter changes.
  const { nodes, edges } = useMemo(() => {
    if (!map) return { nodes: [] as Node[], edges: [] as Edge[] };
    const neighbours = new Map<string, Set<string>>(map.nodes.map((n) => [n.riskKey, new Set([n.riskKey])]));
    map.edges.forEach((e) => {
      neighbours.get(e.source)?.add(e.target);
      neighbours.get(e.target)?.add(e.source);
    });
    const onPath = new Set(path);
    // Categories in bands of three columns — six side by side are too wide to read once fitted.
    const rowsByCategory = new Map<string, number>();
    map.nodes.forEach((n) => rowsByCategory.set(n.category, (rowsByCategory.get(n.category) ?? 0) + 1));
    const bandTops: number[] = [];
    categories.forEach(([c], i) => {
      const band = Math.floor(i / COLUMNS_PER_BAND);
      const height = 28 + (rowsByCategory.get(c) ?? 0) * ROW_HEIGHT;
      bandTops[band + 1] = Math.max(bandTops[band + 1] ?? 0, (bandTops[band] ?? 0) + height + BAND_GAP);
    });
    const origin = (c: string) => {
      const i = categories.findIndex(([key]) => key === c);
      return { x: (i % COLUMNS_PER_BAND) * COLUMN_WIDTH, y: bandTops[Math.floor(i / COLUMNS_PER_BAND)] ?? 0 };
    };
    const headers: Node[] = categories.map(([c, label]) => ({
      id: `header:${c}`,
      type: "header",
      position: origin(c),
      data: { label },
      selectable: false,
      focusable: false,
    }));
    const rowInColumn = new Map<string, number>();
    const conceptNodes: Node<MapNodeData>[] = map.nodes.map((n) => {
      const row = rowInColumn.get(n.category) ?? 0;
      rowInColumn.set(n.category, row + 1);
      const { x, y } = origin(n.category);
      const classes = ["kmap-node", ...[...(neighbours.get(n.riskKey) ?? [])].map((k) => `kn-${k}`)];
      if (n.riskKey === selected) classes.push("kmap-selected");
      if (onPath.has(n.riskKey)) classes.push("kmap-path");
      if (matches && !matches.has(n.riskKey)) classes.push("kmap-filtered");
      return {
        id: n.riskKey,
        type: "concept",
        position: { x, y: y + 28 + row * ROW_HEIGHT },
        className: classes.join(" "),
        // The button inside the node takes focus and handles the click; the wrapper shouldn't.
        focusable: false,
        data: { label: n.label, mastery: n.mastery, riskKey: n.riskKey, onSelect: select },
      };
    });
    const flowEdges: Edge[] = map.edges.map((e) => ({
      id: `${e.source}-${e.target}`,
      source: e.source,
      target: e.target,
      className: `kmap-edge ke-${e.source} ke-${e.target}`,
      style: { strokeDasharray: e.relation === "RELATED" ? "4 4" : undefined },
      markerEnd: e.relation === "PREREQUISITE" ? { type: MarkerType.ArrowClosed, color: "var(--border)" } : undefined,
    }));
    return { nodes: [...headers, ...conceptNodes], edges: flowEdges };
  }, [map, categories, selected, path, matches, select]);

  /** One rule pair per concept: while it's focused, everything that isn't it or a neighbour fades. */
  const focusCss = useMemo(
    () =>
      (map?.nodes ?? [])
        .map(
          ({ riskKey: k }) =>
            `.kmap[data-focus="${k}"] .react-flow__node.kmap-node:not(.kn-${k}){opacity:.25}` +
            `.kmap[data-focus="${k}"] .react-flow__edge.kmap-edge:not(.ke-${k}){opacity:.12}` +
            `.kmap[data-focus="${k}"] .react-flow__edge.ke-${k} path{stroke:var(--accent);stroke-width:2}`,
        )
        .join("\n"),
    [map],
  );

  const counts = useMemo(() => {
    const c: Record<MasteryLevel, number> = { NOT_STARTED: 0, WEAK: 0, PRACTICED: 0, CONFIDENT: 0 };
    map?.nodes.forEach((n) => (c[n.mastery] += 1));
    return c;
  }, [map]);

  const selectedNode = map?.nodes.find((n) => n.riskKey === selected) ?? null;
  const byKey = useMemo(() => new Map((map?.nodes ?? []).map((n) => [n.riskKey, n])), [map]);

  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-4 p-4 sm:p-8">
      <div>
        <Link href="/learning" className="text-sm text-foreground-muted hover:text-foreground">
          ← Learning
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">지식 맵</h1>
        <p className="mt-1 text-sm text-foreground-muted">
          채점 엔진이 아는 {map ? `${map.nodes.length}개 ` : ""}개념입니다. 화살표는 “먼저 알면 좋은 개념”, 점선은 함께 보면 좋은 개념입니다. 개념을 누르면
          요약과 선행 순서가 열립니다.
        </p>
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
      {!map && !error && <LoadingState />}
      {map && (
        <>
          <style>{`
            .kmap .react-flow__node, .kmap .react-flow__edge { transition: opacity 150ms ease; }
            .kmap .react-flow__edge-path { stroke: var(--border); stroke-width: 1.25; }
            .kmap .react-flow__node.kmap-filtered { opacity: .2; }
            .kmap .react-flow__node.kmap-path .kmap-card { border-color: var(--accent); }
            .kmap .react-flow__node.kmap-selected .kmap-card { border-color: var(--accent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 35%, transparent); }
            .kmap .kmap-card:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
            ${focusCss}
          `}</style>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
            <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="개념 이름으로 찾기" className="sm:w-56" aria-label="개념 검색" />
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="rounded-md border border-border bg-surface px-2 py-2 text-sm"
              aria-label="카테고리"
            >
              <option value="ALL">모든 카테고리</option>
              {categories.map(([c, label]) => (
                <option key={c} value={c}>
                  {label}
                </option>
              ))}
            </select>
            <label className="flex items-center gap-2 text-sm text-foreground-muted">
              <input type="checkbox" checked={weakOnly} onChange={(e) => setWeakOnly(e.target.checked)} />
              약점만 ({counts.WEAK})
            </label>
            <p className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-foreground-muted sm:ml-auto">
              {(Object.keys(MASTERY_META) as MasteryLevel[]).map((level) => (
                <span key={level} title={MASTERY_META[level].hint}>
                  {MASTERY_META[level].symbol} {MASTERY_META[level].label} {counts[level]}
                </span>
              ))}
            </p>
          </div>
          {matches && (
            <p className="text-xs text-foreground-muted">
              {matches.size === 0 ? "조건에 맞는 개념이 없습니다." : `${matches.size}개 개념이 조건에 맞습니다 — 나머지는 흐리게 표시합니다.`}
            </p>
          )}
          <div className="flex flex-col gap-4 lg:grid lg:grid-cols-[minmax(0,1fr)_320px] lg:items-start">
            <Card as="section" className="min-w-0 !p-0">
              <div ref={wrapper} className="kmap h-[560px] w-full sm:h-[620px]">
                <ReactFlow
                  nodes={nodes}
                  edges={edges}
                  nodeTypes={NODE_TYPES}
                  nodesDraggable={false}
                  nodesConnectable={false}
                  elementsSelectable={false}
                  // d3-zoom's double-click handler stops the event before React sees it — with it on,
                  // onNodeDoubleClick never fires. Double-click opens the concept instead.
                  zoomOnDoubleClick={false}
                  fitView
                  fitViewOptions={{ padding: 0.04 }}
                  minZoom={0.3}
                  proOptions={{ hideAttribution: true }}
                  onNodeMouseEnter={(_, node) => node.type === "concept" && setFocus(node.id)}
                  onNodeMouseLeave={releaseFocus}
                  onNodeDoubleClick={(_, node) => node.type === "concept" && router.push(`/learning/${node.id}`)}
                  onPaneClick={() => setSelected(null)}
                >
                  <Background color="var(--border)" gap={20} />
                </ReactFlow>
              </div>
            </Card>
            <div ref={panel} className="min-w-0 lg:sticky lg:top-6">
              {selectedNode ? (
                <ConceptPanel
                  node={selectedNode}
                  map={map}
                  labs={labs}
                  path={path.map((k) => byKey.get(k)).filter((n): n is MapNode => !!n)}
                  onSelect={select}
                  onClose={() => setSelected(null)}
                />
              ) : (
                <Card as="section" className="flex flex-col gap-2 text-sm text-foreground-muted">
                  <p className="font-medium text-foreground">개념을 골라 보세요</p>
                  <p>노드를 누르면 여기에 요약, 먼저 알면 좋은 순서, 관련 랩·Build·Drill이 열립니다. 두 번 누르면 개념 페이지로 갑니다.</p>
                  {counts.WEAK > 0 && (
                    <div className="flex flex-col gap-1 pt-1">
                      <p className="text-xs font-semibold">지금 약점인 개념</p>
                      <div className="flex flex-wrap gap-1 text-xs">
                        {map.nodes
                          .filter((n) => n.mastery === "WEAK")
                          .map((n) => (
                            <button key={n.riskKey} type="button" onClick={() => select(n.riskKey)} className="rounded-full border border-danger/50 px-2 py-0.5 hover:border-danger">
                              ⚠ {n.label}
                            </button>
                          ))}
                      </div>
                    </div>
                  )}
                </Card>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
