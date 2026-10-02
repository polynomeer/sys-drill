"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  AdminDashboardStats,
  ApiError,
  ReportedDiscussion,
  ReportedWriteupComment,
  getReportedWriteupComments,
  setWriteupCommentHidden,
  SuccessMetrics,
  getAdminDashboardStats,
  getReportedDiscussions,
  getSuccessMetrics,
  setDiscussionHidden,
} from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { getStoredToken } from "@/lib/localSession";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

/** docs/COMMERCIALIZATION.md — no separate role check on the frontend; the backend's PlatformAccessGuard is the real gate, this page just renders whatever it returns (stats, or a 403 message). */
export default function AdminDashboardPage() {
  const router = useRouter();
  const [stats, setStats] = useState<AdminDashboardStats | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [metrics, setMetrics] = useState<SuccessMetrics | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    // Secondary panel — the page still works if this one fails.
    getSuccessMetrics().then(setMetrics).catch(() => setMetrics(null));
    getAdminDashboardStats()
      .then(setStats)
      .catch((err) =>
        setError(
          err instanceof ApiError && err.status === 403
            ? "관리자 권한이 필요합니다."
            : "대시보드를 불러오지 못했습니다.",
        ),
      )
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [router]);

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">운영 대시보드</h1>
      </div>

      {loading && <LoadingState />}
      {error && <p className="text-sm text-danger">{error}</p>}

      {stats && (
        <div className="grid grid-cols-2 gap-4">
          <StatCard label="총 사용자" value={stats.totalUsers} />
          <StatCard label="오늘 신규 가입" value={stats.newUsersToday} />
          <StatCard label="총 조직 수" value={stats.totalOrganizations} />
          <StatCard label="오늘 완료된 세션" value={stats.sessionsCompletedToday} />
        </div>
      )}

      {metrics && <SuccessMetricsPanel metrics={metrics} />}

      {stats && <ReportedDiscussionsPanel />}
      {stats && <ReportedWriteupCommentsPanel />}
    </div>
  );
}

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C7 (PLAN.md Round E3) — 신고된 토론 검토.
 * 1차 기획이 "토론은 모더레이션 운영을 시작시킨다"고 했는데 API만 있고 화면이 없었다.
 * 숨김은 삭제가 아니라 되돌릴 수 있다(ADR-0040).
 */
function ReportedDiscussionsPanel() {
  const [items, setItems] = useState<ReportedDiscussion[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getReportedDiscussions()
      .then(setItems)
      .catch(() => setError("신고 목록을 불러오지 못했습니다."));
  }, []);

  async function toggle(item: ReportedDiscussion) {
    try {
      const updated = await setDiscussionHidden(item.id, !item.hidden);
      setItems((prev) => prev?.map((i) => (i.id === updated.id ? updated : i)) ?? null);
    } catch {
      setError("처리하지 못했습니다.");
    }
  }

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold">신고된 토론</h2>
      {error && <p className="text-sm text-danger">{error}</p>}
      {items === null && !error && <LoadingState />}
      {items?.length === 0 && <p className="text-sm text-foreground-muted">검토할 신고가 없습니다.</p>}
      {items?.map((item) => (
        <Card key={item.id} className={item.hidden ? "opacity-60" : ""}>
          <div className="mb-1 flex flex-wrap items-baseline gap-2 text-xs text-foreground-muted">
            <span className="font-medium text-foreground">{item.authorNickname}</span>
            <span>신고 {item.reportCount}건</span>
            {item.scenarioId && (
              <Link href={`/discussions/${item.scenarioId}`} className="underline underline-offset-2">
                {item.scenarioTitle ?? "스레드"}
              </Link>
            )}
            {item.hidden && <span className="text-danger">숨김</span>}
          </div>
          <p className="whitespace-pre-wrap text-sm">{item.body}</p>
          <Button size="sm" variant={item.hidden ? "secondary" : "danger"} className="mt-3" onClick={() => toggle(item)}>
            {item.hidden ? "복원" : "숨기기"}
          </Button>
        </Card>
      ))}
    </section>
  );
}

