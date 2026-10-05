"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { FailurePatternDetail, ScenarioSummary, getFailurePattern, listScenarios } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { ContentBlocks } from "@/components/ContentBlocks";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

/** PLAN.md Round E19 (docs/LEARNING_EXPANSION_PLAN.md L8) — one failure pattern, ending in the Drill that trains it. */
export default function FailurePatternPage() {
  const router = useRouter();
  const { domain } = useParams<{ domain: string }>();
  const [pattern, setPattern] = useState<FailurePatternDetail | null>(null);
  const [scenario, setScenario] = useState<ScenarioSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    getFailurePattern(domain)
      .then(setPattern)
      .catch(() => setError("장애 패턴을 찾을 수 없습니다."));
    listScenarios()
      .then((all) => setScenario(all.find((s) => s.domain === domain && !s.creatorNickname) ?? null))
      .catch(() => setScenario(null));
  }, [domain, router]);

  if (error) return <p className="mx-auto w-full max-w-7xl p-8 text-sm text-danger">{error}</p>;
  if (!pattern) return <div className="mx-auto w-full max-w-7xl p-8"><LoadingState /></div>;

  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-4 p-8">
      <div>
        <Link href="/learning/failures" className="text-sm text-foreground-muted hover:text-foreground">
          ← 장애 패턴 사전
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">{pattern.name}</h1>
        <p className="mt-2 max-w-[72ch] leading-relaxed">{pattern.summary}</p>
      </div>

      <div className="flex flex-col gap-4 lg:grid lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-col gap-4">
          <Section title="증상">
            <List items={pattern.symptoms} />
          </Section>
          <div className="grid gap-4 sm:grid-cols-2">
            <Section title="전형적 지표">
              <List items={pattern.typicalMetrics} />
            </Section>
            <Section title="전형적 로그">
              <ul className="flex flex-col gap-1 font-mono text-xs text-foreground-muted">
                {pattern.typicalLogs.map((l) => (
                  <li key={l} className="break-words">
                    {l}
                  </li>
                ))}
              </ul>
            </Section>
          </div>
          <Section title="흔한 원인">
            <List items={pattern.commonCauses} />
          </Section>
          {/* docs/LEARNING_DEEPENING_PLAN.md L12/L13 — how the incident unfolds, in pictures and the engine's numbers. */}
          {(pattern.blocks?.length ?? 0) > 0 && (
            <Card as="section">
              <h2 className="mb-3 text-sm font-semibold">그림과 숫자로 보기</h2>
              <ContentBlocks blocks={pattern.blocks!} />
            </Card>
          )}
          <Card as="section" className="border-danger/40">
            <h2 className="mb-2 text-sm font-semibold">그럴듯하지만 틀린 대응</h2>
            <ul className="flex flex-col gap-2 text-sm">
              {pattern.badFixes.map((b) => (
                <li key={b.fix}>
                  <span className="font-medium">✗ {b.fix}</span>
                  <p className="text-foreground-muted">{b.why}</p>
                </li>
              ))}
            </ul>
          </Card>
          <div className="grid gap-4 sm:grid-cols-2">
            <Section title="완화 (지금)">
              <List items={pattern.mitigations} ordered />
            </Section>
            <Section title="예방 (다음 설계)">
              <List items={pattern.prevention} />
            </Section>
          </div>
        </div>
        <aside className="flex min-w-0 flex-col gap-4 lg:sticky lg:top-6">
          <Section title="관련 개념">
            <div className="flex flex-wrap gap-2 text-sm">
              {pattern.relatedConcepts.map((c) => (
                <Link key={c.riskKey} href={`/learning/${c.riskKey}`} className="rounded-full border border-border px-2.5 py-0.5 hover:border-accent">
                  {c.label}
                </Link>
              ))}
            </div>
          </Section>
          {scenario && (
            <Button href={`/drills/${scenario.id}`} className="self-start lg:self-stretch">
              이 장애를 직접 대응해 보기 →
            </Button>
          )}
        </aside>
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card as="section">
      <h2 className="mb-2 text-sm font-semibold text-foreground-muted">{title}</h2>
      {children}
    </Card>
  );
}

function List({ items, ordered }: { items: string[]; ordered?: boolean }) {
  const Tag = ordered ? "ol" : "ul";
  return (
    <Tag className={`${ordered ? "list-decimal" : "list-disc"} list-inside space-y-1 text-sm text-foreground-muted`}>
      {items.map((i) => (
        <li key={i}>{i}</li>
      ))}
    </Tag>
  );
}
