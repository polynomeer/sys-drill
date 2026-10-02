"use client";

import { useEffect, useState } from "react";
import { type Assumptions, getAssumptions } from "@/lib/api";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";

export type AssumptionChoice = { selected: string[]; custom: string[] };

const MAX_CUSTOM = 5;

/**
 * docs/DRILLS_EXPANSION_PLAN.md M7 (PLAN.md Round E20) — say what the design assumes before
 * the tail design breaks it. Optional; goes out with the INITIAL submission. Renders nothing
 * for a scenario without assumption candidates.
 */
export function AssumptionPanel({
  sessionId,
  value,
  onChange,
}: {
  sessionId: string;
  value: AssumptionChoice;
  onChange: (next: AssumptionChoice) => void;
}) {
  const [data, setData] = useState<Assumptions | null>(null);
  const [draft, setDraft] = useState("");

  useEffect(() => {
    getAssumptions(sessionId).then(setData).catch(() => setData(null));
  }, [sessionId]);

  if (!data?.available || !data.open) return null;

  function toggle(id: string) {
    const selected = value.selected.includes(id) ? value.selected.filter((s) => s !== id) : [...value.selected, id];
    onChange({ ...value, selected });
  }

  function addCustom() {
    const text = draft.trim();
    if (!text || value.custom.length >= MAX_CUSTOM) return;
    onChange({ ...value, custom: [...value.custom, text] });
    setDraft("");
  }

  return (
    <Card as="section" className="text-sm">
      <h2 className="mb-1 text-xs font-semibold uppercase tracking-wide text-foreground-muted">설계 가정</h2>
      <p className="mb-3 text-xs text-foreground-muted">이 설계가 기대는 가정을 골라 두세요. 다음 단계에서 조건이 바뀌면 어떤 가정이 깨졌는지 보여드립니다.</p>
      <div className="flex flex-col gap-1.5">
        {data.options.map((a) => (
          <label key={a.id} className="flex items-start gap-2">
            <input type="checkbox" className="mt-0.5" checked={value.selected.includes(a.id)} onChange={() => toggle(a.id)} />
            <span>{a.text}</span>
          </label>
        ))}
        {value.custom.map((text, i) => (
          <div key={i} className="flex items-start gap-2">
            <span className="text-foreground-muted">+</span>
            <span className="flex-1">{text}</span>
            <button
              type="button"
              className="text-xs text-foreground-muted underline"
              onClick={() => onChange({ ...value, custom: value.custom.filter((_, j) => j !== i) })}
            >
              삭제
            </button>
          </div>
        ))}
      </div>
      {value.custom.length < MAX_CUSTOM && (
        <form
          className="mt-2 flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            addCustom();
          }}
        >
          <Input value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="직접 추가 (예: 쓰기는 초당 100건 이하)" className="flex-1 py-1 text-xs" maxLength={200} />
          <button type="submit" className="text-xs text-accent">
            추가
          </button>
        </form>
      )}
    </Card>
  );
}

/** FOLLOWUP — which assumptions this change broke, split by whether the learner had stated them. */
export function BrokenAssumptions({ sessionId }: { sessionId: string }) {
  const [data, setData] = useState<Assumptions | null>(null);

  useEffect(() => {
    getAssumptions(sessionId).then(setData).catch(() => setData(null));
  }, [sessionId]);

  if (!data?.available || !data.broken || data.broken.length === 0) return null;
  const mine = data.broken.filter((a) => data.selected.includes(a.id));
  const implicit = data.broken.filter((a) => !data.selected.includes(a.id));

  return (
    <Card as="section" className="border-warning/50 text-sm">
      <h2 className="mb-2 text-xs font-semibold uppercase tracking-wide text-warning">깨진 가정</h2>
      {mine.length > 0 && (
        <>
          <p className="mb-1 text-xs text-foreground-muted">내가 둔 가정</p>
          <ul className="mb-2 space-y-1">
            {mine.map((a) => (
              <li key={a.id}>✗ {a.text}</li>
            ))}
          </ul>
        </>
      )}
      {implicit.length > 0 && (
        <>
          <p className="mb-1 text-xs text-foreground-muted">적지 않았지만 설계가 기대고 있었을 수 있는 가정</p>
          <ul className="space-y-1">
            {implicit.map((a) => (
              <li key={a.id}>✗ {a.text}</li>
            ))}
          </ul>
        </>
      )}
    </Card>
  );
}
