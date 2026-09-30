"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ApiError, CertificationStatus, UserActivity, getCertification, getUserActivity } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Badge } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

/** Public verification page — no login required, works for any visitor. */
export default function CertificationVerificationPage() {
  const params = useParams<{ userId: string }>();

  const [status, setStatus] = useState<CertificationStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activity, setActivity] = useState<UserActivity | null>(null);
  const [signedIn, setSignedIn] = useState(false);

  useEffect(() => {
    // docs/CODECRAFTERS_BENCHMARK.md §3.8 — the activity timeline is shown to signed-in members only;
    // anonymous visitors still get the public verification below.
    const hasToken = !!getStoredToken();
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setSignedIn(hasToken);
    if (hasToken) getUserActivity(params.userId).then(setActivity).catch(() => setActivity(null));
    getCertification(params.userId)
      .then(setStatus)
      .catch((err) => setError(err instanceof ApiError && err.status === 404 ? "존재하지 않는 사용자입니다." : "인증 현황을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [params.userId]);

  if (loading) return <LoadingState className="p-8" />;
  if (error || !status) return <p className="p-8 text-sm text-danger">{error ?? "인증 현황을 불러오지 못했습니다."}</p>;

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">{status.nickname}님의 SysDrill 인증 현황</h1>
        <p className="mt-1 text-sm text-foreground-muted">공개 검증 페이지 — 로그인 없이 누구나 확인할 수 있습니다.</p>
      </div>

      <Card as="section">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-foreground-muted">SysDrill Certified Incident Responder</h2>
          <Badge variant={status.certified ? "success" : "neutral"}>{status.certified ? "인증됨" : "미인증"}</Badge>
        </div>
        <ul className="flex flex-col gap-2">
          {status.domains.map((d) => (
            <li key={d.domain} className="flex items-center justify-between text-sm">
              <span className="flex items-center gap-2">
                {d.title}
                <span className="text-xs text-foreground-muted">({d.domain})</span>
              </span>
              <span className="flex items-center gap-2">
                <span className="text-xs text-foreground-muted">{d.bestScore !== null ? `최고 ${d.bestScore}점` : "미완료"}</span>
                <Badge variant={d.passed ? "success" : "neutral"}>{d.passed ? "완료" : "미완료"}</Badge>
              </span>
            </li>
          ))}
        </ul>
      </Card>

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">활동</h2>
        {!signedIn ? (
          <p className="text-sm text-foreground-muted">로그인한 회원에게만 활동 기록이 보입니다.</p>
        ) : !activity ? (
          <p className="text-sm text-foreground-muted">활동 기록을 불러오지 못했습니다.</p>
        ) : activity.hidden ? (
          <p className="text-sm text-foreground-muted">이 사용자는 활동을 공개하지 않습니다.</p>
        ) : activity.entries.length === 0 ? (
          <p className="text-sm text-foreground-muted">아직 완료한 Drill이 없습니다.</p>
        ) : (
          <ActivityTimeline activity={activity} />
        )}
      </Card>
    </div>
  );
}

/** Month-grouped completions, newest first (CodeCrafters profile timeline). */
function ActivityTimeline({ activity }: { activity: UserActivity }) {
  const months = new Map<string, UserActivity["entries"]>();
  for (const entry of activity.entries) {
    const d = new Date(entry.completedAt);
    const key = `${d.getFullYear()}년 ${d.getMonth() + 1}월`;
    months.set(key, [...(months.get(key) ?? []), entry]);
  }
  return (
    <div className="flex flex-col gap-4">
      {[...months.entries()].map(([month, entries]) => (
        <div key={month}>
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-foreground-muted">{month}</p>
          <ul className="flex flex-col gap-1.5 border-l border-border pl-4 text-sm">
            {entries.map((e, i) => (
              <li key={`${e.completedAt}-${i}`}>
                <span className="font-medium">{e.scenarioTitle}</span> Drill 완료
                <span className="ml-2 text-xs text-foreground-muted">{new Date(e.completedAt).toLocaleDateString("ko-KR")}</span>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}
