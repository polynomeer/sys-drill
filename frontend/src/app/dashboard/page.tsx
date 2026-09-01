"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ApiError,
  ScenarioSummary,
  SessionSummary,
  SkillProfile,
  getSkillProfile,
  getUserSessions,
  listScenarios,
  startSession,
} from "@/lib/api";
import { clearStoredUser, getStoredNickname, getStoredToken } from "@/lib/localSession";
import { riskLabel } from "@/lib/riskLabels";

const STATUS_LABELS: Record<string, string> = {
  IN_PROGRESS: "진행 중",
  SUBMITTED: "제출됨",
  EVALUATING: "평가 중",
  FEEDBACK_READY: "결과 확인 가능",
  EVALUATION_FAILED: "평가 실패",
  COMPLETED: "완료",
  ABANDONED: "중단됨",
};

const TREND_DIRECTION_LABELS: Record<string, { text: string; className: string }> = {
  IMPROVING: { text: "▲ 상승", className: "text-emerald-600 dark:text-emerald-400" },
  DECLINING: { text: "▼ 하락", className: "text-red-600 dark:text-red-400" },
  STABLE: { text: "▬ 안정", className: "text-zinc-500" },
  INSUFFICIENT_DATA: { text: "", className: "text-zinc-500" },
};

export default function DashboardPage() {
  const router = useRouter();
  const [nickname, setNickname] = useState<string | null>(null);
  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [skillProfile, setSkillProfile] = useState<SkillProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [startingId, setStartingId] = useState<string | null>(null);
  const [interviewMode, setInterviewMode] = useState(false);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    // One-time sync from localStorage (a real external system, not derived
    // React state) on mount, not a cascading render loop.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setNickname(getStoredNickname());

    Promise.all([listScenarios(), getUserSessions(), getSkillProfile()])
      .then(([scenarioList, sessionList, profile]) => {
        setScenarios(scenarioList);
        setSessions(sessionList);
        setSkillProfile(profile);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [router]);

  async function handleStart(scenarioId: string) {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    setStartingId(scenarioId);
    setError(null);
    try {
      const session = await startSession(scenarioId, undefined, undefined, interviewMode);
      router.push(`/design/${session.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "세션을 시작하지 못했습니다.");
      setStartingId(null);
    }
  }

  const topWeaknesses = skillProfile
    ? Object.values(skillProfile.weaknessesByDomain)
        .flatMap((domainWeaknesses) => Object.entries(domainWeaknesses))
        .sort((a, b) => b[1] - a[1])
        .slice(0, 3)
    : [];

  const recommendedScenario = skillProfile?.recommendedDomain
    ? scenarios.find((s) => s.domain === skillProfile.recommendedDomain)
    : undefined;
  const orderedScenarios = recommendedScenario
    ? [recommendedScenario, ...scenarios.filter((s) => s.id !== recommendedScenario.id)]
    : scenarios;

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-semibold">
            {nickname ? `${nickname}님, 오늘의 훈련을 시작해보세요` : "훈련 시나리오"}
          </h1>
          <p className="mt-1 text-sm text-zinc-500">시나리오를 선택하면 바로 설계를 시작합니다.</p>
        </div>
        <button
          onClick={() => {
            clearStoredUser();
            router.replace("/login");
          }}
          className="text-sm text-zinc-500 underline"
        >
          로그아웃
        </button>
      </div>

      <Link
        href="/bridge"
        className="flex items-center justify-between rounded border border-zinc-300 bg-zinc-50 p-4 dark:border-zinc-700 dark:bg-zinc-900"
      >
        <div>
          <p className="font-medium">Bridge Mode — Build부터 Wargame까지 한 번에</p>
          <p className="text-xs text-zinc-500">Rate Limiter 구현 → 선착순 쿠폰 설계 → 꼬리설계 → 장애 대응까지 이어서 훈련합니다.</p>
        </div>
        <span className="shrink-0 rounded bg-foreground px-4 py-2 text-sm font-medium text-background">시작</span>
      </Link>

      {error && <p className="text-sm text-red-600">{error}</p>}

      {!loading && (topWeaknesses.length > 0 || (skillProfile?.trend.length ?? 0) > 0) && (
        <div className="grid gap-4 sm:grid-cols-2">
          {topWeaknesses.length > 0 && (
            <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
              <h2 className="mb-2 text-sm font-semibold text-zinc-500">내 약점 TOP 3</h2>
              <ul className="space-y-1 text-sm">
                {topWeaknesses.map(([key, count]) => (
                  <li key={key} className="flex justify-between">
                    <span>{riskLabel(key)}</span>
                    <span className="text-zinc-500">{count}회</span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {(skillProfile?.trend.length ?? 0) > 0 && (
            <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
              <div className="mb-2 flex items-center justify-between">
                <h2 className="text-sm font-semibold text-zinc-500">점수 추이</h2>
                {skillProfile && TREND_DIRECTION_LABELS[skillProfile.trendDirection].text && (
                  <span className={`text-xs font-medium ${TREND_DIRECTION_LABELS[skillProfile.trendDirection].className}`}>
                    {TREND_DIRECTION_LABELS[skillProfile.trendDirection].text}
                  </span>
                )}
              </div>
              <div className="flex items-end gap-1.5" style={{ height: 48 }}>
                {skillProfile!.trend.slice(-20).map((score, i) => (
                  <div
                    key={i}
                    title={`${score}/100`}
                    className="w-4 rounded-t bg-foreground/70"
                    style={{ height: `${Math.max(4, score / 2)}px` }}
                  />
                ))}
              </div>
              <p className="mt-1 text-xs text-zinc-500">누적 {skillProfile!.trend.length}회 · 최신 {skillProfile!.trend.at(-1)}점</p>
            </section>
          )}
        </div>
      )}

      {!loading && sessions.length > 0 && (
        <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
          <h2 className="mb-2 text-sm font-semibold text-zinc-500">최근 진행</h2>
          <ul className="space-y-2 text-sm">
            {sessions.slice(0, 5).map((session) => (
              <li key={session.id} className="flex items-center justify-between">
                <span>{session.scenarioTitle}</span>
                <span className="flex items-center gap-2">
                  <span className="text-xs text-zinc-500">{STATUS_LABELS[session.status] ?? session.status}</span>
                  {session.status === "COMPLETED" ? (
                    <Link href={`/report/${session.id}`} className="text-xs font-medium underline">
                      리포트 보기
                    </Link>
                  ) : (
                    <Link href={`/design/${session.id}`} className="text-xs font-medium underline">
                      이어하기
                    </Link>
                  )}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {loading && <p className="text-sm text-zinc-500">불러오는 중...</p>}

      <label className="flex items-start gap-2 text-sm text-zinc-600 dark:text-zinc-400">
        <input
          type="checkbox"
          checked={interviewMode}
          onChange={(e) => setInterviewMode(e.target.checked)}
          className="mt-0.5"
        />
        <span>
          면접형 타이머 모드로 시작 — 각 단계마다 제한 시간이 표시되고, 시간이 다 되면 현재까지 작성한 내용이 자동
          제출됩니다.
        </span>
      </label>

      <ul className="flex flex-col gap-3">
        {orderedScenarios.map((scenario) => (
          <li
            key={scenario.id}
            className={`flex items-center justify-between rounded border p-4 ${
              scenario.id === recommendedScenario?.id
                ? "border-foreground/40 bg-zinc-50 dark:bg-zinc-900"
                : "border-zinc-300 dark:border-zinc-700"
            }`}
          >
            <div>
              <p className="font-medium">
                {scenario.title}
                {scenario.id === recommendedScenario?.id && (
                  <span className="ml-2 rounded bg-foreground px-1.5 py-0.5 align-middle text-[10px] font-medium text-background">
                    추천
                  </span>
                )}
              </p>
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
