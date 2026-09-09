"use client";

import { useEffect, useId, useRef, useState } from "react";

const DEBOUNCE_MS = 400;

function prefersDark(): boolean {
  return typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches;
}

/**
 * Renders a Mermaid diagram from raw DSL text. The only place this project
 * talks to `mermaid` (its first runtime dependency beyond next/react/tailwind)
 * — kept as one small, self-contained component so a syntax error from
 * user-typed DSL (Design Workspace) never takes down the surrounding page.
 * Theme follows this app's only dark-mode mechanism (`prefers-color-scheme`,
 * see globals.css) since there is no manual toggle to read state from.
 */
export function MermaidDiagram({ code }: { code: string }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const rawId = useId().replace(/[^a-zA-Z0-9]/g, "");
  // mermaid.render() errors if an element with the given id already exists in
  // the live document -- and a prior successful render's SVG (inserted below
  // via innerHTML) IS such an element, so every render attempt needs its own
  // fresh id rather than reusing one across re-renders.
  const renderCountRef = useRef(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const timer = setTimeout(() => {
      import("mermaid").then(async ({ default: mermaid }) => {
        if (cancelled) return;
        mermaid.initialize({ startOnLoad: false, theme: prefersDark() ? "dark" : "default", securityLevel: "strict" });
        const renderId = `mermaid-${rawId}-${renderCountRef.current++}`;
        try {
          const { svg } = await mermaid.render(renderId, code);
          if (!cancelled && containerRef.current) {
            containerRef.current.innerHTML = svg;
          }
          if (!cancelled) setError(null);
        } catch {
          if (!cancelled) setError("다이어그램 문법 오류 — 아래 코드를 확인해주세요.");
        } finally {
          // On a syntax error mermaid leaves its own temporary render container
          // (id `d<renderId>`) attached directly to document.body instead of
          // cleaning it up — remove it so a stray error SVG doesn't float at
          // the bottom of the page outside this component's own error UI.
          document.getElementById(`d${renderId}`)?.remove();
        }
      });
    }, DEBOUNCE_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [code, rawId]);

  return (
    <div>
      {error && (
        <div className="mb-2 rounded border border-red-300 p-3 text-xs dark:border-red-800">
          <p className="mb-2 text-red-600">{error}</p>
          <pre className="overflow-x-auto whitespace-pre-wrap text-zinc-500">{code}</pre>
        </div>
      )}
      {/* Always mounted (never conditionally unmounted) -- keeping containerRef
          valid across error <-> success transitions is what lets a later
          successful render actually take effect and clear a stale error. */}
      <div ref={containerRef} className={`overflow-x-auto ${error ? "hidden" : ""}`} />
    </div>
  );
}
