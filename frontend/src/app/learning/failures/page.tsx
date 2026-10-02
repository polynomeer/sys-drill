"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FailurePatternSummary, listFailurePatterns } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

/**
 * PLAN.md Round E19 (docs/LEARNING_EXPANSION_PLAN.md L8) — 장애 패턴 사전. Concepts are design
 * risks; these are what production looks like when the risks fire, one per incident domain.
 */
export default function FailurePatternsPage() {
  const router = useRouter();
  const [patterns, setPatterns] = useState<FailurePatternSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    listFailurePatterns()
      .then(setPatterns)
      .catch(() => setError("장애 패턴을 불러오지 못했습니다."));
  }, [router]);

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6 p-8">
      <div>
        <Link href="/learning" className="text-sm text-foreground-muted hover:text-foreground">
          ← Learning
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">장애 패턴 사전</h1>
        <p className="mt-1 text-sm text-foreground-muted">
          Wargame 인시던트 7개가 실제로 어떤 모습으로 드러나는지 — 증상·지표·로그에서 원인, 그리고 그럴듯하지만 틀린 대응까지.
        </p>
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
      {!patterns && !error && <LoadingState />}
      <div className="grid gap-3 sm:grid-cols-2">
        {patterns?.map((p) => (
          <Link key={p.domain} href={`/learning/failures/${p.domain}`} className="block">
            <Card className="h-full transition-colors hover:border-accent">
              <h2 className="mb-1 font-medium">{p.name}</h2>
              <p className="text-sm text-foreground-muted">{p.summary}</p>
              <ul className="mt-2 list-inside list-disc text-xs text-foreground-muted">
                {p.symptoms.slice(0, 2).map((s) => (
                  <li key={s}>{s}</li>
                ))}
              </ul>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
