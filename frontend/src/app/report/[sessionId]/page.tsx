"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ApiError, Report, getReport } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { BridgeProgress } from "@/components/BridgeProgress";
import { Badge } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

const PHASE_LABELS: Record<string, string> = {
  INITIAL: "초기 설계",
  FOLLOWUP: "꼬리설계",
  INCIDENT: "장애 대응 회고",
};

export default function ReportPage() {
  const params = useParams<{ sessionId: string }>();
  const router = useRouter();
  const sessionId = params.sessionId;

  const [report, setReport] = useState<Report | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }

    getReport(sessionId)
      .then(setReport)
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
          {report.buildSummary && (
            <Card as="section">
              <h2 className="mb-1 text-sm font-semibold text-foreground-muted">Build — {report.buildSummary.challengeTitle}</h2>
              <p className="text-2xl font-semibold">
                {report.buildSummary.score ?? 0} / {report.buildSummary.totalStages}
              </p>
            </Card>
          )}

          <Card as="section">
            <h2 className="mb-1 text-sm font-semibold text-foreground-muted">총평</h2>
            <p className="text-sm">{report.summary ?? "-"}</p>
          </Card>

          <Card as="section">
            <h2 className="mb-3 text-sm font-semibold text-foreground-muted">단계별 결과</h2>
            <ul className="flex flex-col gap-3">
              {report.timelineFeedback.map((entry) => (
                <li key={entry.submissionId} className="border-t border-border pt-3 first:border-t-0 first:pt-0 ">
                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-2 text-sm font-medium">
                      {PHASE_LABELS[entry.phase] ?? entry.phase}
                      {entry.onTime === false && <Badge variant="danger">시간 초과</Badge>}
                      {entry.onTime === true && <Badge variant="success">시간 내 제출</Badge>}
                    </span>
                    <span className="font-mono text-sm">{entry.totalScore ?? "-"} / 100</span>
                  </div>
                  {entry.topRisks.length > 0 && (
                    <ul className="mt-1 list-inside list-disc text-xs text-foreground-muted">
                      {entry.topRisks.map((risk, i) => (
                        <li key={i}>{risk}</li>
                      ))}
                    </ul>
                  )}
                </li>
              ))}
            </ul>
          </Card>

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
        </>
      )}
    </div>
  );
}
