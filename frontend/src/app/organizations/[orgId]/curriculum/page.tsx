"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ApiError,
  Curriculum,
  OrganizationDetail,
  ScenarioSummary,
  getCurriculum,
  getOrganization,
  listOrganizationScenarios,
  listScenarios,
  setCurriculum,
  startSession,
} from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

export default function OrganizationCurriculumPage() {
  const params = useParams<{ orgId: string }>();
  const router = useRouter();
  const orgId = params.orgId;

  const [org, setOrg] = useState<OrganizationDetail | null>(null);
  const [curriculum, setCurriculumState] = useState<Curriculum | null>(null);
  const [candidates, setCandidates] = useState<ScenarioSummary[]>([]);
  const [draftIds, setDraftIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [startingId, setStartingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [orgDetail, curriculumData] = await Promise.all([getOrganization(orgId), getCurriculum(orgId)]);
    setOrg(orgDetail);
    setCurriculumState(curriculumData);
    setDraftIds(curriculumData.steps.map((s) => s.scenarioId));
    if (orgDetail.myRole === "ADMIN") {
      const [publicScenarios, orgScenarios] = await Promise.all([listScenarios(), listOrganizationScenarios(orgId)]);
      setCandidates([...publicScenarios, ...orgScenarios]);
    }
  }, [orgId]);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }

    load()
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setError("이 조직의 멤버만 온보딩 커리큘럼을 볼 수 있습니다.");
        } else {
          setError(err instanceof ApiError ? err.message : "커리큘럼을 불러오지 못했습니다.");
        }
      })
      .finally(() => setLoading(false));
  }, [router, load]);

  async function handleStart(scenarioId: string) {
    setStartingId(scenarioId);
    setError(null);
    try {
      const session = await startSession(scenarioId);
      router.push(`/design/${session.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "세션을 시작하지 못했습니다.");
      setStartingId(null);
    }
  }

  function addToDraft(scenarioId: string) {
    if (draftIds.includes(scenarioId)) return;
    setDraftIds((prev) => [...prev, scenarioId]);
  }

  function removeFromDraft(scenarioId: string) {
    setDraftIds((prev) => prev.filter((id) => id !== scenarioId));
  }

  function moveInDraft(index: number, direction: -1 | 1) {
    setDraftIds((prev) => {
      const next = [...prev];
      const target = index + direction;
      if (target < 0 || target >= next.length) return prev;
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const updated = await setCurriculum(orgId, draftIds);
      setCurriculumState(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "커리큘럼을 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  function titleFor(scenarioId: string): string {
    return candidates.find((c) => c.id === scenarioId)?.title ?? curriculum?.steps.find((s) => s.scenarioId === scenarioId)?.title ?? scenarioId;
  }

  if (loading) return <LoadingState className="p-8" />;
  if (error && !org) {
    return (
      <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-4 p-8">
        <Link href={`/organizations/${orgId}`} className="text-sm text-foreground-muted underline">
          조직 상세로
        </Link>
        <p className="text-sm text-danger">{error}</p>
      </div>
    );
  }
  if (!org || !curriculum) return null;

  return (
    <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-6 p-8">
      <div>
        <Link href={`/organizations/${orgId}`} className="text-sm text-foreground-muted underline">
          {org.name} 조직 상세로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">온보딩 커리큘럼</h1>
        <p className="mt-1 text-sm text-foreground-muted">순서는 안내일 뿐 강제되지 않습니다 — 어떤 단계든 먼저 시작할 수 있습니다.</p>
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">내 진행 상황</h2>
        {curriculum.steps.length === 0 && <p className="text-sm text-foreground-muted">아직 커리큘럼이 설정되지 않았습니다.</p>}
        <ol className="flex flex-col gap-2">
          {curriculum.steps.map((step) => (
            <li key={step.scenarioId} className="flex items-center justify-between text-sm">
              <span>
                {step.order}. {step.completed ? "✅" : "⬜️"} {step.title}
                <span className="ml-2 text-xs text-foreground-muted">({step.domain})</span>
              </span>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => handleStart(step.scenarioId)}
                disabled={startingId === step.scenarioId}
              >
                {startingId === step.scenarioId ? "시작하는 중..." : step.completed ? "다시 풀기" : "시작"}
              </Button>
            </li>
          ))}
        </ol>
      </Card>

      {org.myRole === "ADMIN" && (
        <Card as="section">
          <h2 className="mb-3 text-sm font-semibold text-foreground-muted">커리큘럼 편집</h2>

          <ol className="mb-3 flex flex-col gap-2">
            {draftIds.length === 0 && <p className="text-sm text-foreground-muted">아래에서 시나리오를 추가하세요.</p>}
            {draftIds.map((scenarioId, index) => (
              <li key={scenarioId} className="flex items-center justify-between gap-2 text-sm">
                <span>
                  {index + 1}. {titleFor(scenarioId)}
                </span>
                <span className="flex gap-1">
                  <Button variant="ghost" size="sm" onClick={() => moveInDraft(index, -1)} disabled={index === 0}>
                    위로
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => moveInDraft(index, 1)} disabled={index === draftIds.length - 1}>
                    아래로
                  </Button>
                  <Button variant="danger" size="sm" onClick={() => removeFromDraft(scenarioId)}>
                    제거
                  </Button>
                </span>
              </li>
            ))}
          </ol>

          <div className="mb-3 flex flex-wrap gap-2">
            {candidates
              .filter((c) => !draftIds.includes(c.id))
              .map((c) => (
                <Button key={c.id} variant="secondary" size="sm" onClick={() => addToDraft(c.id)}>
                  + {c.title}
                </Button>
              ))}
          </div>

          <Button onClick={handleSave} disabled={saving || draftIds.length === 0}>
            {saving ? "저장하는 중..." : "저장"}
          </Button>
        </Card>
      )}
    </div>
  );
}
