"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { type KnowledgeMap, type LabSummary, type LearningConceptDetail, getLearningConcept } from "@/lib/api";
import { DOMAIN_TITLES } from "@/lib/designGuidance";
import { MASTERY_META } from "@/lib/mastery";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

type MapNode = KnowledgeMap["nodes"][number];

/**
 * docs/LEARNING_DEEPENING_PLAN.md L11 — what clicking a node on the knowledge map opens, instead of
 * leaving the map: the concept in one glance, where it sits (what to know first, what it unlocks,
 * the whole prerequisite chain) and where to practise it. Neighbour chips select that concept, so
 * the map can be walked from the panel too.
 */
export function ConceptPanel({
  node,
  map,
  labs,
  path,
  onSelect,
  onClose,
}: {
  node: MapNode;
  map: KnowledgeMap;
  labs: LabSummary[];
  /** Prerequisite chain ending at [node], earliest first (excluding node itself). */
  path: MapNode[];
  onSelect: (riskKey: string) => void;
  onClose: () => void;
}) {
  const [detail, setDetail] = useState<{ riskKey: string; concept: LearningConceptDetail } | null>(null);

  useEffect(() => {
    let cancelled = false;
    getLearningConcept(node.riskKey)
      .then((concept) => !cancelled && setDetail({ riskKey: node.riskKey, concept }))
      .catch(() => !cancelled && setDetail(null));
    return () => {
      cancelled = true;
    };
  }, [node.riskKey]);

  const concept = detail?.riskKey === node.riskKey ? detail.concept : null;
  const byKey = new Map(map.nodes.map((n) => [n.riskKey, n]));
  const linked = (keys: string[]) => keys.map((k) => byKey.get(k)).filter((n): n is MapNode => !!n);
  const prerequisites = linked(map.edges.filter((e) => e.relation === "PREREQUISITE" && e.target === node.riskKey).map((e) => e.source));
  const unlocks = linked(map.edges.filter((e) => e.relation === "PREREQUISITE" && e.source === node.riskKey).map((e) => e.target));
  const related = linked(
    map.edges
      .filter((e) => e.relation === "RELATED" && (e.source === node.riskKey || e.target === node.riskKey))
      .map((e) => (e.source === node.riskKey ? e.target : e.source)),
  );
  const conceptLabs = labs.filter((l) => l.riskKey === node.riskKey);
  const meta = MASTERY_META[node.mastery];

  return (
    <Card as="section" className="flex min-w-0 flex-col gap-3 text-sm">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-xs text-foreground-muted">{node.categoryLabel}</p>
          <h2 className="text-base font-semibold">{node.label}</h2>
        </div>
        <button type="button" onClick={onClose} className="rounded px-1.5 text-foreground-muted hover:text-foreground" aria-label="패널 닫기">
          ✕
        </button>
      </div>

      <p className="text-xs" title={meta.hint}>
        <span className="mr-1">{meta.symbol}</span>
        <span className="font-medium">{meta.label}</span>
        <span className="text-foreground-muted"> — {meta.hint}</span>
      </p>

      <p className="text-foreground-muted">{concept?.summary ?? " "}</p>

      {path.length > 0 && (
        <div className="flex flex-col gap-1">
          <h3 className="text-xs font-semibold text-foreground-muted">먼저 알면 좋은 순서</h3>
          <ol className="flex flex-wrap items-center gap-1 text-xs">
            {path.map((n) => (
              <li key={n.riskKey} className="flex items-center gap-1">
                <ChipButton node={n} onSelect={onSelect} />
                <span className="text-foreground-muted" aria-hidden>
                  →
                </span>
              </li>
            ))}
            <li className="rounded-full border border-accent px-2 py-0.5 font-medium">{node.label}</li>
          </ol>
        </div>
      )}

      <Neighbours title="이 개념 다음에 볼 것" nodes={unlocks} onSelect={onSelect} />
      <Neighbours title="함께 보면 좋은 것" nodes={related} onSelect={onSelect} />
      {prerequisites.length > 0 && path.length === 0 && <Neighbours title="먼저 알면 좋은 것" nodes={prerequisites} onSelect={onSelect} />}

      <div className="flex flex-col gap-2 border-t border-border pt-3">
        <Button href={`/learning/${node.riskKey}`} size="sm" className="self-start">
          개념 읽기 →
        </Button>
        <div className="flex flex-wrap gap-2">
          {conceptLabs.map((lab) => (
            <Button key={lab.slug} href={`/learning/labs/${lab.slug}`} size="sm" variant="secondary">
              랩: {lab.title}
            </Button>
          ))}
          {concept?.relatedChallenges.map((slug) => (
            <Button key={slug} href={`/bridge?challenge=${slug}`} size="sm" variant="secondary">
              Build: {slug}
            </Button>
          ))}
        </div>
        {concept && concept.relatedDomains.length > 0 && (
          <p className="text-xs text-foreground-muted">
            이 개념이 채점되는 Drill:{" "}
            {concept.relatedDomains.map((d, i) => (
              <span key={d}>
                {i > 0 && ", "}
                <Link href={`/tracks/${d}`} className="underline hover:text-foreground">
                  {DOMAIN_TITLES[d] ?? d}
                </Link>
              </span>
            ))}
          </p>
        )}
      </div>
    </Card>
  );
}

function Neighbours({ title, nodes, onSelect }: { title: string; nodes: MapNode[]; onSelect: (riskKey: string) => void }) {
  if (nodes.length === 0) return null;
  return (
    <div className="flex flex-col gap-1">
      <h3 className="text-xs font-semibold text-foreground-muted">{title}</h3>
      <div className="flex flex-wrap gap-1 text-xs">
        {nodes.map((n) => (
          <ChipButton key={n.riskKey} node={n} onSelect={onSelect} />
        ))}
      </div>
    </div>
  );
}

function ChipButton({ node, onSelect }: { node: MapNode; onSelect: (riskKey: string) => void }) {
  return (
    <button
      type="button"
      onClick={() => onSelect(node.riskKey)}
      className="rounded-full border border-border px-2 py-0.5 hover:border-accent"
      title={MASTERY_META[node.mastery].label}
    >
      {MASTERY_META[node.mastery].symbol} {node.label}
    </button>
  );
}
