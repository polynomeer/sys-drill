"use client";

import { Fragment, type ReactNode } from "react";
import type { ComparePane, ContentBlock, SystemState } from "@/lib/api";
import { STATE_METRICS, directionOf } from "@/lib/metrics";
import { MermaidDiagram } from "@/components/MermaidDiagram";
import { SystemDiagram } from "@/components/SystemDiagram";
import { TimelineView } from "@/components/TimelineView";

/**
 * docs/LEARNING_DEEPENING_PLAN.md L12 — renders the typed content blocks a concept, failure pattern
 * or design guide carries (backend learning/ContentBlocks.kt). Numbers come already computed by
 * the rule engine; nothing here invents a value.
 */
export function ContentBlocks({ blocks }: { blocks: ContentBlock[] }) {
  return (
    <div className="flex flex-col gap-5">
      {blocks.map((block, i) => (
        <Block key={i} block={block} />
      ))}
    </div>
  );
}

function Block({ block }: { block: ContentBlock }) {
  switch (block.type) {
    case "text":
      return <RichText body={block.body} />;
    case "diagram":
      return (
        <figure className="flex flex-col gap-2">
          <DiagramFrame mermaid={block.mermaid} alt={block.alt} />
          {block.caption && <figcaption className="text-xs text-foreground-muted">{block.caption}</figcaption>}
        </figure>
      );
    case "steps":
      return (
        <section className="flex flex-col gap-2">
          {block.title && <h3 className="text-sm font-semibold">{block.title}</h3>}
          <ol className="flex flex-col gap-3">
            {block.items.map((item, i) => (
              <li key={i} className="grid grid-cols-[1.75rem_1fr] gap-2">
                <span className="flex h-7 w-7 items-center justify-center rounded-full border border-accent text-xs font-semibold text-accent">{i + 1}</span>
                <div className="pt-0.5">
                  <p className="text-sm font-medium">{item.title}</p>
                  {item.body && <p className="mt-0.5 text-sm text-foreground-muted">{inline(item.body)}</p>}
                </div>
              </li>
            ))}
          </ol>
        </section>
      );
    case "callout":
      return <Callout tone={block.tone} title={block.title} body={block.body} />;
    case "compare":
      return (
        <figure className="flex flex-col gap-2">
          <div className="grid gap-3 md:grid-cols-2">
            <ComparePaneView pane={block.before} tone="before" />
            <ComparePaneView pane={block.after} tone="after" />
          </div>
          {block.caption && <figcaption className="text-xs text-foreground-muted">{block.caption}</figcaption>}
        </figure>
      );
    case "numbers":
      return <NumbersView block={block} />;
    case "system":
      return block.state ? <SystemDiagram domain={block.domain} state={block.state} caption={block.caption} /> : null;
    case "timeline":
      return <TimelineView block={block} />;
  }
}

/** Mermaid with its alt text for screen readers — the SVG itself is decorative to assistive tech. */
function DiagramFrame({ mermaid, alt }: { mermaid: string; alt: string }) {
  return (
    <div className="rounded-lg border border-border bg-background p-3" role="img" aria-label={alt}>
      <div aria-hidden>
        <MermaidDiagram code={mermaid} />
      </div>
    </div>
  );
}

function ComparePaneView({ pane, tone }: { pane: ComparePane; tone: "before" | "after" }) {
  return (
    <div className={`flex flex-col gap-2 rounded-lg border p-3 ${tone === "before" ? "border-danger/40" : "border-success/40"}`}>
      <p className={`text-xs font-semibold ${tone === "before" ? "text-danger" : "text-success"}`}>{pane.label}</p>
      <div role="img" aria-label={pane.alt}>
        <div aria-hidden>
          <MermaidDiagram code={pane.mermaid} />
        </div>
      </div>
      {pane.body && <p className="text-sm text-foreground-muted">{inline(pane.body)}</p>}
    </div>
  );
}

const CALLOUT_STYLE: Record<"tip" | "warning" | "tradeoff", { border: string; label: string; text: string }> = {
  tip: { border: "border-accent", label: "팁", text: "text-accent" },
  warning: { border: "border-warning", label: "주의", text: "text-warning" },
  tradeoff: { border: "border-foreground-muted", label: "트레이드오프", text: "text-foreground-muted" },
};

