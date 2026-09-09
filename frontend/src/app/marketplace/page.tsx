"use client";

import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  ApiError,
  ScenarioSummary,
  listMarketplaceScenarios,
  listMyMarketplaceScenarios,
  publishMarketplaceScenario,
  startSession,
} from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Input, Textarea } from "@/components/ui/Input";
import { LoadingState } from "@/components/ui/LoadingState";

/** SysDrill_UIUX_Design_Plan.docx §5.2 — 전체/System Design/Build/Incident 탭.
 * "System Design"과 "Incident"는 한 세션의 서로 다른 단계일 뿐 실제로 분리된
 * 데이터가 없어 같은 scenarios 목록을 보여준다(가짜 필터를 만들지 않는다).
 * "Build"만 Bridge Mode의 단일 rate-limiter 챌린지를 카드 1개로 보여준다. */
type DrillType = "all" | "design" | "build" | "incident";

const TYPE_TABS: { type: DrillType; label: string }[] = [
  { type: "all", label: "전체" },
  { type: "design", label: "System Design" },
  { type: "build", label: "Build" },
  { type: "incident", label: "Incident" },
];

export default function MarketplacePage() {
  return (
    <Suspense fallback={<LoadingState className="p-8" />}>
      <MarketplaceContent />
    </Suspense>
  );
}

function MarketplaceContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [mine, setMine] = useState<ScenarioSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [startingScenarioId, setStartingScenarioId] = useState<string | null>(null);

  const [activeType, setActiveType] = useState<DrillType>("all");
  const [query, setQuery] = useState(searchParams.get("q") ?? "");
  const [difficultyFilter, setDifficultyFilter] = useState("");
  const [domainFilter, setDomainFilter] = useState("");

  const [title, setTitle] = useState("");
  const [domain, setDomain] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [initialPrompt, setInitialPrompt] = useState("");
  const [followupPrompt, setFollowupPrompt] = useState("");
  const [publishing, setPublishing] = useState(false);

  const load = useCallback(async () => {
    const [all, own] = await Promise.all([listMarketplaceScenarios(), listMyMarketplaceScenarios()]);
    setScenarios(all);
    setMine(own);
  }, []);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    load()
      .catch((err) => setError(err instanceof ApiError ? err.message : "마켓플레이스를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [router, load]);

  async function handlePublish(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim() || !domain.trim() || !initialPrompt.trim() || !followupPrompt.trim()) return;
    setPublishing(true);
    setError(null);
    try {
      await publishMarketplaceScenario({
        title: title.trim(),
        difficulty: difficulty.trim() || undefined,
        domain: domain.trim(),
        initialPrompt: initialPrompt.trim(),
        followupPrompt: followupPrompt.trim(),
      });
      setTitle("");
      setDomain("");
      setDifficulty("");
      setInitialPrompt("");
      setFollowupPrompt("");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "시나리오를 등록하지 못했습니다.");
    } finally {
      setPublishing(false);
    }
  }

  async function handleStart(scenarioId: string) {
    setStartingScenarioId(scenarioId);
    setError(null);
    try {
      const session = await startSession(scenarioId);
      router.push(`/design/${session.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "세션을 시작하지 못했습니다.");
      setStartingScenarioId(null);
    }
  }

  const difficulties = useMemo(
    () => Array.from(new Set(scenarios.map((s) => s.difficulty).filter((d): d is string => !!d))),
    [scenarios],
  );
  const domains = useMemo(() => Array.from(new Set(scenarios.map((s) => s.domain))), [scenarios]);

  const filteredScenarios = scenarios.filter((s) => {
    if (query.trim() && !s.title.toLowerCase().includes(query.trim().toLowerCase())) return false;
    if (difficultyFilter && s.difficulty !== difficultyFilter) return false;
    if (domainFilter && s.domain !== domainFilter) return false;
    return true;
  });

  if (loading) return <LoadingState className="p-8" />;

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">Drill 탐색</h1>
        <p className="mt-1 text-sm text-foreground-muted">실제 서비스에서 발생할 수 있는 다양한 상황을 경험하세요.</p>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <div className="flex flex-wrap items-center gap-2">
        {TYPE_TABS.map((tab) => (
          <button
            key={tab.type}
            onClick={() => setActiveType(tab.type)}
            className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
              activeType === tab.type ? "bg-accent text-accent-foreground" : "border border-border text-foreground-muted hover:text-foreground"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeType !== "build" && (
        <div className="flex flex-wrap gap-2">
          <Input
            className="flex-1"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="시나리오 검색..."
          />
          <select
            className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-foreground"
            value={difficultyFilter}
            onChange={(e) => setDifficultyFilter(e.target.value)}
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
          >
            <option value="">카테고리 전체</option>
            {domains.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
        </div>
      )}

      {activeType === "build" ? (
        <Card>
          <div className="flex items-center gap-2">
            <Badge variant="success">Build</Badge>
          </div>
          <p className="mt-2 font-medium">Rate Limiter 구현</p>
          <p className="mt-1 text-xs text-foreground-muted">
            Token Bucket 알고리즘으로 Rate Limiter를 구현하고 6개 stage 테스트를 통과하세요.
          </p>
          <Button href="/bridge" size="sm" className="mt-3">
            시작하기 →
          </Button>
        </Card>
      ) : (
        <Card as="section">
          <h2 className="mb-3 text-sm font-semibold text-foreground-muted">전체 시나리오 ({filteredScenarios.length}개)</h2>
          {filteredScenarios.length === 0 && <EmptyState message="조건에 맞는 시나리오가 없습니다." />}
          <ul className="flex flex-col gap-2">
            {filteredScenarios.map((scenario) => (
              <li key={scenario.id} className="flex items-center justify-between rounded-lg border border-border px-3 py-2 text-sm">
                <span className="flex items-center gap-2">
                  {scenario.title}
                  <Badge variant="accent">Design</Badge>
                  <Badge variant="danger">Incident</Badge>
                  {scenario.difficulty && <Badge>{scenario.difficulty}</Badge>}
                  {scenario.creatorNickname && <span className="text-xs text-foreground-muted">by {scenario.creatorNickname}</span>}
                </span>
                <Button size="sm" variant="secondary" onClick={() => handleStart(scenario.id)} disabled={startingScenarioId === scenario.id}>
                  {startingScenarioId === scenario.id ? "시작하는 중..." : "시작"}
                </Button>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">내가 등록한 시나리오 ({mine.length}개)</h2>
        {mine.length === 0 && <EmptyState message="아직 등록한 시나리오가 없습니다." />}
        <ul className="flex flex-col gap-2">
          {mine.map((scenario) => (
            <li key={scenario.id} className="flex items-center gap-2 text-sm">
              {scenario.title}
              {scenario.difficulty && <Badge>{scenario.difficulty}</Badge>}
            </li>
          ))}
        </ul>

        <form onSubmit={handlePublish} className="mt-4 flex flex-col gap-2 border-t border-border pt-4">
          <p className="text-xs text-foreground-muted">새 시나리오 등록 (설계 + 꼬리설계 2단계, 장애 대응 단계는 없습니다)</p>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목" />
          <div className="flex gap-2">
            <Input className="flex-1" value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="도메인 라벨 (예: community-rate-limit)" />
            <Input className="w-32" value={difficulty} onChange={(e) => setDifficulty(e.target.value)} placeholder="난이도" />
          </div>
          <Textarea value={initialPrompt} onChange={(e) => setInitialPrompt(e.target.value)} placeholder="초기 설계 프롬프트" rows={3} />
          <Textarea value={followupPrompt} onChange={(e) => setFollowupPrompt(e.target.value)} placeholder="꼬리설계 프롬프트" rows={3} />
          <Button type="submit" disabled={publishing} className="self-start">
            {publishing ? "등록하는 중..." : "등록하기"}
          </Button>
        </form>
      </Card>
    </div>
  );
}
