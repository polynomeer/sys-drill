"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ApiError,
  ScenarioDetail,
  SessionSummary,
  getScenario,
  getUserSessions,
  startSession,
} from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { DESIGN_GUIDANCE_BY_DOMAIN, DOMAIN_TITLES } from "@/lib/designGuidance";
import { completedTiers, needsPrereq } from "@/lib/drillPrereq";
import { DomainIcon } from "@/lib/domainIcons";
import { StageList, type Stage } from "@/components/StageList";
import { REPORT_STAGE, stageFromStepType } from "@/lib/stageCopy";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { DifficultyBadge } from "@/components/ui/DifficultyBadge";
import { LoadingState } from "@/components/ui/LoadingState";

interface BaseRequirements {
  functional?: unknown;
  nonFunctional?: unknown;
}

/** baseRequirements is free-form JSON (official seeds use {functional: string[], nonFunctional: {...}}); render only what matches that shape. */
function readRequirements(raw: unknown): { functional: string[]; nonFunctional: [string, string][] } {
  const req = (raw && typeof raw === "object" ? raw : {}) as BaseRequirements;
  const functional = Array.isArray(req.functional) ? req.functional.filter((f): f is string => typeof f === "string") : [];
  const nonFunctional =
    req.nonFunctional && typeof req.nonFunctional === "object"
      ? Object.entries(req.nonFunctional as Record<string, unknown>).map(
          ([k, v]) => [k, typeof v === "number" ? v.toLocaleString() : typeof v === "boolean" ? (v ? "예" : "아니오") : String(v)] as [string, string],
        )
      : [];
  return { functional, nonFunctional };
}

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.1 — the Drill overview page. Lists no
 * longer create a session on click; they link here, and the single CTA on
 * this page is the only place a session starts. Public like GET /scenarios:
 * logged-out visitors see the whole page, and the CTA sends them to sign up.
 */
