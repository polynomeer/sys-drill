"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ApiError,
  EvaluationFeedback,
  Benchmark,
  Report,
  ScenarioSummary,
  getFeedback,
  getBenchmark,
  getReport,
  getSession,
  getClarifications,
  Clarifications,
  getEstimation,
  Estimation,
  getSkillProfile,
  listScenarios,
  startSession,
} from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { BridgeProgress } from "@/components/BridgeProgress";
import { FeedbackDetail } from "@/components/FeedbackDetail";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { BenchmarkRow } from "@/components/BenchmarkRow";
import { CompletionCard } from "@/components/CompletionCard";
import { ShareWriteupCard } from "@/components/ShareWriteupCard";
import { DiscussionPanel } from "@/components/DiscussionPanel";
import { formatDuration } from "@/lib/metrics";
import { Gauge } from "@/components/ui/Gauge";
import { LoadingState } from "@/components/ui/LoadingState";
import { useConceptLookup } from "@/lib/useConceptLabels";
import { EstimateResultRow } from "@/components/Estimates";
import { trackEvent } from "@/lib/events";

const PHASE_LABELS: Record<string, string> = {
  INITIAL: "초기 설계",
  FOLLOWUP: "꼬리설계",
  INCIDENT: "장애 대응 회고",
};

function scoreStatus(score: number): "success" | "warning" | "danger" {
  if (score >= 70) return "success";
  if (score >= 40) return "warning";
  return "danger";
}

