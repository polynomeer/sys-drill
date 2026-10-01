"use client";

import { useCallback, useEffect, useState } from "react";
import { BlockReader } from "@/components/BlockReader";
import { ConceptQuizCard } from "@/components/ConceptQuizCard";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { LabSummary, LearningConceptDetail, ScenarioSummary, getLearningConcept, listLabs, listScenarios } from "@/lib/api";
import { getStoredToken, isConceptRead, markConceptRead } from "@/lib/localSession";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";
import { Button } from "@/components/ui/Button";
import { DOMAIN_TITLES } from "@/lib/designGuidance";

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §5.2 / §5.4 — 개념 상세.
 *
 * 화면의 끝은 항상 **행동**이다: 이 개념이 나오는 시나리오를 바로 시작하거나
 * 관련 Build 과제로 넘어간다. 읽고 끝나면 이 제품에서는 의미가 없다.
 *
 * docs/CODECRAFTERS_BENCHMARK.md §3.6 — 처음 읽을 때는 CodeCrafters Concepts처럼
 * 한 블록씩 드러낸다(Enter ↵로 계속). 끝까지 읽은 개념은 다음부터 전부 펼쳐진다.
 */
export default function LearningConceptPage() {
  const router = useRouter();
  const params = useParams<{ riskKey: string }>();
  const riskKey = params.riskKey;

  const [concept, setConcept] = useState<LearningConceptDetail | null>(null);
  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [labs, setLabs] = useState<LabSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [revealed, setRevealed] = useState(1);
  // Stable so BlockReader's done-effect fires once, not on every render.
  const markRead = useCallback(() => markConceptRead(riskKey), [riskKey]);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    getLearningConcept(riskKey)
      .then(setConcept)
      .catch(() => setError("개념을 찾을 수 없습니다."));
    // Already-read concepts open fully expanded (localStorage read on mount).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setRevealed(isConceptRead(riskKey) ? Number.MAX_SAFE_INTEGER : 1);
    // 관련 도메인 → 실제 시작 가능한 시나리오로 잇기 위한 조회. 실패해도 본문은 보여준다.
    listScenarios().then(setScenarios).catch(() => setScenarios([]));
    // docs/LEARNING_EXPANSION_PLAN.md L5 — the engine lab for this concept, the shortest practice step.
    listLabs().then((all) => setLabs(all.filter((l) => l.riskKey === riskKey))).catch(() => setLabs([]));
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

  const relatedScenarios = scenarios.filter((s) => concept.relatedDomains.includes(s.domain) && !s.creatorNickname);
  const hasTryIt = labs.length > 0 || relatedScenarios.length > 0 || concept.relatedChallenges.length > 0;

  const blocks: { key: string; node: React.ReactNode }[] = [
    {
      key: "why",
      node: (
        <Card as="section">
          <h2 className="mb-2 text-sm font-semibold text-foreground-muted">왜 문제가 되는가</h2>
          <p className="text-sm leading-relaxed">{concept.whyItMatters}</p>
        </Card>
      ),
    },
    {
      key: "symptoms",
      node: (
        <Card as="section">
          <h2 className="mb-2 text-sm font-semibold text-foreground-muted">어떤 신호로 드러나는가</h2>
          <ul className="list-inside list-disc space-y-1 text-sm text-foreground-muted">
            {concept.symptoms.map((s) => (
              <li key={s}>{s}</li>
            ))}
          </ul>
        </Card>
      ),
    },
    {
      key: "patterns",
      node: (
        <Card as="section">
          <h2 className="mb-2 text-sm font-semibold text-foreground-muted">해결 패턴</h2>
          <ul className="list-inside list-disc space-y-1 text-sm text-foreground-muted">
            {concept.patterns.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        </Card>
      ),
    },
    {
      key: "tradeoffs",
      node: (
        <Card as="section">
          <h2 className="mb-2 text-sm font-semibold text-foreground-muted">대가 (트레이드오프)</h2>
          <p className="text-sm leading-relaxed text-foreground-muted">{concept.tradeoffs}</p>
        </Card>
      ),
    },
  ];
  // docs/CODECRAFTERS_BENCHMARK.md §3.6 — self-check before the call to action.
  blocks.push({ key: "quiz", node: <ConceptQuizCard riskKey={riskKey} /> });
  if (hasTryIt) {
    // docs/LEARNING_EXPANSION_PLAN.md L4 — 짧은 것부터 긴 것 순서의 연습 레일:
    // Build 단계(구현) → Drill(설계·장애 대응) → 같은 Drill을 푼 사람들의 토론.
    blocks.push({
      key: "try",
      node: (
        <Card as="section" className="border-accent/40">
          <h2 className="mb-1 text-sm font-semibold">이 개념을 연습하는 방법</h2>
          <p className="mb-3 text-xs text-foreground-muted">읽는 것으로는 이 개념이 어디서 깨지는지 알 수 없습니다.</p>
          <ol className="flex flex-col gap-3">
            {labs.length > 0 && (
              <PracticeStep title="값을 바꿔 보기" hint="랩 — 예측하고 실행해서 현상을 직접 확인 (5분)">
                {labs.map((lab) => (
                  <Button key={lab.slug} href={`/learning/labs/${lab.slug}`} size="sm" variant="secondary">
                    랩: {lab.title}
                  </Button>
                ))}
              </PracticeStep>
            )}
            {concept.relatedChallenges.length > 0 && (
              <PracticeStep title="직접 구현하기" hint="Build 챌린지 — 작은 단계부터">
                {concept.relatedChallenges.map((slug) => (
                  <Button key={slug} href={`/bridge?challenge=${slug}`} size="sm" variant="secondary">
                    Build: {slug}
                  </Button>
                ))}
              </PracticeStep>
            )}
            {relatedScenarios.length > 0 && (
              <PracticeStep title="설계하고 장애에 대응하기" hint="이 개념이 채점 기준에 들어 있는 Drill">
                {relatedScenarios.map((s) => (
                  <Button key={s.id} href={`/drills/${s.id}`} size="sm" variant="secondary">
                    {DOMAIN_TITLES[s.domain] ?? s.title} Drill →
                  </Button>
                ))}
              </PracticeStep>
            )}
            {relatedScenarios.length > 0 && (
              <PracticeStep title="다른 사람은 어떻게 풀었나" hint="시나리오별 토론">
                {relatedScenarios.map((s) => (
                  <Button key={s.id} href={`/discussions/${s.id}`} size="sm" variant="ghost">
                    {DOMAIN_TITLES[s.domain] ?? s.title} 토론
                  </Button>
                ))}
              </PracticeStep>
            )}
          </ol>
        </Card>
      ),
    });
  }

  const shown = Math.min(revealed, blocks.length);
  const done = shown >= blocks.length;

  return (
    <BlockReader
      done={done}
      onDone={markRead}
      onContinue={() => setRevealed((n) => n + 1)}
      onExpandAll={() => setRevealed(Number.MAX_SAFE_INTEGER)}
    >
      <div>
        <Link href="/learning" className="text-sm text-foreground-muted hover:text-foreground">
          ← Learning
        </Link>
        <p className="mt-3 text-xs font-semibold uppercase tracking-wide text-foreground-muted">개념 · {concept.categoryLabel}</p>
        <div className="mt-1 flex flex-wrap items-baseline gap-3">
          <h1 className="break-keep text-2xl font-semibold">{concept.label}</h1>
          {concept.myWeaknessCount > 0 && (
            <span className="rounded-full bg-danger/15 px-2 py-0.5 text-xs text-danger">
              내가 {concept.myWeaknessCount}회 놓친 개념
            </span>
          )}
        </div>
        <p className="mt-1 text-xs text-foreground-muted">
          읽는 데 약 {concept.readingMinutes ?? 1}분 · {shown} / {blocks.length} 블록
        </p>
        <p className="mt-3 leading-relaxed">{concept.summary}</p>
      </div>

      {blocks.slice(0, shown).map((block) => (
        <div key={block.key}>{block.node}</div>
      ))}
    </BlockReader>
  );
}

function PracticeStep({ title, hint, children }: { title: string; hint: string; children: React.ReactNode }) {
  return (
    <li>
      <p className="text-sm font-medium">{title}</p>
      <p className="mb-2 text-xs text-foreground-muted">{hint}</p>
      <div className="flex flex-wrap gap-2">{children}</div>
    </li>
  );
}
