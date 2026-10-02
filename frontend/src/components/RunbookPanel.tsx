"use client";

import { useEffect, useState } from "react";
import { type Runbook, type RunbookCheck, type RunbookStep, type RunbookStepType, getRunbook, getRunbookCheck, saveRunbook } from "@/lib/api";
import { actionLabel } from "@/lib/actionLabels";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";

const TYPE_LABELS: Record<RunbookStepType, string> = {
  OPEN_PANEL: "화면 확인",
  INSPECT_NODE: "노드 확인",
  QUERY_LOGS: "로그 검색",
  OPEN_TRACE: "트레이스 확인",
  ACTION: "조치",
  NOTE: "메모",
};
const PANEL_LABELS: Record<string, string> = { map: "서비스 맵", metrics: "지표", alerts: "알림", logs: "로그", traces: "트레이스", changes: "변경 이력" };

export function describeStep(s: RunbookStep): string {
  const target = s.type === "ACTION" ? actionLabel(s.target ?? "") : s.type === "OPEN_PANEL" ? (PANEL_LABELS[s.target ?? ""] ?? s.target) : s.target;
  return [TYPE_LABELS[s.type], target && `· ${target}`, s.text && `— ${s.text}`].filter(Boolean).join(" ");
}

/**
 * docs/DRILLS_EXPANSION_PLAN.md M12 (PLAN.md Round E27) — on the postmortem: how this incident went
 * against my runbook, and the runbook itself to grow. [suggestions] are this session's own looks and
 * actions, one click to add.
 */
export function RunbookPanel({ sessionId, suggestions }: { sessionId: string; suggestions: RunbookStep[] }) {
  const [check, setCheck] = useState<RunbookCheck | null>(null);
  const [runbook, setRunbook] = useState<Runbook | null>(null);
  const [draft, setDraft] = useState<RunbookStep[]>([]);
  const [editing, setEditing] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    getRunbookCheck(sessionId)
      .then((c) => {
        setCheck(c);
        if (c.domain) {
          getRunbook(c.domain)
            .then((r) => {
              setRunbook(r);
              setDraft(r.steps);
            })
            .catch(() => undefined);
        }
      })
      .catch(() => setCheck(null));
  }, [sessionId]);

  if (!check?.domain) return null;

  async function save() {
    if (!check?.domain) return;
    const next = await saveRunbook(check.domain, draft);
    setRunbook(next);
    setEditing(false);
    setSaved(true);
    getRunbookCheck(sessionId).then(setCheck).catch(() => undefined);
  }

  const missed = check.steps.filter((s) => s.done === false).length;

  return (
    <Card as="section" className="flex flex-col gap-3 text-sm">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground-muted">내 Runbook</h2>
        {!editing && (
          <button type="button" className="text-xs underline" onClick={() => setEditing(true)}>
            {runbook?.steps.length ? "고치기" : "만들기"}
          </button>
        )}
      </div>

      {!editing && check.available && (
        <>
          <p className="text-xs text-foreground-muted">이번 대응과 대조 — {missed === 0 ? "모든 단계를 밟았습니다." : `${missed}개 단계를 건너뛰었습니다.`}</p>
          <ol className="flex flex-col gap-1">
            {check.steps.map((s) => (
              <li key={s.index} className="flex gap-2">
                <span className={s.done === true ? "text-success" : s.done === false ? "text-danger" : "text-foreground-muted"}>
                  {s.done === true ? "✓" : s.done === false ? "✕" : "·"}
                </span>
                <span>
                  Step {s.index + 1} {describeStep(s.step)}
                  {s.atSeconds !== null && <span className="text-xs text-foreground-muted"> (+{Math.floor(s.atSeconds / 60)}분 {s.atSeconds % 60}초)</span>}
                </span>
              </li>
            ))}
          </ol>
        </>
      )}
      {!editing && !check.available && (
        <p className="text-xs text-foreground-muted">이 도메인의 Runbook이 아직 없습니다. 이번 대응에서 효과가 있었던 순서를 남겨 두면 다음 인시던트에서 대조해 드립니다.</p>
      )}
      {saved && !editing && <p className="text-xs text-success">저장했습니다.</p>}

      {editing && (
        <div className="flex flex-col gap-2">
          <ol className="flex flex-col gap-1.5">
            {draft.map((s, i) => (
              <li key={i} className="flex flex-wrap items-center gap-1.5 text-xs">
                <span className="w-12 shrink-0 text-foreground-muted">Step {i + 1}</span>
                <span className="shrink-0">{TYPE_LABELS[s.type]}{s.target ? ` · ${s.type === "ACTION" ? actionLabel(s.target) : s.target}` : ""}</span>
                <Input
                  value={s.text}
                  onChange={(e) => setDraft(draft.map((d, j) => (j === i ? { ...d, text: e.target.value } : d)))}
                  placeholder="왜 이 단계인지 (선택)"
                  className="min-w-0 flex-1 py-1 text-xs"
                  maxLength={300}
                />
                <button type="button" disabled={i === 0} onClick={() => setDraft(swap(draft, i, i - 1))} className="text-foreground-muted disabled:opacity-30" aria-label="위로">
                  ↑
                </button>
                <button type="button" onClick={() => setDraft(draft.filter((_, j) => j !== i))} className="text-foreground-muted underline">
                  삭제
                </button>
              </li>
            ))}
          </ol>
          {suggestions.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              <span className="text-xs text-foreground-muted">이번 대응에서 추가:</span>
              {suggestions.map((s, i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => setDraft([...draft, s])}
                  className="rounded-full border border-dashed border-border px-2 py-0.5 text-xs text-foreground-muted hover:border-accent hover:text-foreground"
                >
                  + {describeStep(s)}
                </button>
              ))}
            </div>
          )}
          <button type="button" className="self-start text-xs underline" onClick={() => setDraft([...draft, { type: "NOTE", target: null, text: "" }])}>
            + 메모 단계
          </button>
          <div className="flex gap-2">
            <Button size="sm" onClick={save}>
              저장
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>
              취소
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}

function swap<T>(list: T[], a: number, b: number): T[] {
  const next = list.slice();
  [next[a], next[b]] = [next[b], next[a]];
  return next;
}

/** M12 — during the incident: my runbook with the steps already taken ticked off (refreshed with the incident). */
export function RunbookLiveCard({ sessionId, version }: { sessionId: string; version: number }) {
  const [check, setCheck] = useState<RunbookCheck | null>(null);

  useEffect(() => {
    getRunbookCheck(sessionId).then(setCheck).catch(() => setCheck(null));
  }, [sessionId, version]);

  if (!check?.available) return null;
  return (
    <Card as="section" className="text-sm">
      <h2 className="mb-2 text-sm font-semibold text-foreground-muted">내 Runbook</h2>
      <ol className="flex flex-col gap-1 text-xs">
        {check.steps.map((s) => (
          <li key={s.index} className="flex gap-2">
            <span className={s.done ? "text-success" : "text-foreground-muted"}>{s.done ? "✓" : s.done === false ? "○" : "·"}</span>
            <span>{describeStep(s.step)}</span>
          </li>
        ))}
      </ol>
    </Card>
  );
}
