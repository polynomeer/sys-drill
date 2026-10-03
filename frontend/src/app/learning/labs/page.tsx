"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type LabSummary, listLabs } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

const KIND_META: Record<LabSummary["kind"], { title: string; description: string }> = {
  ENGINE: {
    title: "시뮬레이션 랩",
    description: "값을 바꾸기 전에 예측하고, 실행하고, 장애를 켜 봅니다. Drill 인시던트와 같은 수식입니다.",
  },
  CAPACITY: {
    title: "Capacity Lab — 규모 추정",
    description: "트래픽과 저장 용량을 어림합니다. 정확한 숫자가 아니라 자릿수가 맞는지(2배 이내)를 봅니다.",
  },
};

/** docs/LEARNING_EXPANSION_PLAN.md L5·L6 (PLAN.md Round E9) — every lab, by kind. */
export default function LabsPage() {
  const router = useRouter();
  const [labs, setLabs] = useState<LabSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    listLabs().then(setLabs).catch(() => setError("랩 목록을 불러오지 못했습니다."));
  }, [router]);

  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-8 p-6 md:p-8">
      <div>
        <Link href="/learning" className="text-sm text-foreground-muted hover:text-foreground">
          ← Learning
        </Link>
        <h1 className="mt-3 text-2xl font-semibold">랩</h1>
        <p className="mt-1 text-sm text-foreground-muted">설명을 읽는 대신 값을 바꿔서 현상을 발견하는 곳입니다.</p>
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
      {!labs && !error && <LoadingState />}
      {labs &&
        (["ENGINE", "CAPACITY"] as const).map((kind) => {
          const ofKind = labs.filter((l) => l.kind === kind);
          if (ofKind.length === 0) return null;
          return (
            <section key={kind} className="flex flex-col gap-3">
              <div>
                <h2 className="font-semibold">{KIND_META[kind].title}</h2>
                <p className="text-sm text-foreground-muted">{KIND_META[kind].description}</p>
              </div>
              <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {ofKind.map((lab) => (
                  <li key={lab.slug}>
                    <Link href={`/learning/labs/${lab.slug}`} className="block h-full">
                      <Card className="h-full transition-colors hover:border-accent/40">
                        <p className="font-medium">{lab.title}</p>
                        <p className="mt-1 text-sm text-foreground-muted">{lab.summary}</p>
                      </Card>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          );
        })}
    </div>
  );
}
