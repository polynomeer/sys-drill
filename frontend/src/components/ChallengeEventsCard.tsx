"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { type ChallengeEventSummary, listChallengeEvents } from "@/lib/api";
import { Badge } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";

const PHASE: Record<ChallengeEventSummary["phase"], { label: string; variant: "success" | "neutral" | "warning" }> = {
  LIVE: { label: "진행 중", variant: "success" },
  UPCOMING: { label: "예정", variant: "warning" },
  ENDED: { label: "종료 · 디브리프", variant: "neutral" },
};

/** docs/COMMUNITY_EXPANSION_PLAN.md C13 (PLAN.md Round E31) — live and upcoming challenges first, then recent debriefs. */
export function ChallengeEventsCard() {
  const [events, setEvents] = useState<ChallengeEventSummary[] | null>(null);

  useEffect(() => {
    listChallengeEvents().then(setEvents).catch(() => setEvents(null));
  }, []);

  if (!events || events.length === 0) return null;
  const order = { LIVE: 0, UPCOMING: 1, ENDED: 2 } as const;
  const shown = [...events].sort((a, b) => order[a.phase] - order[b.phase]).slice(0, 5);

  return (
    <Card as="section">
      <h2 className="mb-2 text-sm font-semibold">챌린지</h2>
      <ul className="divide-y divide-border">
        {shown.map((e) => (
          <li key={e.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
            <Link href={`/community/events/${e.id}`} className="flex flex-wrap items-center gap-2 hover:underline">
              <Badge variant={PHASE[e.phase].variant}>{PHASE[e.phase].label}</Badge>
              {e.title}
              <span className="text-xs text-foreground-muted">{e.scenarioTitle}</span>
            </Link>
            <span className="text-xs text-foreground-muted">
              {new Date(e.startsAt).toLocaleDateString()} ~ {new Date(e.endsAt).toLocaleDateString()} · 참가 {e.participants}명
            </span>
          </li>
        ))}
      </ul>
    </Card>
  );
}
