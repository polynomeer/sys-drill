"use client";

import { useCallback, useEffect } from "react";
import { Button } from "@/components/ui/Button";

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.6 — block-by-block reading (CodeCrafters
 * Concepts): a "계속" button plus Enter ↵ until every block is out, and a
 * "모두 펼치기" escape hatch. [onDone] fires once everything is shown — the
 * concept page uses it to remember that the concept was read.
 */
export function BlockReader({
  done,
  onContinue,
  onExpandAll,
  onDone,
  aside,
  children,
}: {
  done: boolean;
  onContinue: () => void;
  onExpandAll: () => void;
  onDone?: () => void;
  /** Optional side column (meta, related links) shown beside the blocks at lg. */
  aside?: React.ReactNode;
  children: React.ReactNode;
}) {
  const handleKey = useCallback(
    (e: KeyboardEvent) => {
      // Don't hijack Enter while the user is typing somewhere (e.g. the header search).
      if (e.key !== "Enter" || done) return;
      if (e.target instanceof Element && e.target.closest("input, textarea, select, button, a")) return;
      e.preventDefault();
      onContinue();
    },
    [done, onContinue],
  );

  useEffect(() => {
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [handleKey]);

  useEffect(() => {
    if (done) onDone?.();
  }, [done, onDone]);

  return (
    <div
      className={`mx-auto flex w-full max-w-7xl flex-col gap-6 p-8 ${aside ? "lg:grid lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start" : ""}`}
    >
      <div className={`flex min-w-0 flex-col gap-5 ${aside ? "" : "lg:max-w-4xl"}`}>
        {children}
        {!done && (
          <div className="flex items-center gap-4">
            <div className="flex flex-col items-center gap-1">
              <Button onClick={onContinue}>계속</Button>
              <span className="text-[11px] text-foreground-muted">Enter ↵</span>
            </div>
            <button onClick={onExpandAll} className="self-start pt-2 text-xs text-foreground-muted underline hover:text-foreground">
              모두 펼치기
            </button>
          </div>
        )}
      </div>
      {aside && <aside className="flex min-w-0 flex-col gap-4 lg:sticky lg:top-6">{aside}</aside>}
    </div>
  );
}
