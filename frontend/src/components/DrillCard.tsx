import Link from "next/link";
import type { ScenarioSummary } from "@/lib/api";
import { DOMAIN_TITLES } from "@/lib/designGuidance";
import { DomainIcon } from "@/lib/domainIcons";
import { Badge } from "@/components/ui/Badge";
import { DifficultyBadge } from "@/components/ui/DifficultyBadge";

export function hasIncident(scenario: ScenarioSummary): boolean {
  return (scenario.stepTypes ?? []).includes("INCIDENT");
}

/** docs/CODECRAFTERS_BENCHMARK.md §3.5 — catalog card, shared by Drills and the domain track pages. */
export function DrillCard({ scenario }: { scenario: ScenarioSummary }) {
  const stepCount = scenario.stepTypes?.length ?? 0;
  const completed = scenario.completedCount ?? 0;
  const domainTitle = DOMAIN_TITLES[scenario.domain];
  return (
    <Link
      href={`/drills/${scenario.id}`}
      data-card
      className="group flex h-full flex-col gap-3 rounded-xl border border-border bg-surface p-5 transition-colors hover:border-accent/40 focus-visible:border-accent focus-visible:outline-none"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-semibold group-hover:text-accent">{scenario.title}</p>
          <p className="mt-1 truncate text-xs text-foreground-muted">
            {scenario.creatorNickname ? `by ${scenario.creatorNickname}` : domainTitle && domainTitle !== scenario.title ? domainTitle : "공식 Drill"}
          </p>
        </div>
        <DomainIcon domain={scenario.domain} className="h-5 w-5 shrink-0 text-foreground-muted" />
      </div>
      <div className="flex flex-wrap items-center gap-1.5">
        <Badge variant="accent">Design</Badge>
        {hasIncident(scenario) && <Badge variant="danger">Incident</Badge>}
      </div>
      <div className="mt-auto flex items-center justify-between gap-3 border-t border-border pt-3 text-xs text-foreground-muted">
        <span>
          {stepCount > 0 ? `${stepCount}단계` : ""}
          {stepCount > 0 && " · "}
          {completed > 0
            ? `완료 ${completed}명${typeof scenario.averageScore === "number" ? ` · 평균 ${scenario.averageScore}점` : ""}`
            : "아직 완료자 없음"}
        </span>
        <DifficultyBadge difficulty={scenario.difficulty} />
      </div>
    </Link>
  );
}
