"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { DiscussionThread, LearningConceptSummary, getDiscussion, getLearningConcepts, listScenarios } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { DiscussionPanel } from "@/components/DiscussionPanel";

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.5 / ADR-0040 — 시나리오별 토론 페이지.
 *
 * 같은 시나리오의 공개 풀이로 가는 링크를 나란히 둔다. 질문과 남의 풀이는
 * 같은 맥락에서 읽혀야 의미가 있고, 둘의 열람 조건이 다르다는 점(토론은 열려
 * 있고 풀이는 완료자 한정)도 여기서 한눈에 드러난다.
 */
export default function ScenarioDiscussionPage() {
  const params = useParams<{ scenarioId: string }>();
  const router = useRouter();
  const scenarioId = params.scenarioId;
  const [thread, setThread] = useState<DiscussionThread | null>(null);
  // PLAN.md Round E23 (C11) — the concepts this scenario trains, the reverse of the concept page's discussion links.
  const [concepts, setConcepts] = useState<LearningConceptSummary[]>([]);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    // 제목만 쓰는 가벼운 조회다 — 패널이 자체 폴링으로 본문을 갱신한다.
    getDiscussion(scenarioId).then(setThread).catch(() => setThread(null));
    Promise.all([listScenarios(), getLearningConcepts()])
      .then(([scenarios, categories]) => {
        const domain = scenarios.find((s) => s.id === scenarioId)?.domain;
        if (!domain) return;
        setConcepts(categories.flatMap((c) => c.concepts).filter((c) => c.relatedDomains?.includes(domain)));
      })
      .catch(() => setConcepts([]));
  }, [scenarioId, router]);

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6 p-8">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">토론</h1>
          {thread && <p className="mt-1 text-sm text-foreground-muted">{thread.scenarioTitle}</p>}
        </div>
        <Link href={`/writeups/${scenarioId}`} className="text-sm underline">
          이 시나리오의 공개 풀이 →
        </Link>
      </div>

      {concepts.length > 0 && (
        <div className="flex flex-wrap items-center gap-1.5 text-xs">
          <span className="text-foreground-muted">이 시나리오가 다루는 개념:</span>
          {concepts.map((c) => (
            <Link key={c.riskKey} href={`/learning/${c.riskKey}`} className="rounded-full border border-border px-2 py-0.5 hover:border-accent">
              {c.label}
            </Link>
          ))}
        </div>
      )}

      <DiscussionPanel scenarioId={scenarioId} />
    </div>
  );
}
