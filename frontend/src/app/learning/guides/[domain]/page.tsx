"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { type DesignGuide, type GuideStep, type ScenarioSummary, type SystemState, getDesignGuide, listScenarios } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { ContentBlocks, RichText } from "@/components/ContentBlocks";
import { SystemDiagram } from "@/components/SystemDiagram";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

const ACTION_KIND = {
  TUNE: { label: "값 조정", className: "border-accent text-accent" },
  REDESIGN: { label: "설계 변경", className: "border-warning text-warning" },
} as const;

const anchorOf = (where: string) => (where.startsWith("step-") ? where : `section-${where}`);

/**
 * docs/LEARNING_DEEPENING_PLAN.md L14 (PLAN.md Round E35) — one domain's design guide: what the
 * service must carry, the base architecture, then a scenario chain — situation → signal →
 * diagnosis → action (tune a value or change the design) → result and its cost → what's left,
 * which becomes the next step. Every number and every system picture is the rule engine's output
 * for that step's design (ADR-0054); each step links to the lab preset to the same state.
 */
export default function DesignGuidePage() {
  const router = useRouter();
  const { domain } = useParams<{ domain: string }>();
  const [guide, setGuide] = useState<DesignGuide | null>(null);
  const [scenario, setScenario] = useState<ScenarioSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    getDesignGuide(domain)
      .then(setGuide)
      .catch(() => setError("설계 가이드를 찾을 수 없습니다."));
    listScenarios()
      .then((all) => setScenario(all.find((s) => s.domain === domain && !s.creatorNickname) ?? null))
      .catch(() => setScenario(null));
  }, [domain, router]);

  if (error) return <p className="mx-auto w-full max-w-7xl p-8 text-sm text-danger">{error}</p>;
  if (!guide) return <div className="mx-auto w-full max-w-7xl p-8"><LoadingState /></div>;

  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-6 p-4 sm:p-8">
      <div>
        <Link href="/learning" className="text-sm text-foreground-muted hover:text-foreground">
          ← Learning
        </Link>
        <p className="mt-3 text-xs font-semibold uppercase tracking-wide text-foreground-muted">도메인 설계 가이드</p>
        <h1 className="mt-1 text-2xl font-semibold">{guide.title}</h1>
        <p className="mt-2 max-w-[72ch] leading-relaxed text-foreground-muted">{guide.summary}</p>
      </div>

      <div className="flex flex-col gap-6 lg:grid lg:grid-cols-[minmax(0,1fr)_220px] lg:items-start">
        <div className="flex min-w-0 flex-col gap-6">
          <Card as="section">
            <h2 id="section-requirements" className="mb-3 scroll-mt-6 text-lg font-semibold">
              1. 무엇을 감당해야 하나
            </h2>
            <ContentBlocks blocks={guide.requirements} />
          </Card>

          <Card as="section">
            <h2 id="section-architecture" className="mb-3 scroll-mt-6 text-lg font-semibold">
              2. 기본 아키텍처
            </h2>
            <ContentBlocks blocks={guide.architecture} />
          </Card>

          <section className="flex flex-col gap-3">
            <div>
              <h2 id="section-scenario" className="scroll-mt-6 text-lg font-semibold">3. 시나리오 — 문제가 생기면 무엇을 바꾸나</h2>
              <p className="mt-1 text-sm text-foreground-muted">
                각 단계는 앞 단계의 설계에서 출발합니다. 수치와 그림은 그 설계로 시뮬레이션 엔진을 돌린 결과입니다.
              </p>
            </div>
            {guide.steps.map((step, i) => (
              <StepCard key={i} index={i} step={step} domain={guide.domain} last={i === guide.steps.length - 1} />
            ))}
          </section>

          <Card as="section">
            <h2 id="section-checklist" className="mb-3 scroll-mt-6 text-lg font-semibold">
              4. 설계 체크리스트
            </h2>
            <p className="mb-2 text-sm text-foreground-muted">Drill에서 설계를 쓸 때 다뤄야 할 항목과, 이 가이드에서 그 항목을 다룬 곳입니다.</p>
            <ul className="flex flex-col divide-y divide-border text-sm">
              {guide.checklist.map((c) => (
                <li key={c.item} className="flex flex-wrap items-baseline justify-between gap-2 py-2">
                  <span>{c.item}</span>
                  <a href={`#${anchorOf(c.where)}`} className="shrink-0 text-xs text-accent underline">
                    {c.where === "requirements" ? "1. 요구사항" : c.where === "architecture" ? "2. 아키텍처" : `단계 ${c.where.slice(5)}`}
                  </a>
                </li>
              ))}
            </ul>
          </Card>

          {guide.pitfalls.length > 0 && (
            <Card as="section" className="border-danger/40">
              <h2 id="section-pitfalls" className="mb-3 scroll-mt-6 text-lg font-semibold">
                5. 그럴듯하지만 틀린 대응
              </h2>
              <ul className="flex flex-col gap-2 text-sm">
                {guide.pitfalls.map((p) => (
                  <li key={p.fix}>
                    <span className="font-medium">✗ {p.fix}</span>
                    <p className="text-foreground-muted">{p.why}</p>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          {scenario && (
            <Card as="section" className="flex flex-col items-start gap-2">
              <p className="font-medium">이제 직접 설계하고 장애에 대응해 볼 차례입니다.</p>
              <p className="text-sm text-foreground-muted">같은 상황이 Drill에서는 정해진 순서 없이 옵니다 — 신호를 보고 무엇부터 바꿀지 스스로 고르세요.</p>
              <Button href={`/drills/${scenario.id}`}>{scenario.title} Drill 시작 →</Button>
            </Card>
          )}
        </div>

        <nav className="hidden flex-col gap-1 text-sm lg:sticky lg:top-6 lg:flex" aria-label="가이드 목차">
          <p className="mb-1 text-xs font-semibold text-foreground-muted">목차</p>
          <a href="#section-requirements" className="text-foreground-muted hover:text-foreground">1. 무엇을 감당해야 하나</a>
          <a href="#section-architecture" className="text-foreground-muted hover:text-foreground">2. 기본 아키텍처</a>
          <a href="#section-scenario" className="text-foreground-muted hover:text-foreground">3. 시나리오</a>
          {guide.steps.map((s, i) => (
            <a key={i} href={`#step-${i + 1}`} className="pl-3 text-foreground-muted hover:text-foreground">
              {i + 1}. {s.action.label}
            </a>
          ))}
          <a href="#section-checklist" className="text-foreground-muted hover:text-foreground">4. 체크리스트</a>
          {guide.pitfalls.length > 0 && <a href="#section-pitfalls" className="text-foreground-muted hover:text-foreground">5. 틀린 대응</a>}
        </nav>
      </div>
    </div>
  );
}

function StepCard({ index, step, domain, last }: { index: number; step: GuideStep; domain: string; last: boolean }) {
  const kind = ACTION_KIND[step.action.kind];
  const labQuery = new URLSearchParams(Object.entries(step.traitsBefore).map(([k, v]) => [k, String(v)])).toString();
  return (
    <div className="flex flex-col gap-3">
      <Card as="section" className="flex flex-col gap-4">
        <div id={`step-${index + 1}`} className="flex scroll-mt-6 items-start gap-3">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-accent text-sm font-semibold text-accent-foreground">{index + 1}</span>
          <div>
            <h3 className="font-semibold">{step.title}</h3>
            <p className="mt-1 text-sm text-foreground-muted">{step.situation}</p>
          </div>
        </div>

        <div className="grid gap-4 lg:grid-cols-2">
          <div className="flex flex-col gap-2">
            <Label>신호 — 무엇이 보이나</Label>
            <p className="text-sm">{step.signal}</p>
          </div>
          <div className="flex flex-col gap-2">
            <Label>진단 — 왜 그런가</Label>
            <p className="text-sm">{step.diagnosis}</p>
            {step.concepts.length > 0 && (
              <div className="flex flex-wrap gap-1.5 text-xs">
                {step.concepts.map((c) => (
                  <Link key={c.riskKey} href={`/learning/${c.riskKey}`} className="rounded-full border border-border px-2 py-0.5 hover:border-accent">
                    개념: {c.label}
                  </Link>
                ))}
              </div>
            )}
            <div className="mt-2 rounded-lg border border-border bg-background p-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className={`rounded-full border px-2 py-0.5 text-[11px] font-semibold ${kind.className}`}>{kind.label}</span>
                <span className="text-sm font-medium">{step.action.label}</span>
              </div>
              <p className="mt-1 text-sm text-foreground-muted">{step.action.why}</p>
            </div>
          </div>
        </div>

        <BeforeAfterDiagram domain={domain} before={step.before} after={step.after} actionLabel={step.action.label} />

        <div className="flex flex-col gap-2">
          <Label>결과 — 무엇이 바뀌었나</Label>
          <ContentBlocks
            blocks={[
              {
                type: "numbers",
                title: "",
                domain,
                incident: step.incident,
                changeLabel: step.action.label,
                metrics: Object.keys(step.metrics) as GuideStep["claims"][number]["metric"][],
                claims: step.claims,
                resolved: step.metrics,
              },
            ]}
          />
        </div>

        {step.tradeoff && (
          <aside className="rounded-r-lg border-l-4 border-foreground-muted bg-surface px-4 py-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-foreground-muted">대가와 남은 문제</p>
            <div className="mt-1 text-sm text-foreground-muted">
              <RichText body={step.tradeoff} />
            </div>
          </aside>
        )}

        {step.blocks.length > 0 && <ContentBlocks blocks={step.blocks} />}

        <div className="flex flex-wrap gap-2 border-t border-border pt-3">
          {step.links.labs.map((slug) => (
            <Button key={slug} href={`/learning/labs/${slug}${labQuery ? `?${labQuery}` : ""}`} size="sm" variant="secondary">
              이 단계를 랩에서 직접 →
            </Button>
          ))}
          {step.links.challenges.map((slug) => (
            <Button key={slug} href={`/bridge?challenge=${slug}`} size="sm" variant="secondary">
              Build: {slug}
            </Button>
          ))}
          {step.links.failurePattern && (
            <Button href={`/learning/failures/${step.links.failurePattern}`} size="sm" variant="secondary">
              이 장애 패턴 자세히
            </Button>
          )}
        </div>
      </Card>
      {!last && (
        <p className="pl-4 text-xs text-foreground-muted" aria-hidden>
          ↓ 남은 문제가 다음 단계의 상황이 됩니다
        </p>
      )}
    </div>
  );
}

/** One system picture with a 지금 / 바꾼 뒤 switch — flipping it shows where the red moves. */
function BeforeAfterDiagram({ domain, before, after, actionLabel }: { domain: string; before: SystemState; after: SystemState; actionLabel: string }) {
  const [showAfter, setShowAfter] = useState(false);
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        <Label>시스템 상태</Label>
        <div className="flex overflow-hidden rounded-lg border border-border text-xs" role="group" aria-label="시스템 상태 보기">
          {[false, true].map((value) => (
            <button
              key={String(value)}
              type="button"
              onClick={() => setShowAfter(value)}
              aria-pressed={showAfter === value}
              className={`px-3 py-1 ${showAfter === value ? "bg-accent text-accent-foreground" : "text-foreground-muted hover:text-foreground"}`}
            >
              {value ? `바꾼 뒤 (${actionLabel})` : "지금"}
            </button>
          ))}
        </div>
      </div>
      <SystemDiagram domain={domain} state={showAfter ? after : before} />
    </div>
  );
}

function Label({ children }: { children: React.ReactNode }) {
  return <p className="text-xs font-semibold uppercase tracking-wide text-foreground-muted">{children}</p>;
}
