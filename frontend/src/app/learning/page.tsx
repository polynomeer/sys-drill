"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { LearningCategory, LearningPath, getLearningConcepts, getLearningPath } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";
import { LearningPathPanel } from "@/components/LearningPathPanel";
import { DESIGN_GUIDANCE_BY_DOMAIN, DOMAIN_TITLES, INCIDENT_GUIDANCE } from "@/lib/designGuidance";
import { MASTERY_META } from "@/lib/mastery";

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §5.2 (슬라이스 2).
 *
 * 이전에는 프론트 상수(riskLabels.ts)를 그대로 나열해 누가 보든 같은 화면이었다.
 * 이제 개념은 DB에서 오고(ADR-0039), 내가 지적받은 횟수가 함께 내려와 약점이
 * 많은 역량이 위로 올라온다 — 같은 목록이 사람마다 다른 순서로 보인다.
 */
export default function LearningPage() {
  const router = useRouter();
  const [categories, setCategories] = useState<LearningCategory[] | null>(null);
  const [path, setPath] = useState<LearningPath | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    getLearningConcepts()
      .then(setCategories)
      .catch(() => setError("개념 목록을 불러오지 못했습니다."));
    // 경로는 부가 패널이다 — 실패해도 개념 목록은 그대로 보여준다.
    getLearningPath().then(setPath).catch(() => setPath(null));
  }, [router]);

  const totalWeakness = (categories ?? []).reduce((sum, c) => sum + c.myWeaknessCount, 0);

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">Learning</h1>
        <p className="mt-1 text-sm text-foreground-muted">
          채점 기준과 같은 어휘로 쓰인 개념 레퍼런스입니다 — 훈련 중 “놓친 점”에서 만나는 그 개념들입니다.
        </p>
      </div>

      {/* PLAN.md Round E18 (L7) — 25개 개념의 지도 */}
      <Link
        href="/learning/map"
        className="flex items-center justify-between gap-3 rounded-xl border border-border bg-surface p-4 transition-colors hover:border-accent/40"
      >
        <span>
          <span className="block font-medium">지식 맵</span>
          <span className="block text-sm text-foreground-muted">채점 엔진이 아는 25개 개념과 선행 관계 — 내 숙련 상태가 노드에 표시됩니다.</span>
        </span>
        <span className="text-accent">→</span>
      </Link>

      {/* docs/LEARNING_EXPANSION_PLAN.md §5 — 랩: 읽는 대신 값을 바꿔 보는 곳 */}
      <Link
        href="/learning/labs"
        className="flex items-center justify-between gap-3 rounded-xl border border-border bg-surface p-4 transition-colors hover:border-accent/40"
      >
        <span>
          <span className="block font-medium">랩</span>
          <span className="block text-sm text-foreground-muted">규모 추정(Capacity Lab)과 시뮬레이션 랩 — 값을 바꿔서 현상을 발견합니다.</span>
        </span>
        <span className="text-accent">→</span>
      </Link>

      {error && <p className="text-sm text-danger">{error}</p>}
      {!categories && !error && <LoadingState />}

      {categories && (
        <>
          {path && <LearningPathPanel path={path} />}
          <p className="text-xs text-foreground-muted">
            {(["NOT_STARTED", "WEAK", "PRACTICED", "CONFIDENT"] as const).map((level) => (
              <span key={level} className="mr-3">
                {MASTERY_META[level].symbol} {MASTERY_META[level].label}
              </span>
            ))}
            — 숙련은 훈련 결과에서만 파생합니다(읽기만으로는 바뀌지 않음).
          </p>
          {totalWeakness > 0 && (
            <p className="text-xs text-foreground-muted">
              아래 개념 목록은 약점이 많은 역량부터 정렬했습니다 (총 {totalWeakness}회 지적).
            </p>
          )}

          <section className="flex flex-col gap-5">
            {categories.map((category) => (
              <div key={category.category} className="flex flex-col gap-2">
                <div className="flex items-baseline gap-2">
                  <h2 className="text-sm font-semibold">{category.label}</h2>
                  {category.myWeaknessCount > 0 && (
                    <span className="rounded-full bg-danger/15 px-2 py-0.5 text-xs text-danger">
                      내 약점 {category.myWeaknessCount}회
                    </span>
                  )}
                  <span className="text-xs text-foreground-muted">개념 {category.concepts.length}개</span>
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  {category.concepts.map((concept) => (
                    <Link key={concept.riskKey} href={`/learning/${concept.riskKey}`} className="block">
                      <Card className="h-full transition-colors hover:border-accent">
                        <div className="mb-1 flex items-baseline justify-between gap-2">
                          <h3 className="font-medium">
                            {concept.mastery && (
                              <span className="mr-1.5" title={`${MASTERY_META[concept.mastery].label} — ${MASTERY_META[concept.mastery].hint}`} aria-label={MASTERY_META[concept.mastery].label}>
                                {MASTERY_META[concept.mastery].symbol}
                              </span>
                            )}
                            {concept.label}
                          </h3>
                          {concept.myWeaknessCount > 0 && (
                            <span className="shrink-0 text-xs text-danger">{concept.myWeaknessCount}회 놓침</span>
                          )}
                        </div>
                        <p className="text-sm text-foreground-muted">{concept.summary}</p>
                        {concept.readingMinutes && <p className="mt-2 text-[11px] text-foreground-muted">읽는 데 약 {concept.readingMinutes}분</p>}
                      </Card>
                    </Link>
                  ))}
                </div>
              </div>
            ))}
          </section>
        </>
      )}

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-semibold text-foreground-muted">도메인별 설계 가이드</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          {Object.keys(DESIGN_GUIDANCE_BY_DOMAIN).map((domain) => (
            <Card key={domain} as="section">
              <h3 className="mb-2 font-medium">{DOMAIN_TITLES[domain] ?? domain}</h3>
              <ul className="list-inside list-disc space-y-1 text-sm text-foreground-muted">
                {DESIGN_GUIDANCE_BY_DOMAIN[domain].map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </Card>
          ))}
          <Card as="section">
            <h3 className="mb-2 font-medium">장애 대응 회고</h3>
            <ul className="list-inside list-disc space-y-1 text-sm text-foreground-muted">
              {INCIDENT_GUIDANCE.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </Card>
        </div>
      </section>
    </div>
  );
}
