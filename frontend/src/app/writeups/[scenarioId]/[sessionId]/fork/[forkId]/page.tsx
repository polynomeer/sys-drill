"use client";

import { useParams } from "next/navigation";
import { ForkView } from "@/app/design/[sessionId]/forks/[forkId]/ForkView";

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C9 (PLAN.md Round E16) — "Fork My Run": someone else's
 * incident from the moment I picked, with my moves instead. "그럼 같은 상태에서 직접 해봐."
 */
export default function WriteupForkPage() {
  const { scenarioId, sessionId, forkId } = useParams<{ scenarioId: string; sessionId: string; forkId: string }>();
  return (
    <ForkView
      forkId={forkId}
      backHref={`/writeups/${scenarioId}/${sessionId}`}
      backLabel="풀이로"
      originalLabel="이 풀이의 대응"
    />
  );
}
