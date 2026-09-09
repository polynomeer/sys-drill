"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ApiError,
  Assessment,
  OrganizationDetail,
  Report,
  ScenarioSummary,
  createAssessment,
  getAssessmentReport,
  getOrganization,
  listAssessments,
  listOrganizationScenarios,
  listScenarios,
} from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { LoadingState } from "@/components/ui/LoadingState";

const STATUS_LABELS: Record<string, string> = {
  NOT_STARTED: "미시작",
  IN_PROGRESS: "진행중",
  COMPLETED: "완료",
};

export default function OrganizationAssessmentsPage() {
  const params = useParams<{ orgId: string }>();
  const router = useRouter();
  const orgId = params.orgId;

  const [org, setOrg] = useState<OrganizationDetail | null>(null);
  const [assessments, setAssessments] = useState<Assessment[]>([]);
  const [candidateScenarios, setCandidateScenarios] = useState<ScenarioSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [candidateEmail, setCandidateEmail] = useState("");
  const [scenarioId, setScenarioId] = useState("");
  const [creating, setCreating] = useState(false);
  const [lastLink, setLastLink] = useState<string | null>(null);

  const [openReportId, setOpenReportId] = useState<string | null>(null);
  const [report, setReport] = useState<Report | null>(null);
  const [reportError, setReportError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const detail = await getOrganization(orgId);
    setOrg(detail);
    const [publicScenarios, orgScenarios] = await Promise.all([listScenarios(), listOrganizationScenarios(orgId)]);
    setCandidateScenarios([...publicScenarios, ...orgScenarios]);
    setAssessments(await listAssessments(orgId));
  }, [orgId]);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    load()
      .catch((err) => setError(err instanceof ApiError ? err.message : "역량 평가를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [router, load]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!candidateEmail.trim() || !scenarioId) return;
    setCreating(true);
    setError(null);
    try {
      const assessment = await createAssessment(orgId, candidateEmail.trim(), scenarioId);
      setLastLink(`${window.location.origin}/organizations/assessments/${assessment.token}`);
      setCandidateEmail("");
      setScenarioId("");
      setAssessments(await listAssessments(orgId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "평가를 생성하지 못했습니다.");
    } finally {
      setCreating(false);
    }
  }

  async function handleViewReport(assessment: Assessment) {
    setReportError(null);
    setReport(null);
    setOpenReportId(assessment.id);
    try {
      setReport(await getAssessmentReport(orgId, assessment.id));
    } catch (err) {
      setReportError(err instanceof ApiError ? err.message : "리포트를 불러오지 못했습니다.");
    }
  }

  if (loading) return <LoadingState className="p-8" />;
  if (error && !org) return <p className="p-8 text-sm text-danger">{error}</p>;
  if (!org) return null;

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-8">
      <div>
        <Link href={`/organizations/${orgId}`} className="text-sm text-foreground-muted underline">
          {org.name} 조직 상세로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">역량 평가</h1>
        <p className="mt-1 text-sm text-foreground-muted">후보자에게 평가 링크를 보내고 결과를 확인합니다.</p>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">평가 목록 ({assessments.length}건)</h2>
        {assessments.length === 0 && <p className="text-sm text-foreground-muted">아직 생성된 평가가 없습니다.</p>}
        <ul className="flex flex-col gap-3">
          {assessments.map((a) => (
            <li key={a.id} className="text-sm">
              <div className="flex items-center justify-between">
                <span>
                  {a.candidateEmail}
                  <span className="ml-2 text-xs text-foreground-muted">({a.scenarioTitle})</span>
                </span>
                <span className="flex items-center gap-2">
                  <Badge>{STATUS_LABELS[a.status] ?? a.status}</Badge>
                  {a.status === "COMPLETED" && (
                    <Button variant="ghost" size="sm" onClick={() => handleViewReport(a)}>
                      리포트 보기
                    </Button>
                  )}
                </span>
              </div>
              {openReportId === a.id && (
                <div className="mt-2 rounded bg-surface-elevated p-3 text-xs ">
                  {reportError && <p className="text-danger">{reportError}</p>}
                  {report && (
                    <>
                      <p className="mb-2">{report.summary}</p>
                      <ul className="flex flex-col gap-1">
                        {report.timelineFeedback.map((entry) => (
                          <li key={entry.submissionId}>
                            {entry.phase}: {entry.totalScore ?? "-"}점
                            {entry.topRisks.length > 0 && ` — ${entry.topRisks.join(", ")}`}
                          </li>
                        ))}
                      </ul>
                    </>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      </Card>

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">새 평가 생성</h2>
        <form onSubmit={handleCreate} className="flex flex-col gap-2">
          <Input
            type="email"
            value={candidateEmail}
            onChange={(e) => setCandidateEmail(e.target.value)}
            placeholder="candidate@example.com"
          />
          <select
            className="rounded border border-border px-3 py-2 text-sm  "
            value={scenarioId}
            onChange={(e) => setScenarioId(e.target.value)}
          >
            <option value="">시나리오 선택</option>
            {candidateScenarios.map((s) => (
              <option key={s.id} value={s.id}>
                {s.title} ({s.domain})
              </option>
            ))}
          </select>
          <Button type="submit" disabled={creating} className="self-start">
            {creating ? "생성하는 중..." : "평가 생성"}
          </Button>
        </form>
        {lastLink && (
          <div className="mt-3 rounded bg-surface-elevated p-3 text-xs ">
            <p className="mb-1 text-foreground-muted">이 링크를 후보자에게 직접 전달하세요 (이메일은 자동 발송되지 않습니다):</p>
            <code className="break-all">{lastLink}</code>
          </div>
        )}
      </Card>
    </div>
  );
}