function Callout({ tone, title, body }: { tone: "tip" | "warning" | "tradeoff"; title: string; body: string }) {
  const style = CALLOUT_STYLE[tone] ?? CALLOUT_STYLE.tip;
  return (
    <aside className={`rounded-r-lg border-l-4 bg-surface px-4 py-3 ${style.border}`}>
      <p className={`text-xs font-semibold uppercase tracking-wide ${style.text}`}>{style.label}</p>
      {title && <p className="mt-1 text-sm font-medium">{title}</p>}
      <div className="mt-1 text-sm text-foreground-muted">
        <RichText body={body} />
      </div>
    </aside>
  );
}

const ARROW = { UP: "↑", SAME: "≈", DOWN: "↓" } as const;

function NumbersView({ block }: { block: Extract<ContentBlock, { type: "numbers" }> }) {
  if (!block.resolved) return null;
  return (
    <section className="flex flex-col gap-2 rounded-lg border border-border p-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        {block.title && <h3 className="text-sm font-semibold">{block.title}</h3>}
        <p className="text-xs text-foreground-muted">
          변경: <span className="font-medium text-foreground">{block.changeLabel}</span>
          {block.incident ? " · 장애 상황" : " · 평시"}
        </p>
      </div>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs text-foreground-muted">
            <th className="py-1 font-normal">지표</th>
            <th className="py-1 text-right font-normal">전</th>
            <th className="w-8" aria-hidden />
            <th className="py-1 text-right font-normal">후</th>
          </tr>
        </thead>
        <tbody>
          {block.metrics.map((metric) => {
            const pair = block.resolved?.[metric];
            if (!pair) return null;
            const meta = STATE_METRICS[metric as keyof SystemState];
            const fmt = meta?.format ?? ((v: number) => v.toFixed(2));
            const dir = directionOf(pair.before, pair.after);
            return (
              <tr key={metric} className="border-t border-border">
                <td className="py-1.5">{meta?.label ?? metric}</td>
                <td className="py-1.5 text-right tabular-nums text-foreground-muted">{fmt(pair.before)}</td>
                <td className="py-1.5 text-center text-foreground-muted" aria-label={{ UP: "증가", SAME: "변화 없음", DOWN: "감소" }[dir]}>
                  {ARROW[dir]}
                </td>
                <td className="py-1.5 text-right font-medium tabular-nums">{fmt(pair.after)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <p className="text-[11px] text-foreground-muted">수치는 이 페이지를 열 때 시뮬레이션 엔진이 계산한 값입니다 — Drill·랩과 같은 수식입니다.</p>
    </section>
  );
}

/** Paragraphs (blank-line separated), "- " bullet runs, **bold** and `code`. Nothing else is interpreted. */
export function RichText({ body }: { body: string }) {
  const paragraphs = body.split(/\n\s*\n/);
  return (
    <div className="flex flex-col gap-2 text-sm leading-relaxed">
      {paragraphs.map((para, i) => {
        const lines = para.split("\n");
        if (lines.every((l) => l.trimStart().startsWith("- "))) {
          return (
            <ul key={i} className="list-disc space-y-1 pl-5">
              {lines.map((l, j) => (
                <li key={j}>{inline(l.trimStart().slice(2))}</li>
              ))}
            </ul>
          );
        }
        return (
          <p key={i}>
            {lines.map((l, j) => (
              <Fragment key={j}>
                {j > 0 && <br />}
                {inline(l)}
              </Fragment>
            ))}
          </p>
        );
      })}
    </div>
  );
}

function inline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g).map((part, i) => {
    if (part.startsWith("**") && part.endsWith("**")) return <strong key={i} className="font-semibold text-foreground">{part.slice(2, -2)}</strong>;
    if (part.startsWith("`") && part.endsWith("`")) return <code key={i} className="rounded bg-background px-1 py-0.5 text-[0.85em]">{part.slice(1, -1)}</code>;
    return <Fragment key={i}>{part}</Fragment>;
  });
}