export default function ReportPage() {
  const params = useParams<{ sessionId: string }>();
  const router = useRouter();
  const sessionId = params.sessionId;

  const [report, setReport] = useState<Report | null>(null);
  const [benchmark, setBenchmark] = useState<Benchmark | null>(null);
  const [feedbackBySubmission, setFeedbackBySubmission] = useState<Record<string, EvaluationFeedback>>({});
  const [recommended, setRecommended] = useState<ScenarioSummary | null>(null);
  const [startingRecommended, setStartingRecommended] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const concepts = useConceptLookup();
  const [scenarioId, setScenarioId] = useState<string | null>(null);
  const [clarifications, setClarifications] = useState<Clarifications | null>(null);
  const [estimation, setEstimation] = useState<Estimation | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }

    trackEvent("report_view");
    // docs/COMMUNITY_EXPANSION_PLAN.md C7 — 리포트 하단의 토론. 부가 정보라 실패해도 본문은 뜬다.
    getSession(sessionId).then((s) => setScenarioId(s.scenarioId ?? null)).catch(() => setScenarioId(null));
    // docs/DRILLS_EXPANSION_PLAN.md M1 — revealed only now that asking is over.
    getClarifications(sessionId).then(setClarifications).catch(() => setClarifications(null));
    getEstimation(sessionId).then(setEstimation).catch(() => setEstimation(null));
    // 벤치마크는 부가 정보다 — 실패해도 리포트 본문은 떠야 한다.
    getBenchmark(sessionId).then(setBenchmark).catch(() => setBenchmark(null));

    getReport(sessionId)
      .then(async (r) => {
        setReport(r);

        // Report.timelineFeedback only carries the summary fields — the full
        // rubric/strengths/weaknesses/risk breakdown lives behind the same
        // getFeedback(submissionId) the live session view already calls, so
        // this is purely additive UI, no new backend endpoint.
        const entries = await Promise.all(
          r.timelineFeedback.map(async (entry) => {
            try {
              return [entry.submissionId, await getFeedback(entry.submissionId)] as const;
            } catch {
              return null;
            }
          }),
        );
        setFeedbackBySubmission(
          Object.fromEntries(entries.filter((e): e is readonly [string, EvaluationFeedback] => e !== null)),
        );

        try {
          const [skillProfile, scenarios] = await Promise.all([getSkillProfile(), listScenarios()]);
          if (skillProfile.recommendedDomain) {
            setRecommended(scenarios.find((s) => s.domain === skillProfile.recommendedDomain) ?? null);
          }
        } catch {
          // the recommendation is a nice-to-have — the report itself still renders without it
        }
      })
      .catch((err) =>
        setError(
          err instanceof ApiError && err.status === 404
            ? "이 세션은 아직 완료되지 않아 리포트가 없습니다."
            : "리포트를 불러오지 못했습니다.",
        ),
      )
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  async function handleStartRecommended() {
    if (!recommended) return;
    setStartingRecommended(true);
    try {
      const session = await startSession(recommended.id);
      router.push(`/design/${session.id}`);
    } catch {
      setStartingRecommended(false);
    }
  }

  // The gauge and the summary sentence next to it must quote the same
  // number: the server-computed session average (the figure certification
  // also uses), not the last phase's score. Per-phase scores are listed in
  // 단계별 결과 below.
  const headlineScore = report?.averageScore ?? null;

  return (
    <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-6 p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">세션 리포트</h1>
        <div className="flex items-center gap-3">
          <Link href={`/design/${sessionId}/replay`} className="text-sm underline">
            인시던트 리플레이
          </Link>
          <Link href={`/design/${sessionId}/postmortem`} className="text-sm underline">
            포스트모템
          </Link>
        </div>
      </div>

      {report?.buildSummary && (
        <div className="flex justify-end">
          <BridgeProgress current="report" />
        </div>
      )}

      {loading && <LoadingState />}
      {error && <p className="text-sm text-danger">{error}</p>}

      {report && (
        <>
          <CompletionCard sessionId={sessionId} averageScore={report.averageScore} />

          <Card className="flex flex-wrap items-center gap-6">
            {headlineScore !== null && (
              <Gauge label="평균 점수" value={headlineScore / 100} status={scoreStatus(headlineScore)} size={120} />
            )}
            <div className="min-w-0 flex-1">
              <p className="text-sm text-foreground-muted">총평</p>
              <p className="text-sm">{report.summary ?? "-"}</p>
            </div>
          </Card>

          {benchmark && (
            <Card as="section">
              <div className="mb-1 flex items-baseline justify-between">
                <h2 className="text-sm font-semibold text-foreground-muted">커뮤니티 비교</h2>
                <span className="text-xs text-foreground-muted">같은 시나리오 완료 {benchmark.sampleSize}명</span>
              </div>
              <div className="divide-y divide-border">
                <BenchmarkRow label="세션 평균 점수" metric={benchmark.score} format={(v) => `${v}점`} />
                <BenchmarkRow label="MTTD (최초 대응까지)" metric={benchmark.mttdSeconds} format={formatDuration} />
                <BenchmarkRow label="MTTR (마지막 조치까지)" metric={benchmark.mttrSeconds} format={formatDuration} />
              </div>
            </Card>
          )}

          {report.buildSummary && (
            <Card as="section">
              <h2 className="mb-1 text-sm font-semibold text-foreground-muted">Build — {report.buildSummary.challengeTitle}</h2>
              <p className="text-2xl font-semibold">
                {report.buildSummary.score ?? 0} / {report.buildSummary.totalStages}
              </p>
            </Card>
          )}

          <Card as="section">
            <h2 className="mb-3 text-sm font-semibold text-foreground-muted">단계별 결과</h2>
            <ul className="flex flex-col gap-4">
              {report.timelineFeedback.map((entry) => (
                <li key={entry.submissionId} className="border-t border-border pt-4 first:border-t-0 first:pt-0">
                  <div className="mb-2 flex items-center justify-between">
                    <span className="flex items-center gap-2 text-sm font-medium">
                      {PHASE_LABELS[entry.phase] ?? entry.phase}
                      {entry.onTime === false && <Badge variant="danger">시간 초과</Badge>}
                      {entry.onTime === true && <Badge variant="success">시간 내 제출</Badge>}
                    </span>
                    <span className="font-mono text-sm">{entry.totalScore ?? "-"} / 100</span>
                  </div>
                  {feedbackBySubmission[entry.submissionId] ? (
                    <FeedbackDetail feedback={feedbackBySubmission[entry.submissionId]} />
                  ) : (
                    entry.topRisks.length > 0 && (
                      <ul className="list-inside list-disc text-xs text-foreground-muted">
                        {entry.topRisks.map((risk, i) => (
                          <li key={i}>{risk}</li>
                        ))}
                      </ul>
                    )
                  )}
                </li>
              ))}
            </ul>
          </Card>

          {clarifications?.available && <RequirementsDiscovery data={clarifications} />}

          {estimation?.results && (
            <Card as="section">
              <div className="mb-1 flex items-baseline justify-between">
                <h2 className="text-sm font-semibold text-foreground-muted">규모 추정</h2>
                <span className="font-mono text-sm">
                  적중 {estimation.results.filter((r) => r.onTarget).length} / {estimation.results.length}
                </span>
              </div>
              <p className="mb-1 text-xs text-foreground-muted">실제 값의 0.5~2배 안이면 적중입니다.</p>
              <ul>
                {estimation.results.map((r, i) => (
                  <EstimateResultRow key={r.key} label={estimation.fields[i]?.label ?? r.key} unit={estimation.fields[i]?.unit ?? ""} result={r} />
                ))}
              </ul>
              <Link href="/learning/labs" className="mt-2 inline-block text-xs text-accent hover:underline">
                Capacity Lab에서 규모 추정 연습하기 →
              </Link>
            </Card>
          )}

          <MissedConcepts
            riskKeys={Object.values(feedbackBySubmission).flatMap((f) => f.riskFlags.map((flag) => flag.riskKey))}
            isConcept={concepts.isConcept}
            label={concepts.label}
          />

          {report.improvementGuide.length > 0 && (
            <Card as="section">
              <h2 className="mb-2 text-sm font-semibold text-foreground-muted">다음에 시도해볼 것</h2>
              <ul className="list-inside list-disc space-y-1 text-sm">
                {report.improvementGuide.map((item, i) => (
                  <li key={i}>{item}</li>
                ))}
              </ul>
            </Card>
          )}

          <ShareWriteupCard sessionId={sessionId} />

          {scenarioId && <DiscussionPanel scenarioId={scenarioId} compact />}

          {recommended && (
            <Card className="flex items-center justify-between">
              <div>
                <p className="text-sm text-foreground-muted">다음 추천 Drill</p>
                <p className="font-medium">{recommended.title}</p>
              </div>
              <Button onClick={handleStartRecommended} disabled={startingRecommended} size="sm">
                {startingRecommended ? "시작하는 중..." : "시작"}
              </Button>
            </Card>
          )}
        </>
      )}
    </div>
  );
}

