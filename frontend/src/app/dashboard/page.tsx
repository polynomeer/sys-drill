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
import { getStoredNickname, getStoredToken } from "@/lib/localSession";
import { riskLabel } from "@/lib/riskLabels";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { LoadingState } from "@/components/ui/LoadingState";

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
  IMPROVING: { text: "▲ 상승", className: "text-success" },
  DECLINING: { text: "▼ 하락", className: "text-danger" },
  STABLE: { text: "▬ 안정", className: "text-foreground-muted" },
  INSUFFICIENT_DATA: { text: "", className: "text-foreground-muted" },
};

/** SysDrill_UIUX_Design_Plan.docx §5.1 — the three Drill modes are
 * descriptive cards linking into Drills' type tabs, not independently
 * browsable content (System Design and Incident share the same underlying
 * scenario list; see marketplace/page.tsx). */
const MODE_CARDS = [
  { type: "design", label: "System Design Drill", desc: "대규모 시스템을 설계하고 확장 시나리오에 대응하세요.", badge: "accent" as const },
  { type: "build", label: "Build Drill", desc: "실제 컴포넌트를 구현하고 서비스에 배포하세요.", badge: "success" as const },
  { type: "incident", label: "Incident Drill", desc: "장애 상황을 분석하고 실제처럼 복구하세요.", badge: "danger" as const },
];

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

  const domainCount = new Set(scenarios.map((s) => s.domain)).size;
  const summaryColumnCount = [topWeaknesses.length > 0, (skillProfile?.trend.length ?? 0) > 0, sessions.length > 0].filter(Boolean).length;

  return (
    <div className="flex flex-col gap-10 p-8">
      {/* Hero — SysDrill_UIUX_Design_Plan.docx §5.1 */}
      <section className="mx-auto flex w-full max-w-5xl flex-col items-start justify-between gap-6 rounded-xl border border-border bg-surface p-8 md:flex-row md:items-center">
        <div className="flex flex-col gap-3">
          <h1 className="text-3xl font-semibold leading-tight">
            {nickname ? `${nickname}님, ` : ""}
            실전에서 더 강해지는
            <br />
            시스템 엔지니어링 훈련 플랫폼
          </h1>
          <p className="text-sm text-foreground-muted">
            설계하고, 만들어보고, 무너뜨리고, 다시 복구하세요.
            <br />
            실제와 같은 시나리오로 성장하는 실전형 학습 경험, SysDrill.
          </p>
          <Button href="#drills" className="mt-2 self-start">
            지금 시작하기 →
          </Button>
        </div>
        <p className="shrink-0 text-right font-mono text-lg font-semibold leading-tight text-accent">
          Train.
          <br />
          Break.
          <br />
          Fix.
          <br />
          Repeat.
        </p>
      </section>

      <section className="mx-auto grid w-full max-w-5xl gap-4 sm:grid-cols-3">
        {MODE_CARDS.map((mode) => (
          <Link key={mode.type} href="/marketplace">
            <Card className="h-full">
              <Badge variant={mode.badge}>{mode.label.replace(" Drill", "")}</Badge>
              <p className="mt-2 font-medium">{mode.label}</p>
              <p className="mt-1 text-xs text-foreground-muted">{mode.desc}</p>
            </Card>
          </Link>
        ))}
      </section>

      <section className="mx-auto flex w-full max-w-5xl flex-wrap gap-3 text-xs text-foreground-muted">
        <span className="rounded-full border border-border px-3 py-1">{scenarios.length}개 시나리오</span>
        <span className="rounded-full border border-border px-3 py-1">{domainCount}개 도메인</span>
        <span className="rounded-full border border-border px-3 py-1">AI 상세 피드백</span>
        <span className="rounded-full border border-border px-3 py-1">실무 기반 워게임 시뮬레이션</span>
      </section>

      <div className="mx-auto flex w-full max-w-5xl flex-col gap-6" id="drills">
        <Link
          href="/bridge"
          className="flex items-center justify-between rounded-xl border border-accent/40 bg-accent/5 p-4"
        >
          <div>
            <p className="font-medium">Bridge Mode — Build부터 Wargame까지 한 번에</p>
            <p className="text-xs text-foreground-muted">Rate Limiter 구현 → 선착순 쿠폰 설계 → 꼬리설계 → 장애 대응까지 이어서 훈련합니다.</p>
          </div>
          <span className="shrink-0 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-accent-foreground">시작</span>
        </Link>

        {error && <p className="text-sm text-danger">{error}</p>}

        {!loading && summaryColumnCount > 0 && (
          <div className={`grid gap-4 ${summaryColumnCount >= 3 ? "sm:grid-cols-3" : "sm:grid-cols-2"}`}>
            {topWeaknesses.length > 0 && (
              <Card as="section">
                <h2 className="mb-2 text-sm font-semibold text-foreground-muted">내 약점 TOP 3</h2>
                <ul className="space-y-1 text-sm">
                  {topWeaknesses.map(([key, count]) => (
                    <li key={key} className="flex justify-between">
                      <span>{riskLabel(key)}</span>
                      <span className="text-foreground-muted">{count}회</span>
                    </li>
                  ))}
                </ul>
              </Card>
            )}

            {(skillProfile?.trend.length ?? 0) > 0 && (
              <Card as="section">
                <div className="mb-2 flex items-center justify-between">
                  <h2 className="text-sm font-semibold text-foreground-muted">점수 추이</h2>
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
                      className="w-4 rounded-t bg-accent/70"
                      style={{ height: `${Math.max(4, score / 2)}px` }}
                    />
                  ))}
                </div>
                <p className="mt-1 text-xs text-foreground-muted">누적 {skillProfile!.trend.length}회 · 최신 {skillProfile!.trend.at(-1)}점</p>
              </Card>
            )}

            {sessions.length > 0 && (
              <Card as="section">
                <h2 className="mb-2 text-sm font-semibold text-foreground-muted">최근 진행</h2>
                <ul className="space-y-2 text-sm">
                  {sessions.slice(0, 5).map((session) => (
                    <li key={session.id} className="flex items-center justify-between">
                      <span>{session.scenarioTitle}</span>
                      <span className="flex items-center gap-2">
                        <Badge>{STATUS_LABELS[session.status] ?? session.status}</Badge>
                        {session.status === "COMPLETED" ? (
                          <Button href={`/report/${session.id}`} variant="ghost" size="sm">
                            리포트 보기
                          </Button>
                        ) : (
                          <Button href={`/design/${session.id}`} variant="ghost" size="sm">
                            이어하기
                          </Button>
                        )}
                      </span>
                    </li>
                  ))}
                </ul>
              </Card>
            )}
          </div>
        )}

        {loading && <LoadingState />}

        <label className="flex items-start gap-2 text-sm text-foreground-muted">
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
            <Card
              as="li"
              key={scenario.id}
              className={`flex items-center justify-between ${scenario.id === recommendedScenario?.id ? "border-accent/50 bg-accent/5" : ""}`}
            >
              <div>
                <p className="font-medium">
                  {scenario.title}
                  {scenario.id === recommendedScenario?.id && (
                    <Badge variant="accent" className="ml-2 align-middle">
                      추천
                    </Badge>
                  )}
                </p>
                <p className="text-xs text-foreground-muted">
                  {scenario.domain}
                  {scenario.difficulty ? ` · ${scenario.difficulty}` : ""}
                </p>
              </div>
              <Button variant="secondary" onClick={() => handleStart(scenario.id)} disabled={startingId === scenario.id}>
                {startingId === scenario.id ? "시작하는 중..." : "시작"}
              </Button>
            </Card>
          ))}
        </ul>

        {!loading && scenarios.length === 0 && !error && <EmptyState message="아직 등록된 시나리오가 없습니다." />}
      </div>
    </div>
  );
}
