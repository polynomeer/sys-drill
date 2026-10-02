"use client";

import { useEffect, useState } from "react";
import { type WeeklyPuzzle, answerWeeklyPuzzle, getWeeklyPuzzle } from "@/lib/api";
import { Choices, PuzzleCharts } from "@/components/PuzzleCharts";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C12 (PLAN.md Round E28) — this week's "What Would You Do?".
 * Everyone gets the same incident; the split of answers and the reasons people chose to share
 * appear only after answering (once — changing it after seeing the split would make it meaningless).
 */
export function WeeklyPuzzleCard() {
  const [data, setData] = useState<WeeklyPuzzle | null>(null);
  const [choice, setChoice] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [share, setShare] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getWeeklyPuzzle().then(setData).catch(() => setData(null));
  }, []);

  if (!data) return null;
  const answered = data.myChoice !== null;
  const label = (key: string) => data.puzzle.checks.find((c) => c.key === key)?.label ?? key;

  async function submit() {
    if (!choice) return;
    setBusy(true);
    try {
      setData(await answerWeeklyPuzzle({ choice, reason: reason.trim() || undefined, reasonPublic: share }));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card as="section" className="flex flex-col gap-3 text-sm">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-sm font-semibold">이번 주 What Would You Do?</h2>
        <span className="text-xs text-foreground-muted">{String(data.week).slice(4)}주차 · 모두 같은 장애</span>
      </div>
      <PuzzleCharts points={data.puzzle.points} />
      <p className="font-medium">장애가 시작됐습니다. 가장 먼저 무엇을 확인하겠습니까?</p>
      <Choices
        options={data.puzzle.checks}
        value={answered ? data.myChoice : choice}
        onChange={setChoice}
        disabled={answered}
        result={data.result?.acceptedChecks ?? null}
      />
      {!answered ? (
        <div className="flex flex-col gap-2">
          <Input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} placeholder="한 줄 이유 (선택)" className="text-xs" />
          <label className="flex items-center gap-2 text-xs text-foreground-muted">
            <input type="checkbox" checked={share} onChange={(e) => setShare(e.target.checked)} />
            이유를 다른 사람에게 공개
          </label>
          <Button size="sm" className="self-start" onClick={submit} disabled={!choice || busy}>
            답하고 다른 사람 선택 보기
          </Button>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          <ul className="flex flex-col gap-1 text-xs">
            {data.puzzle.checks.map((c) => {
              const n = data.distribution?.[c.key] ?? 0;
              const pct = data.total ? Math.round((n / data.total) * 100) : 0;
              return (
                <li key={c.key} className="grid grid-cols-[minmax(0,10rem)_1fr_3rem] items-center gap-2">
                  <span className={data.result?.acceptedChecks.includes(c.key) ? "text-success" : ""}>{c.label}</span>
                  <span className="h-2 rounded bg-background">
                    <span className="block h-2 rounded bg-accent" style={{ width: `${pct}%` }} />
                  </span>
                  <span className="text-right text-foreground-muted">{pct}%</span>
                </li>
              );
            })}
          </ul>
          {data.result && (
            <p className="text-xs text-foreground-muted">
              {data.result.answerPatternName} — {data.result.explanation}
            </p>
          )}
          {(data.reasons?.length ?? 0) > 0 && (
            <ul className="flex flex-col gap-1 border-t border-border pt-2 text-xs">
              {data.reasons!.map((r, i) => (
                <li key={i}>
                  <span className="font-medium">{r.nickname}</span> <span className="text-foreground-muted">({label(r.choice)})</span> {r.reason}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Card>
  );
}
