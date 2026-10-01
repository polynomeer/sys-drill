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
  children,
}: {
  done: boolean;
  onContinue: () => void;
  onExpandAll: () => void;
  onDone?: () => void;
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
    <div className="mx-auto flex max-w-3xl flex-col gap-5 p-8">
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
  );
}
