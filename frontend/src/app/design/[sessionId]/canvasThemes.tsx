"use client";

import type { CSSProperties, ReactNode } from "react";
import { BackgroundVariant } from "@xyflow/react";
import { Database, Globe, Layers, Monitor, Network, Server, Zap, type LucideIcon } from "lucide-react";
import type { AppThemeId } from "@/lib/appTheme";

/**
 * Canvas visual themes — purely a per-viewer display preference. A theme only
 * changes how nodes/edges/background are drawn; the graph itself (and its
 * Mermaid serialization, per ADR-0036) is identical across themes.
 */
export type CanvasThemeId = "app" | "classic" | "icon" | "blueprint" | "neon" | "terminal" | "sticky";

export type NodeVisual = { kindLabel: string; color: string; Icon: LucideIcon };

export const KIND_ICONS: Record<string, LucideIcon> = {
  client: Monitor,
  gateway: Network,
  service: Server,
  db: Database,
  cache: Zap,
  queue: Layers,
  cdn: Globe,
};

type CanvasSurface = {
  background: string;
  patternColor: string;
  variant: BackgroundVariant;
  gap: number;
  /** Must match the theme's `--xy-edge-stroke` in globals.css — the arrowhead marker is colored separately from the edge path. */
  edgeColor: string;
  colorScheme: "dark" | "light";
};

/** Class names for the shared label input / trait fields, so every theme reuses the same editing controls. */
type FieldClasses = { label: string; traitRow: string; traitInput: string; traitDivider: string };

type CanvasTheme = { id: CanvasThemeId; label: string; surface: CanvasSurface; fields: FieldClasses };

const DEFAULT_FIELDS: FieldClasses = {
  label: "nodrag w-full bg-transparent text-sm font-semibold text-foreground outline-none",
  traitRow: "flex items-center justify-between gap-2 text-[10px] text-foreground-muted",
  traitInput: "w-16 rounded border border-border bg-transparent px-1 py-0.5 text-right text-foreground outline-none",
  traitDivider: "border-t border-border",
};

export const CANVAS_THEMES: CanvasTheme[] = [
  {
    // Follows the app theme: every color, radius, display font and panel
    // decoration (Quest drop shadow, Arcade thick border, RPG frame, Tactical
    // corner brackets) comes from the `[data-theme]` tokens via `.ui-card`.
    id: "app",
    label: "앱 테마",
    surface: { background: "var(--background)", patternColor: "var(--border)", variant: BackgroundVariant.Dots, gap: 20, edgeColor: "var(--foreground-muted)", colorScheme: "dark" },
    fields: DEFAULT_FIELDS,
  },
  {
    id: "classic",
    label: "Classic",
    surface: { background: "var(--surface)", patternColor: "var(--border)", variant: BackgroundVariant.Dots, gap: 16, edgeColor: "#b1b1b7", colorScheme: "dark" },
    fields: DEFAULT_FIELDS,
  },
  {
    id: "icon",
    label: "Icon",
    surface: { background: "var(--surface)", patternColor: "var(--border)", variant: BackgroundVariant.Dots, gap: 20, edgeColor: "#b1b1b7", colorScheme: "dark" },
    fields: { ...DEFAULT_FIELDS, label: "nodrag w-full bg-transparent text-center text-xs font-semibold text-foreground outline-none" },
  },
  {
    id: "blueprint",
    label: "Blueprint",
    surface: { background: "#0d3b66", patternColor: "rgba(224, 242, 254, 0.12)", variant: BackgroundVariant.Lines, gap: 20, edgeColor: "#e0f2fe", colorScheme: "dark" },
    fields: {
      label: "nodrag w-full bg-transparent font-mono text-sm font-semibold text-sky-50 outline-none",
      traitRow: "flex items-center justify-between gap-2 font-mono text-[10px] text-sky-200/80",
      traitInput: "w-16 border border-dashed border-sky-100/60 bg-transparent px-1 py-0.5 text-right font-mono text-sky-50 outline-none",
      traitDivider: "border-t border-dashed border-sky-100/40",
    },
  },
  {
    id: "neon",
    label: "Neon",
    surface: { background: "#05060a", patternColor: "#1c2233", variant: BackgroundVariant.Dots, gap: 18, edgeColor: "#22d3ee", colorScheme: "dark" },
    fields: {
      ...DEFAULT_FIELDS,
      traitInput: "w-16 rounded border border-white/15 bg-black/40 px-1 py-0.5 text-right text-foreground outline-none",
      traitDivider: "border-t border-white/10",
    },
  },
  {
    id: "terminal",
    label: "Terminal",
    surface: { background: "#060a06", patternColor: "#123012", variant: BackgroundVariant.Dots, gap: 14, edgeColor: "#22c55e", colorScheme: "dark" },
    fields: {
      label: "nodrag w-full bg-transparent font-mono text-sm text-green-300 caret-green-400 outline-none",
      traitRow: "flex items-center justify-between gap-2 font-mono text-[10px] text-green-500",
      traitInput: "w-16 border border-green-700 bg-transparent px-1 py-0.5 text-right font-mono text-green-300 outline-none",
      traitDivider: "border-t border-green-900",
    },
  },
  {
    id: "sticky",
    label: "Sticky",
    surface: { background: "#f4efe3", patternColor: "#d4ccb8", variant: BackgroundVariant.Dots, gap: 22, edgeColor: "#475569", colorScheme: "light" },
    fields: {
      label: "nodrag w-full bg-transparent text-sm font-bold text-slate-800 outline-none",
      traitRow: "flex items-center justify-between gap-2 text-[10px] text-slate-600",
      traitInput: "w-16 rounded border border-slate-400/50 bg-white/50 px-1 py-0.5 text-right text-slate-800 outline-none",
      traitDivider: "border-t border-slate-500/25",
    },
  },
];

