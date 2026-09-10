"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { PolarAngleAxis, PolarGrid, Radar, RadarChart, ResponsiveContainer } from "recharts";
import {
  ApiError,
  CertificationStatus,
  PostmortemSummary,
  ScenarioSummary,
  SessionSummary,
  SkillProfile,
  getMyCertification,
  getPostmortemSummary,
  getSkillProfile,
  getUserSessions,
  listScenarios,
  startSession,
} from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { DOMAIN_TITLES } from "@/lib/designGuidance";
import { riskLabel } from "@/lib/riskLabels";
import { categoryLabel } from "@/lib/skillCategoryLabels";
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

// MTTD/MTTR IMPROVING means the duration got shorter, the opposite direction of TREND_DIRECTION_LABELS'
// "▲ 상승" (which means a score went up) — reusing that text as-is would read backwards for a time metric.
const TIME_TREND_LABELS: Record<string, { text: string; className: string }> = {
  IMPROVING: { text: "▼ 단축", className: "text-success" },
  DECLINING: { text: "▲ 증가", className: "text-danger" },
  STABLE: { text: "▬ 안정", className: "text-foreground-muted" },
  INSUFFICIENT_DATA: { text: "", className: "text-foreground-muted" },
};

function formatSeconds(seconds: number | null): string {
  if (seconds === null) return "—";
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return m > 0 ? `${m}분 ${s}초` : `${s}초`;
}

/**
 * SysDrill_UIUX_Design_Plan.docx §4 — 진행률/역량 프로필/기록/배지/추천 학습
 * 경로 5개 구성요소. 전부 이미 존재하는 엔드포인트(대시보드/인증 페이지가
 * 각각 부분적으로 이미 쓰던 것)로 채운다 — 새 백엔드 작업 없음.
 */
