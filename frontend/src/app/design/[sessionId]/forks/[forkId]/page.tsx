"use client";

import { useParams } from "next/navigation";
import { ForkView } from "./ForkView";

/** docs/DRILLS_EXPANSION_PLAN.md M6 (PLAN.md Round E14) — Counterfactual Replay on my own session. */
export default function ForkPage() {
  const { sessionId, forkId } = useParams<{ sessionId: string; forkId: string }>();
  return <ForkView forkId={forkId} backHref={`/design/${sessionId}/replay`} backLabel="리플레이로" originalLabel="원래 대응" />;
}
