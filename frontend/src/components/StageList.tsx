export type StageStatus = "done" | "current" | "upcoming";

export interface Stage {
  key: string;
  title: string;
  description?: string;
  status?: StageStatus;
}

const DOT_CLASSES: Record<StageStatus, string> = {
  done: "border-success bg-success",
  current: "border-accent bg-accent/30",
  upcoming: "border-border bg-transparent",
};

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.2 — the vertical stage roadmap
 * (status dot + numbered title + one-line description), borrowed from
 * CodeCrafters' stage list. Presentational only: callers decide each
 * stage's status, so the same list serves the Drill overview (all
 * "upcoming") and in-session views.
 */
export function StageList({ stages }: { stages: Stage[] }) {
  return (
    <ol className="flex flex-col">
      {stages.map((stage, i) => {
        const status = stage.status ?? "upcoming";
        return (
          <li key={stage.key} className="flex gap-3">
            <div className="flex flex-col items-center">
              <span className={`mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full border-2 ${DOT_CLASSES[status]}`} aria-hidden />
              {i < stages.length - 1 && <span className="w-px flex-1 bg-border" aria-hidden />}
            </div>
            <div className="pb-4">
              <p className={`text-sm font-medium ${status === "upcoming" ? "text-foreground" : status === "current" ? "text-accent" : "text-foreground-muted"}`}>
                <span className="mr-2 font-mono text-xs text-foreground-muted">{String(i + 1).padStart(2, "0")}</span>
                {stage.title}
              </p>
              {stage.description && <p className="mt-0.5 text-xs text-foreground-muted">{stage.description}</p>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
