"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, ScenarioSummary, listScenarios, startSession } from "@/lib/api";
import { getStoredNickname, getStoredUserId } from "@/lib/localSession";

export default function DashboardPage() {
  const router = useRouter();
  const [nickname, setNickname] = useState<string | null>(null);
  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [startingId, setStartingId] = useState<string | null>(null);

  useEffect(() => {
    const userId = getStoredUserId();
    if (!userId) {
      router.replace("/onboarding");
      return;
    }
    // One-time sync from localStorage (a real external system, not derived
    // React state) on mount, not a cascading render loop.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setNickname(getStoredNickname());

    listScenarios()
      .then(setScenarios)
      .catch((err) => setError(err instanceof ApiError ? err.message : "시나리오를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [router]);

  async function handleStart(scenarioId: string) {
    const userId = getStoredUserId();
    if (!userId) {
      router.replace("/onboarding");
      return;
    }
    setStartingId(scenarioId);
    setError(null);
    try {
      const session = await startSession(userId, scenarioId);
      router.push(`/design/${session.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "세션을 시작하지 못했습니다.");
      setStartingId(null);
    }
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">
          {nickname ? `${nickname}님, 오늘의 훈련을 시작해보세요` : "훈련 시나리오"}
        </h1>
        <p className="mt-1 text-sm text-zinc-500">시나리오를 선택하면 바로 설계를 시작합니다.</p>
      </div>

      {loading && <p className="text-sm text-zinc-500">불러오는 중...</p>}
      {error && <p className="text-sm text-red-600">{error}</p>}

      <ul className="flex flex-col gap-3">
        {scenarios.map((scenario) => (
          <li
            key={scenario.id}
            className="flex items-center justify-between rounded border border-zinc-300 p-4 dark:border-zinc-700"
          >
            <div>
              <p className="font-medium">{scenario.title}</p>
              <p className="text-xs text-zinc-500">
                {scenario.domain}
                {scenario.difficulty ? ` · ${scenario.difficulty}` : ""}
              </p>
            </div>
            <button
              onClick={() => handleStart(scenario.id)}
              disabled={startingId === scenario.id}
              className="rounded bg-foreground px-4 py-2 text-sm font-medium text-background disabled:opacity-50"
            >
              {startingId === scenario.id ? "시작하는 중..." : "시작"}
            </button>
          </li>
        ))}
      </ul>

      {!loading && scenarios.length === 0 && !error && (
        <p className="text-sm text-zinc-500">아직 등록된 시나리오가 없습니다.</p>
      )}
    </div>
  );
}
