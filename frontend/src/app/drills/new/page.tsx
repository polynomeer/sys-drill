"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiError, ScenarioSummary, listMyMarketplaceScenarios, publishMarketplaceScenario } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { DifficultyBadge } from "@/components/ui/DifficultyBadge";
import { EmptyState } from "@/components/ui/EmptyState";
import { Input, Textarea } from "@/components/ui/Input";
import { LoadingState } from "@/components/ui/LoadingState";

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.5 — community scenario publishing, split
 * out of the Drills list page so the catalog stays a catalog. Same request
 * shape as before (ADR-0031): design + follow-up prompts only, no incident
 * step for marketplace scenarios.
 */
export default function NewDrillPage() {
  const router = useRouter();
  const [mine, setMine] = useState<ScenarioSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [title, setTitle] = useState("");
  const [domain, setDomain] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [initialPrompt, setInitialPrompt] = useState("");
  const [followupPrompt, setFollowupPrompt] = useState("");
  const [publishing, setPublishing] = useState(false);

  const loadMine = useCallback(() => listMyMarketplaceScenarios().then(setMine), []);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }
    loadMine()
      .catch((err) => setError(err instanceof ApiError ? err.message : "등록한 시나리오를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [router, loadMine]);

  async function handlePublish(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim() || !domain.trim() || !initialPrompt.trim() || !followupPrompt.trim()) return;
    setPublishing(true);
    setError(null);
    setNotice(null);
    try {
      const published = await publishMarketplaceScenario({
        title: title.trim(),
        difficulty: difficulty.trim() || undefined,
        domain: domain.trim(),
        initialPrompt: initialPrompt.trim(),
        followupPrompt: followupPrompt.trim(),
      });
      setTitle("");
      setDomain("");
      setDifficulty("");
      setInitialPrompt("");
      setFollowupPrompt("");
      setNotice(`"${published.title}"을(를) 등록했습니다. Drills 목록에 바로 나타납니다.`);
      await loadMine();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "시나리오를 등록하지 못했습니다.");
    } finally {
      setPublishing(false);
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 p-8">
      <div>
        <Link href="/marketplace" className="text-xs text-foreground-muted hover:text-foreground">
          ← Drills
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">시나리오 등록</h1>
        <p className="mt-1 text-sm text-foreground-muted">
          초기 설계와 꼬리설계 2단계로 구성된 커뮤니티 Drill을 만듭니다. 장애 대응 단계는 포함되지 않습니다.
        </p>
      </div>

      <form onSubmit={handlePublish} className="flex flex-col gap-3">
        <Input label="제목" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="예: 커뮤니티 게시판 좋아요 카운터" />
        <div className="flex flex-col gap-3 sm:flex-row">
          {/* Input's className styles the field itself, not its label wrapper — size the wrappers here. */}
          <div className="flex-1">
            <Input
              label="도메인 라벨"
              className="w-full"
              value={domain}
              onChange={(e) => setDomain(e.target.value)}
              placeholder="예: community-rate-limit"
            />
          </div>
          <div className="sm:w-44">
            <Input label="난이도" className="w-full" value={difficulty} onChange={(e) => setDifficulty(e.target.value)} placeholder="EASY / MEDIUM / HARD" />
          </div>
        </div>
        <Textarea label="초기 설계 프롬프트" value={initialPrompt} onChange={(e) => setInitialPrompt(e.target.value)} rows={4} />
        <Textarea
          label="꼬리설계 프롬프트 — 1단계 제출 후에 공개되는 바뀐 조건"
          value={followupPrompt}
          onChange={(e) => setFollowupPrompt(e.target.value)}
          rows={4}
        />
        {error && <p className="text-sm text-danger">{error}</p>}
        {notice && <p className="text-sm text-success">{notice}</p>}
        <Button type="submit" disabled={publishing} className="self-start">
          {publishing ? "등록하는 중..." : "등록하기"}
        </Button>
      </form>

      <Card as="section">
        <h2 className="mb-3 text-sm font-semibold text-foreground-muted">내가 등록한 시나리오 ({mine.length}개)</h2>
        {loading ? (
          <LoadingState />
        ) : mine.length === 0 ? (
          <EmptyState message="아직 등록한 시나리오가 없습니다." />
        ) : (
          <ul className="flex flex-col gap-2">
            {mine.map((scenario) => (
              <li key={scenario.id} className="flex items-center justify-between gap-3 text-sm">
                <Link href={`/drills/${scenario.id}`} className="hover:text-accent">
                  {scenario.title}
                </Link>
                <DifficultyBadge difficulty={scenario.difficulty} />
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
