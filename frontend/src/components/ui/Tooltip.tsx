/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.9 — a CSS-only tooltip (hover and keyboard
 * focus). Short hints only; anything the user must read stays in the page.
 */
export function Tooltip({ content, children, className = "" }: { content: string; children: React.ReactNode; className?: string }) {
  return (
    <span className={`group relative inline-flex ${className}`}>
      {children}
      <span
        role="tooltip"
        className="pointer-events-none absolute bottom-full left-1/2 z-20 mb-1.5 w-max max-w-56 -translate-x-1/2 rounded-md border border-border bg-surface-elevated px-2 py-1 text-[11px] font-normal normal-case tracking-normal text-foreground opacity-0 shadow-lg transition-opacity group-focus-within:opacity-100 group-hover:opacity-100"
      >
        {content}
      </span>
    </span>
  );
}
