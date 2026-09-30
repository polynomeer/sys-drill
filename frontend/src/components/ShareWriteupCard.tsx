"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import type { SessionVisibility } from "@/lib/api";
import { getSessionVisibility, setSessionVisibility } from "@/lib/api";
import { Card } from "@/components/ui/Card";

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.2 / ADR-0041 — 리포트 화면의 공개 토글.
 *
 * 기본은 비공개이고 공개는 명시적 선택이다(ARCHITECTURE §13). 그래서 이 카드는
 * "공개 중"을 기본 상태처럼 보이게 하지 않고, 켜는 순간 **무엇이 공개되는지**를
 * 먼저 말한다 — 설계 답안 원문과 점수가 그대로 간다.
 */
export function ShareWriteupCard({ sessionId }: { sessionId: string }) {
  const [state, setState] = useState<SessionVisibility | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getSessionVisibility(sessionId)
      .then(setState)
      .catch(() => setState(null));
  }, [sessionId]);

  async function update(visibility: "PRIVATE" | "PUBLIC", anonymous: boolean) {
    if (saving) return;
    setSaving(true);
    setError(null);
    try {
      setState(await setSessionVisibility(sessionId, visibility, anonymous));
    } catch {
      setError("설정을 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  if (!state) return null;

  const isPublic = state.visibility === "PUBLIC";

  return (
    <Card as="section">
      <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground-muted">풀이 공개</h2>
        {state.scenarioId && (
          <Link href={`/writeups/${state.scenarioId}`} className="text-xs underline">
            이 시나리오의 다른 풀이 보기 →
          </Link>
        )}
      </div>

      {!state.completed ? (
        <p className="text-sm text-foreground-muted">
          세션을 끝까지 마치면 이 풀이를 공개할 수 있습니다.
        </p>
      ) : (
        <>
          <p className="mb-3 text-sm text-foreground-muted">
            {isPublic
              ? "공개 중입니다. 이 시나리오를 완료한 사람만 볼 수 있습니다 — 아직 풀지 않은 사람에게는 보이지 않습니다."
              : "공개하면 단계별 설계 답안 원문과 점수가 함께 공개됩니다. 열람은 이 시나리오를 완료한 사람으로 제한됩니다."}
          </p>

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => update(isPublic ? "PRIVATE" : "PUBLIC", state.anonymous)}
              disabled={saving}
              className={`rounded px-3 py-1 text-sm ${
                isPublic ? "bg-surface-elevated text-foreground-muted" : "bg-accent/15 text-accent"
              } disabled:opacity-50`}
            >
              {isPublic ? "공개 철회" : "풀이 공개하기"}
            </button>

            {/* 익명 선택은 공개 중일 때만 의미가 있다 — 비공개 상태에서 미리 고를 것이 없다. */}
            {isPublic && (
              <label className="flex items-center gap-1.5 text-sm text-foreground-muted">
                <input
                  type="checkbox"
                  checked={state.anonymous}
                  disabled={saving}
                  onChange={(e) => update("PUBLIC", e.target.checked)}
                />
                닉네임 대신 익명으로 표시
              </label>
            )}
          </div>
        </>
      )}

      {error && <p className="mt-2 text-sm text-danger">{error}</p>}
    </Card>
  );
}