export default function DrillOverviewPage() {
  const params = useParams<{ scenarioId: string }>();
  const router = useRouter();
  const scenarioId = params.scenarioId;

  const [scenario, setScenario] = useState<ScenarioDetail | null>(null);
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [loggedIn, setLoggedIn] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);
  const [interviewMode, setInterviewMode] = useState(false);

  useEffect(() => {
    const hasToken = !!getStoredToken();
    // One-time sync from localStorage on mount, same as dashboard/page.tsx.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoggedIn(hasToken);

    // Sessions only feed the advisory prerequisite hint — logged out, there are none to check.
    const sessionsRequest = hasToken ? getUserSessions() : Promise.resolve([] as SessionSummary[]);

    Promise.all([getScenario(scenarioId), sessionsRequest])
      .then(([detail, sessionList]) => {
        setScenario(detail);
        setSessions(sessionList);
      })
      .catch((err) =>
        setError(err instanceof ApiError && err.status === 404 ? "존재하지 않는 Drill입니다." : "Drill 정보를 불러오지 못했습니다."),
      )
      .finally(() => setLoading(false));
  }, [scenarioId]);

  async function handleStart() {
    if (!getStoredToken()) {
      router.push("/onboarding");
      return;
    }
    setStarting(true);
    setError(null);
    try {
      const session = await startSession(scenarioId, undefined, undefined, interviewMode);
      router.push(`/design/${session.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "세션을 시작하지 못했습니다.");
      setStarting(false);
    }
  }

  if (loading) return <LoadingState className="p-8" />;

  if (!scenario) {
    return (
      <div className="mx-auto flex max-w-5xl flex-col gap-4 p-8">
        <Alert variant="danger">{error ?? "Drill 정보를 불러오지 못했습니다."}</Alert>
        <Button href="/marketplace" variant="secondary" className="self-start">
          Drill 목록으로
        </Button>
      </div>
    );
  }

  const steps = (scenario.steps ?? []).slice().sort((a, b) => a.order - b.order);
  const hasIncident = steps.some((s) => s.type === "INCIDENT");
  const stages: Stage[] = [
    ...steps.map((step) => stageFromStepType(step.type, `${step.order}-${step.type}`)),
    REPORT_STAGE,
  ];
  const { functional, nonFunctional } = readRequirements(scenario.baseRequirements);
  const guidance = DESIGN_GUIDANCE_BY_DOMAIN[scenario.domain] ?? [];
  // Official scenarios' domain title usually *is* the scenario title — don't print it twice.
  const domainTitle = DOMAIN_TITLES[scenario.domain] ?? scenario.domain;
  const domainLabel = domainTitle !== scenario.title ? `${domainTitle} · ` : "";
  const prereq = loggedIn && needsPrereq(scenario.difficulty, completedTiers(sessions));

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-8 p-8">
      <header className="flex flex-col gap-4 border-b border-border pb-8">
        {/* Official scenarios are listed on Home; the Drills tab lists only user-published ones. */}
        <Link
          href={scenario.creatorNickname ? "/marketplace" : "/dashboard"}
          className="text-xs text-foreground-muted hover:text-foreground"
        >
          ← {scenario.creatorNickname ? "Drills" : "Home"}
        </Link>
        <div className="flex items-center gap-3">
          <DomainIcon domain={scenario.domain} className="h-8 w-8 shrink-0 text-accent" />
          <h1 className="break-keep text-3xl font-semibold leading-tight md:text-4xl">{scenario.title}</h1>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="accent">Design</Badge>
          {hasIncident && <Badge variant="danger">Incident</Badge>}
          <DifficultyBadge difficulty={scenario.difficulty} />
          <span className="text-xs text-foreground-muted">
            {domainLabel}{stages.length - 1}단계
            {scenario.creatorNickname && ` · by ${scenario.creatorNickname}`}
          </span>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <Button onClick={handleStart} disabled={starting} className="self-start px-6 py-2.5 text-base">
            {starting ? "시작하는 중..." : loggedIn ? "시작하기 →" : "가입하고 시작하기 →"}
          </Button>
          {loggedIn && (
            <label className="flex items-center gap-2 text-xs text-foreground-muted">
              <input type="checkbox" checked={interviewMode} onChange={(e) => setInterviewMode(e.target.checked)} />
              면접형 타이머 모드 — 단계마다 제한 시간, 시간이 다 되면 자동 제출
            </label>
          )}
        </div>
        {error && <p className="text-sm text-danger">{error}</p>}
      </header>

      <div className="grid gap-8 lg:grid-cols-[1fr_280px]">
        <div className="flex min-w-0 flex-col gap-8">
          {scenario.initialPrompt && (
            <section className="flex flex-col gap-3">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground-muted">문제</h2>
              <p className="leading-relaxed">{scenario.initialPrompt}</p>
              {(functional.length > 0 || nonFunctional.length > 0) && (
                <div className="grid gap-4 rounded-xl border border-border bg-surface p-4 text-sm sm:grid-cols-2">
                  {functional.length > 0 && (
                    <div>
                      <p className="mb-1 text-xs text-foreground-muted">기능 요구사항</p>
                      <ul className="list-disc space-y-0.5 pl-4">
                        {functional.map((f) => (
                          <li key={f}>{f}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {nonFunctional.length > 0 && (
                    <div>
                      <p className="mb-1 text-xs text-foreground-muted">비기능 요구사항</p>
                      <dl className="space-y-0.5">
                        {nonFunctional.map(([k, v]) => (
                          <div key={k} className="flex justify-between gap-3">
                            <dt className="font-mono text-xs text-foreground-muted">{k}</dt>
                            <dd className="text-right">{v}</dd>
                          </div>
                        ))}
                      </dl>
                    </div>
                  )}
                </div>
              )}
            </section>
          )}

          <section className="flex flex-col divide-y divide-border border-y border-border">
            {guidance.length > 0 && (
              <details className="group py-3">
                <summary className="cursor-pointer list-none font-medium marker:hidden">
                  <span className="mr-2 inline-block text-foreground-muted transition-transform group-open:rotate-90">›</span>
                  무엇을 평가받나요?
                </summary>
                <ul className="mt-2 list-disc space-y-1 pl-9 text-sm text-foreground-muted">
                  {guidance.map((g) => (
                    <li key={g}>{g}</li>
                  ))}
                </ul>
              </details>
            )}
            <details className="group py-3">
              <summary className="cursor-pointer list-none font-medium marker:hidden">
                <span className="mr-2 inline-block text-foreground-muted transition-transform group-open:rotate-90">›</span>
                어떻게 진행되나요?
              </summary>
              <p className="mt-2 pl-5 text-sm text-foreground-muted">
                단계마다 답안을 제출하면 AI가 7개 루브릭 항목으로 채점하고 잘한 점·놓친 점·꼬리질문을 돌려줍니다. 피드백을
                확인한 뒤 다음 단계로 넘어가며, 다음 단계의 조건은 그때 공개됩니다.
                {hasIncident && " 마지막 장애 대응 단계는 실시간 시뮬레이션에서 대응 액션을 직접 실행합니다."}
              </p>
            </details>
            <details className="group py-3">
              <summary className="cursor-pointer list-none font-medium marker:hidden">
                <span className="mr-2 inline-block text-foreground-muted transition-transform group-open:rotate-90">›</span>
                선수 지식이 필요한가요?
              </summary>
              <p className="mt-2 pl-5 text-sm text-foreground-muted">
                {prereq
                  ? "아직 더 쉬운 난이도의 Drill을 완료하지 않았습니다. 막히지 않으려면 쉬운 난이도부터 풀어보는 것을 추천합니다 — 강제는 아닙니다."
                  : "백엔드 API·데이터베이스·캐시의 기본 개념이면 충분합니다. 부족한 개념은 피드백의 '놓친 점'과 Learning 탭에서 채울 수 있습니다."}
              </p>
            </details>
          </section>

          <section className="flex flex-col gap-3">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground-muted">단계</h2>
            <StageList stages={stages} />
          </section>
        </div>

        <aside className="flex flex-col gap-4">
          <Card>
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground-muted">완료 현황</p>
            {(scenario.completedCount ?? 0) > 0 ? (
              <div className="mt-2 flex gap-6">
                <div>
                  <p className="text-2xl font-semibold">{scenario.completedCount}</p>
                  <p className="text-xs text-foreground-muted">완료</p>
                </div>
                {typeof scenario.averageScore === "number" && (
                  <div>
                    <p className="text-2xl font-semibold">{scenario.averageScore}</p>
                    <p className="text-xs text-foreground-muted">평균 점수</p>
                  </div>
                )}
              </div>
            ) : (
              <p className="mt-2 text-sm text-foreground-muted">아직 완료한 사람이 없습니다. 첫 번째가 되어보세요.</p>
            )}
          </Card>
        </aside>
      </div>
    </div>
  );
}
