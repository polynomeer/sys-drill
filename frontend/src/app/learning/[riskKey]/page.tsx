"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { LearningConceptDetail, ScenarioSummary, getLearningConcept, listScenarios } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";
import { Button } from "@/components/ui/Button";
import { DOMAIN_TITLES } from "@/lib/designGuidance";

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §5.2 / §5.4 — 개념 상세.
 *
 * 화면의 끝은 항상 **행동**이다: 이 개념이 나오는 시나리오를 바로 시작하거나
 * 관련 Build 과제로 넘어간다. 읽고 끝나면 이 제품에서는 의미가 없다.
 */
export default function LearningConceptPage() {
  const router = useRouter();
  const params = useParams<{ riskKey: string }>();
  const riskKey = params.riskKey;

  const [concept, setConcept] = useState<LearningConceptDetail | null>(null);
  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    getLearningConcept(riskKey)
      .then(setConcept)
      .catch(() => setError("개념을 찾을 수 없습니다."));
    // 관련 도메인 → 실제 시작 가능한 시나리오로 잇기 위한 조회. 실패해도 본문은 보여준다.
    listScenarios().then(setScenarios).catch(() => setScenarios([]));
  }, [riskKey, router]);

  if (error) {
    return (
      <div className="mx-auto max-w-3xl p-8">
        <p className="text-sm text-danger">{error}</p>
        <Link href="/learning" className="mt-3 inline-block text-sm underline">
          Learning으로
        </Link>
      </div>
    );
  }
  if (!concept) return <div className="mx-auto max-w-3xl p-8"><LoadingState /></div>;

  const relatedScenarios = scenarios.filter((s) => concept.relatedDomains.includes(s.domain));

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5 p-8">
      <div>
        <Link href="/learning" className="text-sm underline">
          ← Learning
        </Link>
        <div className="mt-2 flex items-baseline gap-3">
          <h1 className="text-2xl font-semibold">{concept.label}</h1>
          <span className="text-xs text-foreground-muted">{concept.categoryLabel}</span>
          {concept.myWeaknessCount > 0 && (
            <span className="rounded-full bg-danger/15 px-2 py-0.5 text-xs text-danger">
              내가 {concept.myWeaknessCount}회 놓친 개념
            </span>
          )}
        </div>
        <p className="mt-2 text-sm">{concept.summary}</p>
      </div>

      <Card as="section">
        <h2 className="mb-2 text-sm font-semibold text-foreground-muted">왜 문제가 되는가</h2>
        <p className="text-sm leading-relaxed">{concept.whyItMatters}</p>
      </Card>

      <Card as="section">
        <h2 className="mb-2 text-sm font-semibold text-foreground-muted">어떤 신호로 드러나는가</h2>
        <ul className="list-inside list-disc space-y-1 text-sm text-foreground-muted">
          {concept.symptoms.map((s) => (
            <li key={s}>{s}</li>
          ))}
        </ul>
      </Card>

      <Card as="section">
        <h2 className="mb-2 text-sm font-semibold text-foreground-muted">해결 패턴</h2>
        <ul className="list-inside list-disc space-y-1 text-sm text-foreground-muted">
          {concept.patterns.map((p) => (
            <li key={p}>{p}</li>
          ))}
        </ul>
      </Card>

      <Card as="section">
        <h2 className="mb-2 text-sm font-semibold text-foreground-muted">대가 (트레이드오프)</h2>
        <p className="text-sm leading-relaxed text-foreground-muted">{concept.tradeoffs}</p>
      </Card>

      {(relatedScenarios.length > 0 || concept.relatedChallenges.length > 0) && (
        <Card as="section">
          <h2 className="mb-1 text-sm font-semibold">직접 해보기</h2>
          <p className="mb-3 text-xs text-foreground-muted">
            읽는 것으로는 이 개념이 어디서 깨지는지 알 수 없습니다.
          </p>
          {relatedScenarios.length > 0 && (
            <div className="mb-3 flex flex-wrap gap-2">
              {relatedScenarios.map((s) => (
                <Button key={s.id} href={`/dashboard#drills`} size="sm" variant="secondary">
                  {DOMAIN_TITLES[s.domain] ?? s.title} 시나리오
                </Button>
              ))}
            </div>
          )}
          {concept.relatedChallenges.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {concept.relatedChallenges.map((slug) => (
                <Button key={slug} href="/bridge" size="sm" variant="secondary">
                  Build: {slug}
                </Button>
              ))}
            </div>
          )}
        </Card>
      )}
    </div>
  );
}