/**
 * docs/LEARNING_EXPANSION_PLAN.md L4 — 이번 세션에서 지적받은 개념을 한곳에 모은다.
 * 같은 개념이 여러 단계에서 지적되면 한 번만, 지적 횟수를 함께 보여준다.
 */
function MissedConcepts({
  riskKeys,
  isConcept,
  label,
}: {
  riskKeys: string[];
  isConcept: (riskKey: string) => boolean;
  label: (riskKey: string) => string;
}) {
  const counts = new Map<string, number>();
  riskKeys.filter(isConcept).forEach((key) => counts.set(key, (counts.get(key) ?? 0) + 1));
  if (counts.size === 0) return null;
  return (
    <Card as="section">
      <h2 className="mb-1 text-sm font-semibold text-foreground-muted">이번에 놓친 개념</h2>
      <p className="mb-3 text-xs text-foreground-muted">개념 문서에서 왜 문제인지, 어떤 신호로 드러나는지 확인하고 다시 도전해 보세요.</p>
      <div className="flex flex-wrap gap-2">
        {[...counts.entries()].map(([key, count]) => (
          <Link
            key={key}
            href={`/learning/${key}`}
            onClick={() => trackEvent("feedback_concept_click")}
            className="rounded-full border border-border px-3 py-1 text-sm hover:border-accent hover:text-accent"
          >
            {label(key)}
            {count > 1 && <span className="ml-1 text-xs text-foreground-muted">×{count}</span>}
          </Link>
        ))}
      </div>
    </Card>
  );
}

/**
 * docs/DRILLS_EXPANSION_PLAN.md M1 (PLAN.md Round E8) — which of the deliberately hidden
 * requirements the learner asked about. Unrelated questions aren't penalised or counted
 * against them; the point is the critical ones that went unasked.
 */
function RequirementsDiscovery({ data }: { data: Clarifications }) {
  const missed = data.questions.filter((q) => q.critical && !q.asked);
  return (
    <Card as="section">
      <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground-muted">요구사항 확인</h2>
        {data.criticalTotal !== null && (
          <span className="font-mono text-sm">
            핵심 질문 {data.criticalAsked} / {data.criticalTotal}
          </span>
        )}
      </div>
      {missed.length === 0 ? (
        <p className="text-sm text-success">설계 전에 핵심 요구사항을 모두 확인했습니다.</p>
      ) : (
        <>
          <p className="mb-2 text-xs text-foreground-muted">묻지 않아서 모른 채 설계한 요구사항:</p>
          <ul className="flex flex-col gap-2 text-sm">
            {missed.map((q) => (
              <li key={q.id} className="rounded-lg border border-warning/40 px-3 py-2">
                <p className="text-xs text-foreground-muted">Q. {q.question}</p>
                <p className="mt-0.5">⚠ {q.answer}</p>
              </li>
            ))}
          </ul>
        </>
      )}
      <p className="mt-3 text-xs text-foreground-muted">
        확인한 질문 {data.questions.filter((q) => q.asked).length}개 · 전체 {data.questions.length}개
      </p>
    </Card>
  );
}
