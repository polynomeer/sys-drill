"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { type LabSummary, listLabs } from "@/lib/api";
import { getStoredToken } from "@/lib/localSession";
import { LoadingState } from "@/components/ui/LoadingState";
import { CapacityLabView } from "./CapacityLabView";
import { EngineLabView } from "./EngineLabView";

/** docs/LEARNING_EXPANSION_PLAN.md L5·L6 — one lab; the view depends on its kind. */
export default function LabPage() {
  const router = useRouter();
  const { slug } = useParams<{ slug: string }>();
  const [lab, setLab] = useState<LabSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    listLabs()
      .then((labs) => {
        const found = labs.find((l) => l.slug === slug);
        if (found) setLab(found);
        else setError("랩을 찾을 수 없습니다.");
      })
      .catch(() => setError("랩을 불러오지 못했습니다."));
  }, [router, slug]);

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 p-6 md:p-8">
      <Link href="/learning/labs" className="text-sm text-foreground-muted hover:text-foreground">
        ← 랩 목록
      </Link>
      {error && <p className="text-sm text-danger">{error}</p>}
      {!lab && !error && <LoadingState />}
      {lab?.kind === "CAPACITY" && <CapacityLabView slug={lab.slug} />}
      {lab?.kind === "ENGINE" && <EngineLabView slug={lab.slug} />}
    </div>
  );
}
