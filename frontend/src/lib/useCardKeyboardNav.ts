"use client";

import { useEffect, type RefObject } from "react";

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.9 — `j`/`k` move focus between the card
 * links inside [container] (Enter opens the focused one natively). Ignored
 * while typing in a field or with a modifier held.
 */
export function useCardKeyboardNav(container: RefObject<HTMLElement | null>) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key !== "j" && e.key !== "k") return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      const target = e.target as HTMLElement | null;
      if (target?.closest("input, textarea, select, [contenteditable=true]")) return;
      const links = Array.from(container.current?.querySelectorAll<HTMLAnchorElement>("a[data-card]") ?? []);
      if (links.length === 0) return;
      e.preventDefault();
      const current = links.indexOf(document.activeElement as HTMLAnchorElement);
      const next = current === -1 ? 0 : Math.min(links.length - 1, Math.max(0, current + (e.key === "j" ? 1 : -1)));
      links[next].focus();
      links[next].scrollIntoView({ block: "nearest" });
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [container]);
}
