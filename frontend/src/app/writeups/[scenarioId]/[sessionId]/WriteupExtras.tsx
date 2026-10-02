"use client";

import { actionLabel } from "@/lib/actionLabels";
import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  ApiError,
  type WriteupComparison,
  type WriteupDetail,
  createFork,
  getWriteupComparison,
  setWriteupNote,
} from "@/lib/api";
import { NODE_KIND_LABELS, TRAIT_LABELS } from "@/lib/designLabels";
import { formatDuration } from "@/lib/metrics";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

const kindLabel = (k: string) => NODE_KIND_LABELS[k] ?? k;
const traitLabel = (k: string) => TRAIT_LABELS[k] ?? k;

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C8 (PLAN.md Round E15) — the summary generated from the
 * session (what was drawn, which settings moved, what was done in the incident) plus the
 * author's own note. The author only has to say *why*.
 */
export function WriteupSummaryCard({ writeup, onUpdated }: { writeup: WriteupDetail; onUpdated: (w: WriteupDetail) => void }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(writeup.note ?? "");
  const [saving, setSaving] = useState(false);
  const summary = writeup.summary;
  const kinds = Object.entries(summary?.nodeKinds ?? {});

  async function save() {
    setSaving(true);
    try {
      onUpdated(await setWriteupNote(writeup.sessionId, draft));
      setEditing(false);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card as="section">
      <h2 className="mb-3 text-sm font-semibold text-foreground-muted">요약</h2>
      {editing ? (
        <div className="mb-3 flex flex-col gap-2">
          <textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            maxLength={2000}
            rows={4}
            placeholder="왜 이렇게 설계했는지, 다시 한다면 무엇을 바꿀지 남겨 주세요."
            className="w-full rounded border border-border bg-transparent p-2 text-sm"
          />
          <div className="flex gap-2">
            <Button size="sm" onClick={save} disabled={saving}>
              {saving ? "저장 중..." : "메모 저장"}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>
              취소
            </Button>
          </div>
        </div>
      ) : writeup.note ? (
        <blockquote className="mb-3 whitespace-pre-wrap border-l-2 border-accent pl-3 text-sm">{writeup.note}</blockquote>
      ) : null}
      {writeup.mine && !editing && (
        <Button size="sm" variant="secondary" className="mb-3" onClick={() => setEditing(true)}>
          {writeup.note ? "메모 고치기" : "작성자 메모 남기기"}
        </Button>
      )}

      <dl className="grid gap-3 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-xs text-foreground-muted">구성 요소</dt>
          <dd className="mt-1 flex flex-wrap gap-1">
            {kinds.length === 0
              ? <span className="text-foreground-muted">캔버스 없음</span>
              : kinds.map(([k, n]) => (
                  <span key={k} className="rounded border border-border px-1.5 text-xs">
                    {kindLabel(k)}{n > 1 ? ` ×${n}` : ""}
                  </span>
                ))}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-foreground-muted">기본값에서 바꾼 설정</dt>
          <dd className="mt-1 text-xs">
            {(summary?.changedTraits ?? []).length === 0
              ? <span className="text-foreground-muted">없음</span>
              : summary!.changedTraits.map((t) => (
                  <span key={t.key} className="block font-mono">
                    {traitLabel(t.key)} {t.defaultValue} → {t.value}
                  </span>
                ))}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-foreground-muted">인시던트 조치 순서</dt>
          <dd className="mt-1 text-xs">
            {(summary?.actions ?? []).length === 0 ? <span className="text-foreground-muted">없음</span> : summary!.actions.map(actionLabel).join(" → ")}
          </dd>
        </div>
      </dl>
    </Card>
  );
}

/** C8 — my design vs this one, as structural differences (no LLM). */
export function ComparePanel({ sessionId }: { sessionId: string }) {
  const [comparison, setComparison] = useState<WriteupComparison | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      setComparison(await getWriteupComparison(sessionId));
    } catch {
      setError("비교하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  if (!comparison) {
    return (
      <Card as="section" className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-foreground-muted">내가 같은 시나리오에서 그린 설계와 어디가 다른지 봅니다.</p>
        <Button size="sm" onClick={load} disabled={loading}>
          {loading ? "비교하는 중..." : "내 설계와 비교"}
        </Button>
        {error && <p className="w-full text-xs text-danger">{error}</p>}
      </Card>
    );
  }

  const { mine, theirs } = comparison;
  return (
    <Card as="section">
      <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground-muted">내 설계 vs 이 풀이</h2>
        {comparison.distance !== null && (
          <span className="text-xs text-foreground-muted">구조 차이 {Math.round(comparison.distance * 100)}%</span>
        )}
      </div>
      {!mine ? (
        <p className="text-sm text-foreground-muted">비교할 내 캔버스가 없습니다 — 다음엔 캔버스로 설계해 보세요.</p>
      ) : (
        <>
          {comparison.largestDifference && (
            <p className="mb-3 text-sm">
              가장 큰 차이: <strong>{traitLabel(comparison.largestDifference)}</strong>
            </p>
          )}
          <div className="grid gap-3 text-sm sm:grid-cols-3">
            <div>
              <p className="text-xs text-foreground-muted">나만 쓴 것</p>
              <p>{comparison.onlyMine.map(kindLabel).join(", ") || "—"}</p>
            </div>
            <div>
              <p className="text-xs text-foreground-muted">이 풀이만 쓴 것</p>
              <p>{comparison.onlyTheirs.map(kindLabel).join(", ") || "—"}</p>
            </div>
            <div>
              <p className="text-xs text-foreground-muted">둘 다</p>
              <p>{comparison.shared.map(kindLabel).join(", ") || "—"}</p>
            </div>
          </div>
          {comparison.traitDiffs.length > 0 && (
            <table className="mt-3 w-full text-sm">
              <thead>
                <tr className="text-xs text-foreground-muted">
                  <th className="text-left font-normal">설정</th>
                  <th className="text-right font-normal">나</th>
                  <th className="text-right font-normal">이 풀이</th>
                </tr>
              </thead>
              <tbody className="font-mono">
                {comparison.traitDiffs.map((d) => (
                  <tr key={d.key} className="border-t border-border">
                    <td className="py-1 font-sans">{traitLabel(d.key)}</td>
                    <td className="text-right">{d.mine}</td>
                    <td className="text-right">{d.theirs}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <dl className="mt-3 grid grid-cols-2 gap-3 text-xs">
            <div>
              <dt className="text-foreground-muted">나 — 점수 · MTTR · 조치</dt>
              <dd>
                {mine.averageScore ?? "-"}점 · {mine.mttrSeconds != null ? formatDuration(mine.mttrSeconds) : "-"} · {mine.actions.map(actionLabel).join(" → ") || "없음"}
              </dd>
            </div>
            <div>
              <dt className="text-foreground-muted">이 풀이 — 점수 · MTTR · 조치</dt>
              <dd>
                {theirs.averageScore ?? "-"}점 · {theirs.mttrSeconds != null ? formatDuration(theirs.mttrSeconds) : "-"} · {theirs.actions.map(actionLabel).join(" → ") || "없음"}
              </dd>
            </div>
          </dl>
        </>
      )}
    </Card>
  );
}

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C9 (PLAN.md Round E16) — "Fork My Run": pick a moment in
 * this writeup's incident and take over from there. Rule-based incidents only (ADR-0046).
 */
export function ForkMyRunPanel({ writeup }: { writeup: WriteupDetail }) {
  const router = useRouter();
  const [pending, setPending] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const summary = writeup.summary;
  if (!summary?.forkable) return null;

  const steps = [{ label: "인시던트 시작 직후", seconds: 0 }, ...summary.actions.map((a, i) => ({ label: `${actionLabel(a)} 직후`, seconds: summary.actionSeconds[i] ?? 0 }))];

  async function fork(atStep: number) {
    setPending(atStep);
    setError(null);
    try {
      const created = await createFork(writeup.sessionId, atStep);
      router.push(`/writeups/${writeup.scenarioId}/${writeup.sessionId}/fork/${created.forkId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "포크를 만들지 못했습니다.");
      setPending(null);
    }
  }

  return (
    <Card as="section">
      <h2 className="mb-1 text-sm font-semibold text-foreground-muted">여기서 내가 해보기</h2>
      <p className="mb-3 text-xs text-foreground-muted">
        이 풀이의 인시던트를 고른 시점의 상태 그대로 이어받아, 내 판단으로 대응해 봅니다. 결과는 이 풀이와 나란히 비교되고 기록에는 남지 않습니다.
      </p>
      <ol className="flex flex-col gap-1.5">
        {steps.map((step, i) => (
          <li key={i} className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <span>
              <span className="mr-2 font-mono text-xs text-foreground-muted">+{formatDuration(step.seconds)}</span>
              {step.label}
            </span>
            <Button size="sm" variant="secondary" onClick={() => fork(i)} disabled={pending !== null}>
              {pending === i ? "여는 중..." : "여기서 내가 해보기"}
            </Button>
          </li>
        ))}
      </ol>
      {error && <p className="mt-2 text-xs text-danger">{error}</p>}
    </Card>
  );
}