export default function ProfilePage() {
  const router = useRouter();
  const [skillProfile, setSkillProfile] = useState<SkillProfile | null>(null);
  const [certification, setCertification] = useState<CertificationStatus | null>(null);
  const [postmortemSummary, setPostmortemSummary] = useState<PostmortemSummary | null>(null);
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [recommended, setRecommended] = useState<ScenarioSummary | null>(null);
  const [startingRecommended, setStartingRecommended] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    Promise.all([getSkillProfile(), getMyCertification(), getUserSessions(), listScenarios(), getPostmortemSummary()])
      .then(([profile, cert, sessionList, scenarios, postmortem]) => {
        setSkillProfile(profile);
        setCertification(cert);
        setSessions(sessionList);
        setPostmortemSummary(postmortem);
        if (profile.recommendedDomain) {
          setRecommended(scenarios.find((s) => s.domain === profile.recommendedDomain) ?? null);
        }
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "프로필 정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [router]);

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

  if (loading) return <LoadingState className="p-8" />;
  if (error) return <p className="p-8 text-sm text-danger">{error}</p>;

  const passedCount = certification?.domains.filter((d) => d.passed).length ?? 0;
  const totalDomains = certification?.domains.length ?? 0;
  const radarData = certification?.domains.map((d) => ({ domain: d.title, score: d.bestScore ?? 0 })) ?? [];
  // Skill Graph slice 1 — grouped by cross-domain competency category
  // (weaknessesByCategory) instead of a flat top-N list, so e.g. coupon's
  // MISSING_CONCURRENCY_CONTROL and reservation's MISSING_RESERVATION_LOCKING
  // show up together under "동시성·정합성" rather than as unrelated rows.
  const weaknessCategories = skillProfile
    ? Object.entries(skillProfile.weaknessesByCategory)
        .map(([category, riskKeys]) => ({
          category,
          total: Object.values(riskKeys).reduce((sum, count) => sum + count, 0),
          topRiskKeys: Object.entries(riskKeys).sort((a, b) => b[1] - a[1]).slice(0, 3),
        }))
        .sort((a, b) => b.total - a.total)
    : [];

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">역량 프로필</h1>
        <p className="mt-1 text-sm text-foreground-muted">진행률, 역량, 기록, 배지를 한눈에 확인하세요.</p>
      </div>

      <Card as="section">
        <div className="mb-3 flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-foreground-muted">진행률</h2>
            <p className="text-xs text-foreground-muted">
              {passedCount}/{totalDomains}개 도메인 완료
            </p>
          </div>
          <Badge variant={certification?.certified ? "success" : "neutral"}>
            {certification?.certified ? "SysDrill Certified" : "인증 진행 중"}
          </Badge>
        </div>
        <div className="mb-3 h-1.5 overflow-hidden rounded-full bg-surface-elevated">
          <div
            className="h-full rounded-full bg-accent transition-all"
            style={{ width: `${totalDomains ? (passedCount / totalDomains) * 100 : 0}%` }}
          />
        </div>
        <div className="flex flex-wrap gap-2">
          {certification?.domains.map((d) => (
            <Badge key={d.domain} variant={d.passed ? "success" : "neutral"}>
              {d.title}
              {d.passed ? " ✓" : ""}
            </Badge>
          ))}
        </div>
      </Card>

      {radarData.length > 0 && (
        <Card as="section">
          <h2 className="mb-2 text-sm font-semibold text-foreground-muted">역량 프로필</h2>
          <ResponsiveContainer width="100%" height={280}>
            <RadarChart data={radarData} outerRadius="60%" margin={{ top: 12, right: 32, bottom: 12, left: 32 }}>
              <PolarGrid stroke="var(--border)" />
              <PolarAngleAxis dataKey="domain" tick={{ fill: "var(--foreground-muted)", fontSize: 11 }} />
              <Radar dataKey="score" stroke="#2f80ff" fill="#2f80ff" fillOpacity={0.35} />
            </RadarChart>
          </ResponsiveContainer>
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
            {skillProfile!.trend.slice(-30).map((score, i) => (
              <div
                key={i}
                title={`${score}/100`}
                className="w-3 rounded-t bg-accent/70"
                style={{ height: `${Math.max(4, score / 2)}px` }}
              />
            ))}
          </div>
          <p className="mt-1 text-xs text-foreground-muted">
            누적 {skillProfile!.trend.length}회 · 최신 {skillProfile!.trend.at(-1)}점
          </p>
        </Card>
      )}

      {postmortemSummary && postmortemSummary.totalIncidents > 0 && (
        <Card as="section">
          <h2 className="mb-2 text-sm font-semibold text-foreground-muted">장애 대응 통계</h2>
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <div className="flex items-center justify-between">
                <span className="text-xs text-foreground-muted">평균 MTTD</span>
                {TIME_TREND_LABELS[postmortemSummary.mttdTrend].text && (
                  <span className={`text-xs font-medium ${TIME_TREND_LABELS[postmortemSummary.mttdTrend].className}`}>
                    {TIME_TREND_LABELS[postmortemSummary.mttdTrend].text}
                  </span>
                )}
              </div>
              <p className="font-semibold">{formatSeconds(postmortemSummary.avgMttdSeconds)}</p>
            </div>
            <div>
              <div className="flex items-center justify-between">
                <span className="text-xs text-foreground-muted">평균 MTTR</span>
                {TIME_TREND_LABELS[postmortemSummary.mttrTrend].text && (
                  <span className={`text-xs font-medium ${TIME_TREND_LABELS[postmortemSummary.mttrTrend].className}`}>
                    {TIME_TREND_LABELS[postmortemSummary.mttrTrend].text}
                  </span>
                )}
              </div>
              <p className="font-semibold">{formatSeconds(postmortemSummary.avgMttrSeconds)}</p>
            </div>
          </div>
          <ul className="mt-3 space-y-1 border-t border-border pt-3 text-sm">
            {postmortemSummary.byDomain.map((d) => (
              <li key={d.domain} className="flex justify-between">
                <span>
                  {DOMAIN_TITLES[d.domain] ?? d.domain} <span className="text-foreground-muted">({d.incidentCount}회)</span>
                </span>
                <span className="text-foreground-muted">
                  MTTD {formatSeconds(d.avgMttdSeconds)} · MTTR {formatSeconds(d.avgMttrSeconds)}
                </span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {weaknessCategories.length > 0 && (
        <Card as="section">
          <h2 className="mb-3 text-sm font-semibold text-foreground-muted">보완이 필요한 영역</h2>
          <div className="space-y-3">
            {weaknessCategories.map(({ category, total, topRiskKeys }) => (
              <div key={category}>
                <div className="flex justify-between text-sm font-medium">
                  <span>{categoryLabel(category)}</span>
                  <span className="text-foreground-muted">{total}회</span>
                </div>
                <ul className="mt-1 space-y-1 pl-3 text-sm text-foreground-muted">
                  {topRiskKeys.map(([key, count]) => (
                    <li key={key} className="flex justify-between">
                      <span>{riskLabel(key)}</span>
                      <span>{count}회</span>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </Card>
      )}

      {recommended && (
        <Card className="flex items-center justify-between">
          <div>
            <p className="text-sm text-foreground-muted">
              추천 학습 경로
              {skillProfile?.recommendedCategory && ` · 가장 약한 영역: ${categoryLabel(skillProfile.recommendedCategory)}`}
            </p>
            <p className="font-medium">{recommended.title}</p>
          </div>
          <Button onClick={handleStartRecommended} disabled={startingRecommended} size="sm">
            {startingRecommended ? "시작하는 중..." : "시작"}
          </Button>
        </Card>
      )}

      <Card as="section">
        <h2 className="mb-2 text-sm font-semibold text-foreground-muted">기록 ({sessions.length}건)</h2>
        {sessions.length === 0 ? (
          <EmptyState message="아직 진행한 시나리오가 없습니다." />
        ) : (
          <ul className="space-y-2 text-sm">
            {sessions.map((session) => (
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
        )}
      </Card>
    </div>
  );
}
