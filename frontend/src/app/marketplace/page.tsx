"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ApiError,
  ScenarioSummary,
  listMarketplaceScenarios,
  listMyMarketplaceScenarios,
  publishMarketplaceScenario,
  startSession,
} from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";

export default function MarketplacePage() {
  const router = useRouter();

  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [mine, setMine] = useState<ScenarioSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [startingScenarioId, setStartingScenarioId] = useState<string | null>(null);

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

  if (loading) return <p className="p-8 text-sm text-zinc-500">불러오는 중...</p>;

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <Link href="/dashboard" className="text-sm text-zinc-500 underline">
          대시보드로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">마켓플레이스</h1>
        <p className="mt-1 text-sm text-zinc-500">누구나 시나리오를 등록하고 플레이할 수 있습니다. 결제는 지원하지 않습니다.</p>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <h2 className="mb-3 text-sm font-semibold text-zinc-500">전체 시나리오 ({scenarios.length}개)</h2>
        {scenarios.length === 0 && <p className="text-sm text-zinc-500">아직 등록된 시나리오가 없습니다.</p>}
        <ul className="flex flex-col gap-2">
          {scenarios.map((scenario) => (
            <li key={scenario.id} className="flex items-center justify-between text-sm">
              <span>
                {scenario.title}
                {scenario.difficulty && <span className="ml-2 text-xs text-zinc-500">({scenario.difficulty})</span>}
                {scenario.creatorNickname && (
                  <span className="ml-2 text-xs text-zinc-500">by {scenario.creatorNickname}</span>
                )}
              </span>
              <button
                onClick={() => handleStart(scenario.id)}
                disabled={startingScenarioId === scenario.id}
                className="rounded bg-foreground px-3 py-1 text-xs font-medium text-background disabled:opacity-50"
              >
                {startingScenarioId === scenario.id ? "시작하는 중..." : "시작"}
              </button>
            </li>
          ))}
        </ul>
      </section>

      <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <h2 className="mb-3 text-sm font-semibold text-zinc-500">내가 등록한 시나리오 ({mine.length}개)</h2>
        {mine.length === 0 && <p className="text-sm text-zinc-500">아직 등록한 시나리오가 없습니다.</p>}
        <ul className="flex flex-col gap-2">
          {mine.map((scenario) => (
            <li key={scenario.id} className="text-sm">
              {scenario.title}
              {scenario.difficulty && <span className="ml-2 text-xs text-zinc-500">({scenario.difficulty})</span>}
            </li>
          ))}
        </ul>

        <form onSubmit={handlePublish} className="mt-4 flex flex-col gap-2 border-t border-zinc-200 pt-4 dark:border-zinc-800">
          <p className="text-xs text-zinc-500">새 시나리오 등록 (설계 + 꼬리설계 2단계, 장애 대응 단계는 없습니다)</p>
          <input
            className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="제목"
          />
          <div className="flex gap-2">
            <input
              className="flex-1 rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
              value={domain}
              onChange={(e) => setDomain(e.target.value)}
              placeholder="도메인 라벨 (예: community-rate-limit)"
            />
            <input
              className="w-32 rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
              value={difficulty}
              onChange={(e) => setDifficulty(e.target.value)}
              placeholder="난이도"
            />
          </div>
          <textarea
            className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
            value={initialPrompt}
            onChange={(e) => setInitialPrompt(e.target.value)}
            placeholder="초기 설계 프롬프트"
            rows={3}
          />
          <textarea
            className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900"
            value={followupPrompt}
            onChange={(e) => setFollowupPrompt(e.target.value)}
            placeholder="꼬리설계 프롬프트"
            rows={3}
          />
          <button
            type="submit"
            disabled={publishing}
            className="self-start rounded bg-foreground px-4 py-2 text-sm font-medium text-background disabled:opacity-50"
          >
            {publishing ? "등록하는 중..." : "등록하기"}
          </button>
        </form>
      </section>
    </div>
  );
}