/** PLAN.md Round E22 (C10) — reported writeup reviews, the same review → hide flow as discussions. */
function ReportedWriteupCommentsPanel() {
  const [items, setItems] = useState<ReportedWriteupComment[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getReportedWriteupComments()
      .then(setItems)
      .catch(() => setError("신고 목록을 불러오지 못했습니다."));
  }, []);

  async function toggle(item: ReportedWriteupComment) {
    try {
      const updated = await setWriteupCommentHidden(item.id, !item.hidden);
      setItems((prev) => prev?.map((i) => (i.id === updated.id ? updated : i)) ?? null);
    } catch {
      setError("처리하지 못했습니다.");
    }
  }

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold">신고된 풀이 리뷰</h2>
      {error && <p className="text-sm text-danger">{error}</p>}
      {items === null && !error && <LoadingState />}
      {items?.length === 0 && <p className="text-sm text-foreground-muted">검토할 신고가 없습니다.</p>}
      {items?.map((item) => (
        <Card key={item.id} className={item.hidden ? "opacity-60" : ""}>
          <div className="mb-1 flex flex-wrap items-baseline gap-2 text-xs text-foreground-muted">
            <span className="font-medium text-foreground">{item.authorNickname}</span>
            <span>신고 {item.reportCount}건</span>
            {item.anchorLabel && <span>@ {item.anchorLabel}</span>}
            {item.scenarioId && (
              <Link href={`/writeups/${item.scenarioId}/${item.sessionId}`} className="underline underline-offset-2">
                풀이
              </Link>
            )}
            {item.hidden && <span className="text-danger">숨김</span>}
          </div>
          <p className="whitespace-pre-wrap text-sm">{item.body}</p>
          <Button size="sm" variant={item.hidden ? "secondary" : "danger"} className="mt-3" onClick={() => toggle(item)}>
            {item.hidden ? "복원" : "숨기기"}
          </Button>
        </Card>
      ))}
    </section>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <Card>
      <p className="text-sm text-foreground-muted">{label}</p>
      <p className="text-3xl font-semibold">{value}</p>
    </Card>
  );
}

const EVENT_LABELS: Record<string, string> = {
  drill_overview_view: "Drill 개요 조회",
  drill_overview_start: "개요에서 시작",
  certifications_view: "인증 페이지 방문",
  organizations_view: "조직 페이지 방문",
  architecture_analysis_view: "아키텍처 분석 방문",
  report_view: "리포트 조회",
  feedback_concept_click: "피드백 → 개념 클릭",
  observe_tab_map: "인시던트 Service Map 탭",
  observe_tab_alerts: "인시던트 Alerts 탭",
  observe_tab_metrics: "인시던트 Metrics 탭",
  observe_tab_logs: "인시던트 Logs 탭",
  observe_tab_changes: "인시던트 Changes 탭",
};

/** docs/CODECRAFTERS_BENCHMARK.md §6 — the success metrics, aggregates only. */
function SuccessMetricsPanel({ metrics }: { metrics: SuccessMetrics }) {
  const pct = (v: number | null) => (v === null ? "—" : `${v}%`);
  const distribution = Object.entries(metrics.buildProgressDistribution).sort(([a], [b]) => Number(a) - Number(b));
  const maxCount = Math.max(1, ...distribution.map(([, n]) => n));
  return (
    <section className="flex flex-col gap-4">
      <h2 className="text-lg font-semibold">성공 지표</h2>
      <div className="grid grid-cols-2 gap-4">
        <Card>
          <p className="text-sm text-foreground-muted">7일 내 첫 Drill 완료율</p>
          <p className="text-2xl font-semibold">{pct(metrics.firstDrillWithin7DaysPercent)}</p>
          <p className="text-xs text-foreground-muted">가입 7~90일 전 사용자 {metrics.cohortSize}명 기준</p>
        </Card>
        <Card>
          <p className="text-sm text-foreground-muted">Build 1단계 첫 통과까지</p>
          <p className="text-2xl font-semibold">
            {metrics.medianMinutesToFirstBuildPass === null ? "—" : `${metrics.medianMinutesToFirstBuildPass}분`}
          </p>
          <p className="text-xs text-foreground-muted">첫 제출부터, 중앙값</p>
        </Card>
        <Card>
          <p className="text-sm text-foreground-muted">개요 → 시작 전환율 (30일)</p>
          <p className="text-2xl font-semibold">{pct(metrics.overviewToStartPercent)}</p>
        </Card>
        <Card>
          <p className="mb-1 text-sm text-foreground-muted">페이지 이벤트 (30일)</p>
          <ul className="text-xs text-foreground-muted">
            {Object.entries(metrics.events30d).map(([name, n]) => (
              <li key={name} className="flex justify-between">
                <span>{EVENT_LABELS[name] ?? name}</span>
                <span className="font-mono text-foreground">{n}</span>
              </li>
            ))}
          </ul>
        </Card>
      </div>
      <Card>
        <p className="mb-2 text-sm text-foreground-muted">Build 진행 분포 — 최근 채점 기준, 순서대로 통과한 마지막 단계</p>
        {distribution.length === 0 ? (
          <p className="text-sm text-foreground-muted">아직 채점된 Build 제출이 없습니다.</p>
        ) : (
          <ul className="flex flex-col gap-1.5 text-xs">
            {distribution.map(([stage, n]) => (
              <li key={stage} className="flex items-center gap-2">
                <span className="w-16 shrink-0 text-foreground-muted">{stage === "0" ? "1단계 전" : `${stage}단계`}</span>
                <span className="h-2 rounded bg-accent/70" style={{ width: `${(n / maxCount) * 100}%` }} />
                <span className="font-mono">{n}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>
      <p className="text-xs text-foreground-muted">
        페이지 이벤트는 날짜·이벤트 이름별 카운터만 저장합니다 — 누가 조회했는지는 기록하지 않습니다.
      </p>
    </section>
  );
}

