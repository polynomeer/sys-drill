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

  if (loading) return <p className="p-8 text-sm text-zinc-500">불러오는 중...</p>;
  if (error && !org) {
    return (
      <div className="mx-auto flex min-h-screen max-w-2xl flex-col gap-4 p-8">
        <Link href={`/organizations/${orgId}`} className="text-sm text-zinc-500 underline">
          조직 상세로
        </Link>
        <p className="text-sm text-red-600">{error}</p>
      </div>
    );
  }
  if (!org || !curriculum) return null;

  return (
    <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-6 p-8">
      <div>
        <Link href={`/organizations/${orgId}`} className="text-sm text-zinc-500 underline">
          {org.name} 조직 상세로
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">온보딩 커리큘럼</h1>
        <p className="mt-1 text-sm text-zinc-500">순서는 안내일 뿐 강제되지 않습니다 — 어떤 단계든 먼저 시작할 수 있습니다.</p>
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <h2 className="mb-3 text-sm font-semibold text-zinc-500">내 진행 상황</h2>
        {curriculum.steps.length === 0 && <p className="text-sm text-zinc-500">아직 커리큘럼이 설정되지 않았습니다.</p>}
        <ol className="flex flex-col gap-2">
          {curriculum.steps.map((step) => (
            <li key={step.scenarioId} className="flex items-center justify-between text-sm">
              <span>
                {step.order}. {step.completed ? "✅" : "⬜️"} {step.title}
                <span className="ml-2 text-xs text-zinc-500">({step.domain})</span>
              </span>
              <button
                onClick={() => handleStart(step.scenarioId)}
                disabled={startingId === step.scenarioId}
                className="rounded bg-foreground px-3 py-1 text-xs font-medium text-background disabled:opacity-50"
              >
                {startingId === step.scenarioId ? "시작하는 중..." : step.completed ? "다시 풀기" : "시작"}
              </button>
            </li>
          ))}
        </ol>
      </section>

      {org.myRole === "ADMIN" && (
        <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
          <h2 className="mb-3 text-sm font-semibold text-zinc-500">커리큘럼 편집</h2>

          <ol className="mb-3 flex flex-col gap-2">
            {draftIds.length === 0 && <p className="text-sm text-zinc-500">아래에서 시나리오를 추가하세요.</p>}
            {draftIds.map((scenarioId, index) => (
              <li key={scenarioId} className="flex items-center justify-between gap-2 text-sm">
                <span>
                  {index + 1}. {titleFor(scenarioId)}
                </span>
                <span className="flex gap-1 text-xs">
                  <button onClick={() => moveInDraft(index, -1)} disabled={index === 0} className="underline disabled:opacity-30">
                    위로
                  </button>
                  <button onClick={() => moveInDraft(index, 1)} disabled={index === draftIds.length - 1} className="underline disabled:opacity-30">
                    아래로
                  </button>
                  <button onClick={() => removeFromDraft(scenarioId)} className="text-red-600 underline">
                    제거
                  </button>
                </span>
              </li>
            ))}
          </ol>

          <div className="mb-3 flex flex-wrap gap-2">
            {candidates
              .filter((c) => !draftIds.includes(c.id))
              .map((c) => (
                <button
                  key={c.id}
                  onClick={() => addToDraft(c.id)}
                  className="rounded border border-zinc-300 px-2 py-1 text-xs dark:border-zinc-700"
                >
                  + {c.title}
                </button>
              ))}
          </div>

          <button
            onClick={handleSave}
            disabled={saving || draftIds.length === 0}
            className="rounded bg-foreground px-4 py-2 text-sm font-medium text-background disabled:opacity-50"
          >
            {saving ? "저장하는 중..." : "저장"}
          </button>
        </section>
      )}
    </div>
  );
}
