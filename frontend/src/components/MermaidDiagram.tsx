"use client";

import { useEffect, useId, useRef, useState } from "react";
import { useAppTheme } from "@/lib/useAppTheme";

const DEBOUNCE_MS = 400;

/**
 * Renders a Mermaid diagram from raw DSL text. The only place this project
 * talks to `mermaid` (its first runtime dependency beyond next/react/tailwind)
 * — kept as one small, self-contained component so a syntax error from
 * user-typed DSL (Design Workspace) never takes down the surrounding page.
 * Every app theme is dark, but each has its own palette — so the diagram uses
 * mermaid's `base` theme fed from the current theme's CSS tokens, and re-renders
 * when the app theme switches (docs/LEARNING_DEEPENING_PLAN.md L12).
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
  const appTheme = useAppTheme();

  useEffect(() => {
    let cancelled = false;
    const timer = setTimeout(() => {
      import("mermaid").then(async ({ default: mermaid }) => {
        if (cancelled) return;
        mermaid.initialize({ startOnLoad: false, theme: "base", securityLevel: "strict", themeVariables: themeVariables() });
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
  }, [code, rawId, appTheme]);

  return (
    <div>
      {error && (
        <div className="mb-2 rounded-lg border border-danger/40 p-3 text-xs">
          <p className="mb-2 text-danger">{error}</p>
          <pre className="overflow-x-auto whitespace-pre-wrap text-foreground-muted">{code}</pre>
        </div>
      )}
      {/* Always mounted (never conditionally unmounted) -- keeping containerRef
          valid across error <-> success transitions is what lets a later
          successful render actually take effect and clear a stale error. */}
      <div ref={containerRef} className={`overflow-x-auto ${error ? "hidden" : ""}`} />
    </div>
  );
}

/** The current app theme's tokens as mermaid `base` theme variables. */
function themeVariables(): Record<string, string | boolean> {
  const css = getComputedStyle(document.documentElement);
  const token = (name: string, fallback: string) => css.getPropertyValue(name).trim() || fallback;
  const surface = token("--surface", "#0f1b2b");
  const elevated = token("--surface-elevated", surface);
  const foreground = token("--foreground", "#f5f7fa");
  const muted = token("--foreground-muted", "#94a3b8");
  const accent = token("--accent", "#2f80ff");
  return {
    darkMode: true,
    background: token("--background", "#08111f"),
    primaryColor: elevated,
    primaryTextColor: foreground,
    primaryBorderColor: accent,
    secondaryColor: surface,
    tertiaryColor: surface,
    lineColor: muted,
    textColor: foreground,
    edgeLabelBackground: surface,
    clusterBkg: surface,
    clusterBorder: token("--border", "#1e2f45"),
    fontFamily: "inherit",
    fontSize: "13px",
  };
}
