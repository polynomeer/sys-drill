"use client";

import { useState } from "react";
import { type ReactionKind, type ReactionSummary, toggleReaction } from "@/lib/api";
import { DOMAIN_TITLES } from "@/lib/designGuidance";

const KINDS: { kind: ReactionKind; label: string }[] = [
  { kind: "HELPFUL", label: "도움됨" },
  { kind: "INSIGHT", label: "통찰" },
  { kind: "GOOD_TRADEOFF", label: "좋은 트레이드오프" },
];

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C14 (PLAN.md Round E32) — typed reactions instead of a like.
 * Your own posts show counts but can't be reacted to.
 */
export function ReactionBar({
  targetType,
  targetId,
  initial,
  mine,
}: {
  targetType: "DISCUSSION" | "WRITEUP_COMMENT";
  targetId: string;
  initial: ReactionSummary | null | undefined;
  /** The viewer wrote it. */
  mine: boolean;
}) {
  const [summary, setSummary] = useState<ReactionSummary>(initial ?? { counts: {}, mine: [] });

  async function toggle(kind: ReactionKind) {
    try {
      setSummary(await toggleReaction(targetType, targetId, kind));
    } catch {
      // a hidden post or a lost gate — leave the counts as they were
    }
  }

  return (
    <div className="mt-1 flex flex-wrap gap-1.5">
      {KINDS.map(({ kind, label }) => {
        const n = summary.counts[kind] ?? 0;
        const active = summary.mine.includes(kind);
        if (mine && n === 0) return null;
        return (
          <button
            key={kind}
            type="button"
            disabled={mine}
            onClick={() => toggle(kind)}
            className={`rounded-full border px-2 py-0.5 text-[11px] ${active ? "border-accent text-foreground" : "border-border text-foreground-muted"} disabled:cursor-default`}
          >
            {label}
            {n > 0 && ` ${n}`}
          </button>
        );
      })}
    </div>
  );
}

/** C14 — per-domain sums on a profile. No rank, no score. */
export function ReputationList({ items }: { items: { domain: string; helpful: number; insight: number; goodTradeoff: number; total: number }[] }) {
  if (items.length === 0) return <p className="text-sm text-foreground-muted">아직 받은 반응이 없습니다. 토론과 풀이 리뷰에 남긴 글에 반응이 모입니다.</p>;
  return (
    <ul className="flex flex-col gap-1 text-sm">
      {items.map((r) => (
        <li key={r.domain} className="flex flex-wrap items-baseline justify-between gap-2">
          <span>{DOMAIN_TITLES[r.domain] ?? r.domain}</span>
          <span className="text-xs text-foreground-muted">
            도움됨 {r.helpful} · 통찰 {r.insight} · 좋은 트레이드오프 {r.goodTradeoff}
          </span>
        </li>
      ))}
    </ul>
  );
}
