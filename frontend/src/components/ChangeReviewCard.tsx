"use client";

import { useEffect, useState } from "react";
import { type ChangeReview, getChangeReview, pickChangeReview } from "@/lib/api";
import { Card } from "@/components/ui/Card";

/**
 * docs/DRILLS_EXPANSION_PLAN.md M9 (PLAN.md Round E30) — before the incident: "which of this release's
 * changes is riskiest?". After recovery is declared (or the session ends): the real cause next to my pick.
 * Renders nothing for scenarios without a change list.
 */
export function ChangeReviewCard({ sessionId }: { sessionId: string }) {
  const [data, setData] = useState<ChangeReview | null>(null);

  useEffect(() => {
    getChangeReview(sessionId).then(setData).catch(() => setData(null));
  }, [sessionId]);

  if (!data?.available) return null;
  const revealed = data.culpritId !== null;

  async function pick(id: string) {
    setData(await pickChangeReview(sessionId, id));
  }

  return (
    <Card as="section" className="flex flex-col gap-2 text-sm">
      <h2 className="font-semibold">릴리즈 변경 검토</h2>
      <p className="text-xs text-foreground-muted">
        {revealed
          ? "배포 전에 고른 위험 변경과 실제 원인입니다."
          : data.locked
            ? "배포 전에 고른 변경입니다. 실제 원인은 복구를 선언한 뒤 공개됩니다."
            : "이번 배포에 들어간 변경입니다. 가장 위험해 보이는 하나를 고르세요 — 장애가 끝나면 실제 원인과 대조합니다."}
      </p>
      <ul className="flex flex-col gap-1">
        {data.changes.map((c) => {
          const mine = data.pick === c.id;
          const culprit = revealed && data.culpritId === c.id;
          return (
            <li key={c.id}>
              <button
                type="button"
                disabled={data.locked}
                onClick={() => pick(c.id)}
                className={`w-full rounded-lg border px-3 py-1.5 text-left ${
                  culprit ? "border-danger text-foreground" : mine ? "border-accent" : "border-border text-foreground-muted"
                } disabled:cursor-default`}
              >
                {c.text}
                {mine && <span className="ml-2 text-xs text-accent">내 선택</span>}
                {culprit && <span className="ml-2 text-xs text-danger">실제 원인</span>}
              </button>
            </li>
          );
        })}
      </ul>
      {revealed && data.explanation && <p className="text-xs text-foreground-muted">{data.explanation}</p>}
    </Card>
  );
}
