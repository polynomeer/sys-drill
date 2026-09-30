"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Plus, Terminal } from "lucide-react";
import { ApiError, ScenarioSummary, listScenarios } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { DOMAIN_TITLES } from "@/lib/designGuidance";
import { DomainIcon } from "@/lib/domainIcons";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { DifficultyBadge } from "@/components/ui/DifficultyBadge";
import { EmptyState } from "@/components/ui/EmptyState";
import { Input } from "@/components/ui/Input";
import { LoadingState } from "@/components/ui/LoadingState";

/** Every scenario has design stages, so "System Design" is the whole pool;
 * "Incident" narrows to the ones whose published version actually has an
 * INCIDENT step (stepTypes from GET /scenarios — community scenarios have
 * none). "Build" is the single Bridge Mode rate-limiter challenge. */
type DrillType = "all" | "design" | "build" | "incident";
type Source = "all" | "official" | "community";

const TYPE_TABS: { type: DrillType; label: string }[] = [
  { type: "all", label: "전체" },
  { type: "design", label: "System Design" },
  { type: "build", label: "Build" },
  { type: "incident", label: "Incident" },
];

const SOURCE_FILTERS: { source: Source; label: string }[] = [
  { source: "all", label: "전체" },
  { source: "official", label: "공식" },
  { source: "community", label: "커뮤니티" },
];

export default function MarketplacePage() {
  return (
    <Suspense fallback={<LoadingState className="p-8" />}>
      <MarketplaceContent />
    </Suspense>
  );
}

function hasIncident(scenario: ScenarioSummary): boolean {
  return (scenario.stepTypes ?? []).includes("INCIDENT");
}

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.5 — the Drills catalog. Sourced from
 * GET /scenarios (every public scenario, official and community) rather than
 * the marketplace list, which only ever held community ones — official Drills
 * used to be missing from this tab entirely. Publishing moved to /drills/new.
 */
function MarketplaceContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [activeType, setActiveType] = useState<DrillType>("all");
  const [source, setSource] = useState<Source>("all");
  const [query, setQuery] = useState(searchParams.get("q") ?? "");
  const [difficultyFilter, setDifficultyFilter] = useState("");
  const [domainFilter, setDomainFilter] = useState("");

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }
    listScenarios()
      .then(setScenarios)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Drill 목록을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [router]);

  const difficulties = useMemo(
    () => Array.from(new Set(scenarios.map((s) => s.difficulty).filter((d): d is string => !!d))),
    [scenarios],
  );
  const domains = useMemo(() => Array.from(new Set(scenarios.map((s) => s.domain))), [scenarios]);

  const filtered = scenarios.filter((s) => {
    if (activeType === "incident" && !hasIncident(s)) return false;
    if (source === "official" && s.creatorNickname) return false;
    if (source === "community" && !s.creatorNickname) return false;
    if (query.trim() && !s.title.toLowerCase().includes(query.trim().toLowerCase())) return false;
    if (difficultyFilter && s.difficulty !== difficultyFilter) return false;
    if (domainFilter && s.domain !== domainFilter) return false;
    return true;
  });
  // Official first (they have the curated difficulty ladder), then by popularity.
  const ordered = filtered
    .slice()
    .sort((a, b) => Number(!!a.creatorNickname) - Number(!!b.creatorNickname) || (b.completedCount ?? 0) - (a.completedCount ?? 0));

  if (loading) return <LoadingState className="p-8" />;

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6 p-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Drill 탐색</h1>
          <p className="mt-1 text-sm text-foreground-muted">실제 서비스에서 발생할 수 있는 다양한 상황을 경험하세요.</p>
        </div>
        <Button href="/drills/new" variant="secondary" size="sm" className="gap-1">
          <Plus className="h-3.5 w-3.5" aria-hidden />
          시나리오 등록
        </Button>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <div className="flex flex-wrap items-center gap-2">
        {TYPE_TABS.map((tab) => (
          <button
            key={tab.type}
            onClick={() => setActiveType(tab.type)}
            aria-pressed={activeType === tab.type}
            className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
              activeType === tab.type ? "bg-accent text-accent-foreground" : "border border-border text-foreground-muted hover:text-foreground"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeType === "build" ? (
        <div className="grid gap-4 md:grid-cols-2">
          <Link href="/bridge" className="group flex flex-col gap-3 rounded-xl border border-border bg-surface p-5 transition-colors hover:border-accent/40">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-semibold group-hover:text-accent">Rate Limiter 구현</p>
                <p className="mt-1 text-sm text-foreground-muted">
                  fixed window부터 동시성·분산 스토어·fail-open/closed·운영 metric까지 단계별 테스트를 통과하세요.
                </p>
              </div>
              <Terminal className="h-5 w-5 shrink-0 text-foreground-muted" aria-hidden strokeWidth={1.75} />
            </div>
            <div className="mt-auto flex items-center gap-3 text-xs text-foreground-muted">
              <Badge variant="success">Build</Badge>
              <span>6단계 · Python / TypeScript</span>
            </div>
          </Link>
        </div>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <Input className="min-w-48 flex-1" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Drill 검색..." />
            <select
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-foreground"
              value={difficultyFilter}
              onChange={(e) => setDifficultyFilter(e.target.value)}
              aria-label="난이도"
            >
              <option value="">난이도 전체</option>
              {difficulties.map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
            </select>
            <select
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-foreground"
              value={domainFilter}
              onChange={(e) => setDomainFilter(e.target.value)}
              aria-label="도메인"
            >
              <option value="">도메인 전체</option>
              {domains.map((d) => (
                <option key={d} value={d}>
                  {DOMAIN_TITLES[d] ?? d}
                </option>
              ))}
            </select>
            <div className="flex rounded-lg border border-border p-0.5 text-xs">
              {SOURCE_FILTERS.map((f) => (
                <button
                  key={f.source}
                  onClick={() => setSource(f.source)}
                  aria-pressed={source === f.source}
                  className={`rounded-md px-2.5 py-1.5 ${source === f.source ? "bg-surface-elevated text-foreground" : "text-foreground-muted"}`}
                >
                  {f.label}
                </button>
              ))}
            </div>
          </div>

          <p className="text-xs text-foreground-muted">{ordered.length}개 Drill</p>

          {ordered.length === 0 ? (
            <EmptyState message="조건에 맞는 Drill이 없습니다." />
          ) : (
            <ul className="grid gap-4 md:grid-cols-2">
              {ordered.map((scenario) => (
                <li key={scenario.id}>
                  <DrillCard scenario={scenario} />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}

function DrillCard({ scenario }: { scenario: ScenarioSummary }) {
  const stepCount = scenario.stepTypes?.length ?? 0;
  const completed = scenario.completedCount ?? 0;
  const domainTitle = DOMAIN_TITLES[scenario.domain];
  return (
    <Link
      href={`/drills/${scenario.id}`}
      className="group flex h-full flex-col gap-3 rounded-xl border border-border bg-surface p-5 transition-colors hover:border-accent/40"
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
