"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  CertificationStatus,
  ScenarioSummary,
  getMyCertification,
  listMarketplaceScenarios,
  listScenarios,
} from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { LoadingState } from "@/components/ui/LoadingState";
import { RankingPanel } from "@/components/RankingPanel";
import { CommunityHomeFeeds } from "@/components/CommunityHomeFeeds";
import { WeeklyPuzzleCard } from "@/components/WeeklyPuzzleCard";

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.3~§6.6 (슬라이스 4~7).
 *
 * 이전에는 GitHub Issues 링크 한 장이었다. 지금은 네 가지를 보여준다 —
 * 내 Drill Score 와 랭킹(ADR-0042), 공유 가능한 내 인증 프로필, 다른 사람이
 * 만든 공개 시나리오(실측 난이도 신호 포함), 그리고 시나리오별 토론 스레드
 * (ADR-0040). 마지막 항목이 GitHub Issues 링크를 대체한 자리다.
 *
 * 벤치마크(§6.1)는 이 화면이 아니라 세션 리포트·포스트모템에 붙는다 —
 * 비교는 내 결과 옆에서만 의미가 있기 때문이다.
 */
export default function CommunityPage() {
  const router = useRouter();
  const [certification, setCertification] = useState<CertificationStatus | null>(null);
  const [scenarios, setScenarios] = useState<ScenarioSummary[] | null>(null);
  const [officialScenarios, setOfficialScenarios] = useState<ScenarioSummary[] | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    getMyCertification().then(setCertification).catch(() => setCertification(null));
    listMarketplaceScenarios().then(setScenarios).catch(() => setScenarios([]));
    // 토론 스레드는 공식 시나리오 단위다 — 마켓플레이스 목록과 출처가 다르다.
    listScenarios().then(setOfficialScenarios).catch(() => setOfficialScenarios([]));
  }, [router]);

  // certification 은 SSR 시점에 null 이라 window 에 닿지 않지만, 순서가 바뀌어도
  // 깨지지 않도록 명시적으로 막아둔다.
  const profileUrl =
    certification && typeof window !== "undefined"
      ? `${window.location.origin}/certifications/${certification.userId}`
      : null;
  const passedCount = certification?.domains.filter((d) => d.passed).length ?? 0;

  async function copyProfileUrl() {
    if (!profileUrl) return;
    try {
      await navigator.clipboard.writeText(profileUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  // 완료 수가 많은 순 — 표본이 많을수록 평균 점수가 믿을 만한 난이도 신호가 된다.
  const ranked = (scenarios ?? [])
    .slice()
    .sort((a, b) => (b.completedCount ?? 0) - (a.completedCount ?? 0));

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">Community</h1>
        <p className="mt-1 text-sm text-foreground-muted">
          내 점수와 순위를 확인하고, 인증을 공유하고, 다른 사람이 만든 시나리오를 찾아보세요.
        </p>
      </div>

      {/* docs/COMMUNITY_EXPANSION_PLAN.md C11 (PLAN.md Round E23) */}
      <CommunityHomeFeeds />
      {/* docs/COMMUNITY_EXPANSION_PLAN.md C12 (PLAN.md Round E28) */}
      <WeeklyPuzzleCard />

      <RankingPanel />

      <Card as="section">
        <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-sm font-semibold">내 공개 프로필</h2>
          {certification && (
            <span className="text-xs text-foreground-muted">
              인증 통과 {passedCount} / {certification.domains.length} 도메인
            </span>
          )}
        </div>
        {!certification ? (
          <p className="text-sm text-foreground-muted">인증 현황을 불러오지 못했습니다.</p>
        ) : (
          <>
            <p className="mb-3 text-sm text-foreground-muted">
              로그인 없이 누구나 볼 수 있는 검증 페이지입니다. 약점 프로필과 실패 이력은 포함되지 않습니다.
            </p>
            <div className="flex flex-wrap items-center gap-2">
              <Button href={`/certifications/${certification.userId}`} size="sm" variant="secondary">
                내 프로필 보기
              </Button>
              <Button size="sm" variant="secondary" onClick={copyProfileUrl}>
                {copied ? "복사됨 ✓" : "공유 링크 복사"}
              </Button>
            </div>
          </>
        )}
      </Card>

      <Card as="section">
        <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-sm font-semibold">공개 시나리오</h2>
          <Link href="/marketplace" className="text-xs underline">
            전체 보기 · 내 시나리오 공개하기
          </Link>
        </div>
        {scenarios === null ? (
          <LoadingState />
        ) : ranked.length === 0 ? (
          <p className="text-sm text-foreground-muted">
            아직 공개된 시나리오가 없습니다. 직접 만들어 공개하면 여기에 표시됩니다.
          </p>
        ) : (
          <ul className="divide-y divide-border">
            {ranked.slice(0, 8).map((scenario) => (
              <li key={scenario.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                <span className="flex flex-wrap items-center gap-2">
                  {scenario.title}
                  {scenario.difficulty && <Badge>{scenario.difficulty}</Badge>}
                  {scenario.creatorNickname && (
                    <span className="text-xs text-foreground-muted">by {scenario.creatorNickname}</span>
                  )}
                </span>
                <span className="text-xs text-foreground-muted">
                  {(scenario.completedCount ?? 0) > 0
                    ? `완료 ${scenario.completedCount}명${
                        typeof scenario.averageScore === "number" ? ` · 평균 ${scenario.averageScore}점` : ""
                      }`
                    : "아직 완료한 사람이 없습니다"}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card as="section">
        <h2 className="mb-2 text-sm font-semibold">토론</h2>
        <p className="mb-3 text-sm text-foreground-muted">
          질문은 시나리오 안에서 합니다 — 내 답안과 받은 지적이 함께 있는 곳이라야 &ldquo;왜 이 설계가
          감점인가&rdquo;를 제대로 물을 수 있기 때문입니다.
        </p>
        {officialScenarios === null ? (
          <LoadingState />
        ) : (
          <ul className="divide-y divide-border">
            {officialScenarios.slice(0, 6).map((scenario) => (
              <li key={scenario.id} className="py-2">
                <Link
                  href={`/discussions/${scenario.id}`}
                  className="flex flex-wrap items-center justify-between gap-2 text-sm hover:underline"
                >
                  <span className="flex flex-wrap items-center gap-2">
                    {scenario.title}
                    {scenario.difficulty && <Badge>{scenario.difficulty}</Badge>}
                  </span>
                  <span className="text-xs text-foreground-muted">스레드 열기 →</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
