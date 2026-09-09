const STEPS = [
  { key: "build", label: "Build" },
  { key: "design", label: "Design" },
  { key: "wargame", label: "Wargame" },
  { key: "report", label: "Report" },
] as const;

export type BridgeStepKey = (typeof STEPS)[number]["key"];

/** Shown on every screen in a Bridge Mode flow (PLAN.md step 10) so the user always sees where they are in Build -> Design -> Wargame -> Report. */
export function BridgeProgress({ current }: { current: BridgeStepKey }) {
  const currentIndex = STEPS.findIndex((step) => step.key === current);

  return (
    <div className="flex items-center gap-2 rounded-lg border border-border px-3 py-2 text-xs">
      <span className="font-medium text-foreground-muted">Bridge Mode</span>
      <div className="flex items-center gap-1.5">
        {STEPS.map((step, i) => (
          <div key={step.key} className="flex items-center gap-1.5">
            <span
              className={
                i === currentIndex
                  ? "rounded bg-accent px-2 py-0.5 font-medium text-accent-foreground"
                  : i < currentIndex
                    ? "text-foreground-muted line-through"
                    : "text-foreground-muted"
              }
            >
              {step.label}
            </span>
            {i < STEPS.length - 1 && <span className="text-foreground-muted">&rarr;</span>}
          </div>
        ))}
      </div>
    </div>
  );
}
