"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CircleCheck } from "lucide-react";
import { ApiError, CertificationStatus, ScenarioSummary, getMyCertification, listScenarios } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { DOMAIN_TITLES } from "@/lib/designGuidance";
import { DomainIcon } from "@/lib/domainIcons";
import { TRACK_DOMAINS, trackDrills } from "@/lib/tracks";
import { CardGridSkeleton } from "@/components/ui/Skeleton";

/** docs/CODECRAFTERS_BENCHMARK.md §3.7 — the track index, one card per simulation domain. */
export default function TracksPage() {
  const router = useRouter();
  const [scenarios, setScenarios] = useState<ScenarioSummary[] | null>(null);
  const [certification, setCertification] = useState<CertificationStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/login");
      return;
    }
    listScenarios()
      .then(setScenarios)
      .catch((err) => setError(err instanceof ApiError ? err.message : "트랙을 불러오지 못했습니다."));
    // Certification only decorates the cards; the page works without it.
    getMyCertification().then(setCertification).catch(() => setCertification(null));
  }, [router]);

  if (error) return <p className="p-8 text-sm text-danger">{error}</p>;
  if (!scenarios)
    return (
      <div className="mx-auto w-full max-w-5xl p-8">
        <CardGridSkeleton count={6} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" />
      </div>
    );

  const passed = new Set(certification?.domains.filter((d) => d.passed).map((d) => d.domain));

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6 p-8">
      <div>
        <h1 className="text-2xl font-semibold">도메인 트랙</h1>
        <p className="mt-1 text-sm text-foreground-muted">
          한 도메인의 설계·구현·장애 대응 Drill과 관련 개념을 한곳에서 이어서 훈련하세요. 트랙의 공식 Drill을 통과하면 그
          도메인 인증이 완료됩니다.
        </p>
      </div>
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {TRACK_DOMAINS.map((domain) => {
          const drills = trackDrills(scenarios, domain);
          const completions = drills.reduce((sum, s) => sum + (s.completedCount ?? 0), 0);
          return (
            <li key={domain}>
              <Link
                href={`/tracks/${domain}`}
                className="group flex h-full flex-col gap-3 rounded-xl border border-border bg-surface p-5 transition-colors hover:border-accent/40"
              >
                <div className="flex items-start justify-between gap-3">
                  <p className="font-semibold group-hover:text-accent">{DOMAIN_TITLES[domain]}</p>
                  <DomainIcon domain={domain} className="h-5 w-5 shrink-0 text-foreground-muted" />
                </div>
                <p className="mt-auto flex items-center justify-between text-xs text-foreground-muted">
                  <span>
                    Drill {drills.length}개 · 완료 {completions}회
                  </span>
                  {passed.has(domain) && (
                    <span className="flex items-center gap-1 text-success">
                      <CircleCheck className="h-3.5 w-3.5" aria-hidden /> 인증
                    </span>
                  )}
                </p>
              </Link>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
