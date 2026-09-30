"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { CircleCheck, Terminal } from "lucide-react";
import {
  CertificationStatus,
  LearningConceptSummary,
  RankingBoard,
  ScenarioSummary,
  getLearningConcepts,
  getMyCertification,
  getRankingBoard,
  listScenarios,
} from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { DOMAIN_TITLES } from "@/lib/designGuidance";
import { DomainIcon } from "@/lib/domainIcons";
import { TRACK_BUILD, trackDrills } from "@/lib/tracks";
import { DrillCard } from "@/components/DrillCard";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { LoadingState } from "@/components/ui/LoadingState";

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.7 — one domain's track: its Drills,
 * the Build challenge wired to it, the concepts that domain exercises, and
 * (rail) my certification for the domain plus the domain ranking. Built only
 * from existing endpoints; each section degrades on its own if its call fails.
 */
export default function TrackPage() {
  const router = useRouter();
  const params = useParams<{ domain: string }>();
  const domain = params.domain;
  const title = DOMAIN_TITLES[domain];

  const [scenarios, setScenarios] = useState<ScenarioSummary[] | null>(null);
  const [concepts, setConcepts] = useState<LearningConceptSummary[]>([]);
  const [certification, setCertification] = useState<CertificationStatus | null>(null);
  const [ranking, setRanking] = useState<RankingBoard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }
    listScenarios()
      .then(setScenarios)
      .catch(() => setError("트랙을 불러오지 못했습니다."));
    getLearningConcepts()
      .then((categories) =>
        setConcepts(categories.flatMap((c) => c.concepts).filter((c) => (c.relatedDomains ?? []).includes(domain))),
      )
      .catch(() => setConcepts([]));
    getMyCertification().then(setCertification).catch(() => setCertification(null));
    getRankingBoard("domain", domain).then(setRanking).catch(() => setRanking(null));
  }, [domain, router]);

  if (!title) {
    return (
      <div className="mx-auto max-w-5xl p-8">
        <p className="text-sm text-danger">존재하지 않는 트랙입니다.</p>
        <Link href="/tracks" className="mt-2 inline-block text-sm underline">
          트랙 목록으로
        </Link>
      </div>
    );
  }
  if (error) return <p className="p-8 text-sm text-danger">{error}</p>;
  if (!scenarios) return <LoadingState className="p-8" />;

  const drills = trackDrills(scenarios, domain);
  const completions = drills.reduce((sum, s) => sum + (s.completedCount ?? 0), 0);
  const build = TRACK_BUILD[domain];
  const myDomain = certification?.domains.find((d) => d.domain === domain);

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-8 p-8">
      <header className="flex flex-col gap-3 border-b border-border pb-8">
        <Link href="/tracks" className="text-xs text-foreground-muted hover:text-foreground">
          ← 도메인 트랙
        </Link>
        <div className="flex items-center gap-3">
          <DomainIcon domain={domain} className="h-9 w-9 shrink-0 text-accent" />
          <h1 className="break-keep text-3xl font-semibold md:text-4xl">{title} 마스터</h1>
        </div>
        <p className="text-sm text-foreground-muted">
          Drill {drills.length}개 · 누적 완료 {completions}회{concepts.length > 0 && ` · 관련 개념 ${concepts.length}개`}
        </p>
      </header>

      <div className="grid gap-8 lg:grid-cols-[1fr_280px]">
        <div className="flex min-w-0 flex-col gap-8">
          <section className="flex flex-col gap-3">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground-muted">Drill</h2>
            {drills.length === 0 ? (
              <EmptyState message="이 도메인에는 아직 Drill이 없습니다." />
            ) : (
              <ul className="grid gap-4 md:grid-cols-2">
                {drills.map((s) => (
                  <li key={s.id}>
                    <DrillCard scenario={s} />
                  </li>
                ))}
              </ul>
            )}
          </section>

          {build && (
            <section className="flex flex-col gap-3">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground-muted">Build</h2>
              <Link
                href={build.href}
                className="group flex items-center justify-between gap-3 rounded-xl border border-border bg-surface p-5 transition-colors hover:border-accent/40"
              >
                <div>
                  <p className="font-semibold group-hover:text-accent">{build.title}</p>
                  <p className="mt-1 text-sm text-foreground-muted">핵심 컴포넌트를 직접 구현한 뒤 이 도메인의 설계로 이어갑니다.</p>
                </div>
                <Terminal className="h-5 w-5 shrink-0 text-foreground-muted" aria-hidden strokeWidth={1.75} />
              </Link>
            </section>
          )}

          {concepts.length > 0 && (
            <section className="flex flex-col gap-3">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground-muted">이 도메인에서 쓰는 개념</h2>
              <ul className="grid gap-2 sm:grid-cols-2">
                {concepts.map((c) => (
                  <li key={c.riskKey}>
                    <Link
                      href={`/learning/${c.riskKey}`}
                      className="flex h-full flex-col rounded-lg border border-border px-4 py-3 text-sm transition-colors hover:border-accent/40"
                    >
                      <span className="flex items-center justify-between gap-2 font-medium">
                        {c.label}
                        {c.myWeaknessCount > 0 && (
                          <span className="shrink-0 rounded-full bg-danger/15 px-2 py-0.5 text-[11px] text-danger">
                            {c.myWeaknessCount}회 놓침
                          </span>
                        )}
                      </span>
                      <span className="mt-1 text-xs text-foreground-muted">{c.summary}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>

        <aside className="flex flex-col gap-4">
          {myDomain && (
            <Card as="section">
              <p className="text-xs font-semibold uppercase tracking-wide text-foreground-muted">내 인증</p>
              {myDomain.passed ? (
                <p className="mt-2 flex items-center gap-1.5 font-medium text-success">
                  <CircleCheck className="h-4 w-4" aria-hidden /> 인증 완료
                </p>
              ) : (
                <p className="mt-2 text-sm text-foreground-muted">아직 인증 전입니다. 이 트랙의 공식 Drill을 통과하면 인증됩니다.</p>
              )}
              {myDomain.bestScore !== null && <p className="mt-1 text-xs text-foreground-muted">최고 {myDomain.bestScore}점</p>}
              <Link href="/certifications" className="mt-3 inline-block text-xs underline">
                전체 인증 현황
              </Link>
            </Card>
          )}

          {ranking && (
            <Card as="section">
              <p className="text-xs font-semibold uppercase tracking-wide text-foreground-muted">{title} 랭킹</p>
              {ranking.entries.length === 0 ? (
                <p className="mt-2 text-sm text-foreground-muted">아직 이 도메인을 완료한 참여자가 없습니다.</p>
              ) : (
                <ol className="mt-2 flex flex-col gap-1.5 text-sm">
                  {ranking.entries.slice(0, 5).map((e) => (
                    <li key={`${e.rank}-${e.nickname}`} className={`flex items-center justify-between gap-2 ${e.isMe ? "text-accent" : ""}`}>
                      <span className="truncate">
                        <span className="mr-2 font-mono text-xs text-foreground-muted">#{e.rank}</span>
                        {e.nickname}
                      </span>
                      <span className="shrink-0 text-xs text-foreground-muted">{e.tierLabel}</span>
                    </li>
                  ))}
                </ol>
              )}
              <p className="mt-2 text-[11px] text-foreground-muted">참여자 {ranking.participantCount}명 · 랭킹 숨김 사용자 제외</p>
            </Card>
          )}
        </aside>
      </div>
    </div>
  );
}