export const AUTO_CANVAS_THEME: CanvasThemeId = "app";

export function canvasThemeById(id: string | null): CanvasTheme {
  return CANVAS_THEMES.find((t) => t.id === id) ?? CANVAS_THEMES[0];
}

/** The app-theme canvas swaps dots for the same grid lines Arcade and Tactical draw behind the page. */
export function canvasSurface(theme: CanvasTheme, appTheme: AppThemeId): CanvasSurface {
  if (theme.id === "app" && (appTheme === "arcade" || appTheme === "tactical")) {
    return { ...theme.surface, variant: BackgroundVariant.Lines, gap: 32 };
  }
  return theme.surface;
}

/** Small deterministic tilt (−2°..2°) so sticky notes look hand-placed but don't jitter between renders. */
function stickyTilt(id: string): number {
  let hash = 0;
  for (const ch of id) hash = (hash * 31 + ch.charCodeAt(0)) | 0;
  return (Math.abs(hash) % 5) - 2;
}

const HANDWRITING: CSSProperties = { fontFamily: '"Chalkboard SE", "Comic Sans MS", "Nanum Pen Script", cursive' };

/**
 * The per-theme node chrome. `editor` is the shared label input + trait
 * fields (already styled with the theme's `fields` classes); each theme only
 * decides what surrounds it.
 */
export function ThemedNodeBody({
  themeId,
  nodeId,
  visual,
  editor,
}: {
  themeId: CanvasThemeId;
  nodeId: string;
  visual: NodeVisual;
  editor: ReactNode;
}) {
  const { kindLabel, color, Icon } = visual;

  switch (themeId) {
    case "app":
      return (
        <div
          className="ui-card rounded-xl border border-border bg-surface px-3 py-2 text-foreground"
          style={{ borderTop: `3px solid ${color}`, minWidth: 128 }}
        >
          <p className="mb-1 flex items-center gap-1.5 font-display text-[10px] uppercase tracking-wide" style={{ color }}>
            <Icon size={12} />
            {kindLabel}
          </p>
          {editor}
        </div>
      );

    case "icon":
      return (
        <div className="flex w-[136px] flex-col items-center gap-1.5">
          <div
            className="flex h-14 w-14 items-center justify-center rounded-2xl shadow-sm"
            style={{ background: `${color}1f`, border: `1.5px solid ${color}`, color }}
          >
            <Icon size={26} strokeWidth={1.75} />
          </div>
          <span className="text-[9px] uppercase tracking-wider" style={{ color }}>
            {kindLabel}
          </span>
          <div className="w-full">{editor}</div>
        </div>
      );

    case "blueprint":
      return (
        <div
          className="border-[1.5px] border-dashed border-sky-100/80 px-3 py-2"
          style={{ background: "rgba(13, 59, 102, 0.85)", minWidth: 132 }}
        >
          <p className="mb-1 flex items-center gap-1 font-mono text-[10px] uppercase tracking-widest text-sky-200">
            <Icon size={11} strokeWidth={1.5} />
            {kindLabel}
          </p>
          {editor}
        </div>
      );

    case "neon":
      return (
        <div
          className="rounded-xl border px-3 py-2"
          style={{
            borderColor: color,
            background: "#0a0d14",
            boxShadow: `0 0 14px ${color}88, inset 0 0 10px ${color}33`,
            minWidth: 128,
          }}
        >
          <p
            className="mb-1 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider"
            style={{ color, textShadow: `0 0 6px ${color}` }}
          >
            <Icon size={12} style={{ filter: `drop-shadow(0 0 4px ${color})` }} />
            {kindLabel}
          </p>
          {editor}
        </div>
      );

    case "terminal":
      return (
        <div className="border border-green-600 bg-[#050805] font-mono" style={{ minWidth: 140 }}>
          <p className="flex items-center justify-between bg-green-600 px-2 py-0.5 text-[10px] font-bold text-black">
            <span>{kindLabel.toLowerCase().replace(/\s+/g, "-")}.sh</span>
            <span>■ ■</span>
          </p>
          <div className="flex items-baseline gap-1 px-2 pt-1.5 text-green-500">
            <span className="text-xs">$</span>
            <div className="min-w-0 flex-1">{editor}</div>
          </div>
          <div className="h-1.5" />
        </div>
      );

    case "sticky":
      return (
        <div
          className="relative px-3 pb-3 pt-4"
          style={{
            ...HANDWRITING,
            background: `color-mix(in srgb, ${color} 30%, #fff8dc)`,
            boxShadow: "2px 5px 10px rgba(60, 50, 30, 0.22)",
            transform: `rotate(${stickyTilt(nodeId)}deg)`,
            minWidth: 128,
          }}
        >
          <span
            aria-hidden
            className="absolute -top-2 left-3 h-4 w-10 rotate-[-4deg]"
            style={{ background: "rgba(255, 255, 255, 0.55)", boxShadow: "0 1px 2px rgba(0,0,0,0.08)" }}
          />
          <p className="mb-1 flex items-center gap-1 text-[11px] text-slate-600">
            <Icon size={12} />
            {kindLabel}
          </p>
          {editor}
        </div>
      );

    default:
      return (
        <div
          className="rounded-lg border-2 px-3 py-2 text-xs font-medium text-foreground shadow-sm"
          style={{ borderColor: color, background: "var(--surface-elevated)", minWidth: 120 }}
        >
          <p className="mb-1 text-[10px] uppercase tracking-wide" style={{ color }}>
            {kindLabel}
          </p>
          {editor}
        </div>
      );
  }
}
