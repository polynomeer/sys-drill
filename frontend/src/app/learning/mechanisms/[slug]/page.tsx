"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { MECHANISMS } from "@/lib/mechanisms";
import { RateLimiterWidget } from "@/components/mechanisms/RateLimiterWidget";

/** docs/LEARNING_DEEPENING_PLAN.md L17 (PLAN.md Round E41, ADR-0055) — one mechanism widget. Computed in the browser; nothing is sent. */
export default function MechanismPage() {
  const { slug } = useParams<{ slug: string }>();
  const mechanism = MECHANISMS.find((m) => m.slug === slug);
  if (!mechanism) return <p className="mx-auto w-full max-w-7xl p-8 text-sm text-danger">메커니즘을 찾을 수 없습니다.</p>;
  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-5 p-4 sm:p-8">
      <div>
        <Link href={`/learning/${mechanism.riskKey}`} className="text-sm text-foreground-muted hover:text-foreground">
          ← 개념으로
        </Link>
        <p className="mt-2 text-xs font-semibold uppercase tracking-wide text-foreground-muted">메커니즘 시뮬레이터</p>
        <h1 className="mt-1 text-2xl font-semibold">{mechanism.title}</h1>
        <p className="mt-1 max-w-[72ch] text-sm text-foreground-muted">{mechanism.summary} 브라우저에서 계산하며 기록되지 않습니다.</p>
      </div>
      {mechanism.slug === "rate-limiter" && <RateLimiterWidget />}
    </div>
  );
}
