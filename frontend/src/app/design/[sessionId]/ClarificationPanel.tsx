"use client";

import { useEffect, useState } from "react";
import { type Clarifications, askClarification, getClarifications } from "@/lib/api";
import { Card } from "@/components/ui/Card";

/**
 * docs/DRILLS_EXPANSION_PLAN.md M1 (PLAN.md Round E8) — "질문하기". The brief is
 * deliberately incomplete; the learner picks which questions to ask before
 * designing. Nothing here scores anything while the drill is running (Hidden
 * Score) — which questions were critical is only shown on the report.
 *
 * Renders nothing for a scenario without clarifying questions.
 */
export function ClarificationPanel({ sessionId }: { sessionId: string }) {
  const [data, setData] = useState<Clarifications | null>(null);
  const [pending, setPending] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getClarifications(sessionId).then(setData).catch(() => setData(null));
  }, [sessionId]);

  if (!data?.available) return null;
  const asked = data.questions.filter((q) => q.asked);
  const open = data.questions.filter((q) => !q.asked);

  async function ask(id: string) {
    setPending(id);
    setError(null);
    try {
      setData(await askClarification(sessionId, id));
    } catch {
      setError("질문하지 못했습니다.");
    } finally {
      setPending(null);
    }
  }

  return (
    <Card as="section" className="text-sm">
      <h2 className="mb-1 text-xs font-semibold uppercase tracking-wide text-foreground-muted">요구사항 확인하기</h2>
      <p className="mb-3 text-xs text-foreground-muted">
        문제 설명은 일부러 짧습니다. 설계 전에 필요한 것을 물어보세요 — 묻지 않은 요구사항은 모르는 채로 설계하게 됩니다.
      </p>

      {asked.length > 0 && (
        <ul className="mb-3 flex flex-col gap-2">
          {asked.map((q) => (
            <li key={q.id} className="rounded-lg border border-border bg-surface-elevated px-3 py-2">
              <p className="text-xs text-foreground-muted">Q. {q.question}</p>
              <p className="mt-0.5">→ {q.answer}</p>
            </li>
          ))}
        </ul>
      )}

      {data.canAsk && open.length > 0 && (
        <div className="flex flex-col gap-1.5">
          {open.map((q) => (
            <button
              key={q.id}
              type="button"
              onClick={() => ask(q.id)}
              disabled={pending !== null}
              className="rounded-lg border border-dashed border-border px-3 py-1.5 text-left text-xs text-foreground-muted hover:border-accent hover:text-foreground disabled:opacity-60"
            >
              {pending === q.id ? "묻는 중..." : `Q. ${q.question}`}
            </button>
          ))}
        </div>
      )}
      {error && <p className="mt-2 text-xs text-danger">{error}</p>}
    </Card>
  );
}
