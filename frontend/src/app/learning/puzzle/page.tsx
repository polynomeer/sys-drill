"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { type DiagnosticPuzzle, type PuzzleResult, answerPuzzle, getPuzzle } from "@/lib/api";
import { Choices, PuzzleCharts } from "@/components/PuzzleCharts";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";

/**
 * docs/LEARNING_EXPANSION_PLAN.md L9 (PLAN.md Round E28) — five minutes, no login: metrics from an
 * incident in progress, then "what is it?" and "what would you check first?". Not recorded —
 * mastery comes from Drills only.
 */
export default function PuzzlePage() {
  const [puzzle, setPuzzle] = useState<DiagnosticPuzzle | null>(null);
  const [pattern, setPattern] = useState<string | null>(null);
  const [check, setCheck] = useState<string | null>(null);
  const [result, setResult] = useState<PuzzleResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const next = useCallback(() => {
    setPuzzle(null);
    setPattern(null);
    setCheck(null);
    setResult(null);
    getPuzzle()
      .then(setPuzzle)
      .catch(() => setError("퍼즐을 불러오지 못했습니다."));
  }, []);

  useEffect(() => {
    // Fetch on mount.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    next();
  }, [next]);

  async function submit() {
    if (!puzzle) return;
    setResult(await answerPuzzle(puzzle.seed, { pattern: pattern ?? undefined, check: check ?? undefined }));
  }

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-4 p-4 sm:p-8">
      <div>
        <Link href="/learning" className="text-sm text-foreground-muted hover:text-foreground">
          ← Learning
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">진단 퍼즐</h1>
        <p className="mt-1 text-sm text-foreground-muted">
          운영 중인 시스템에서 장애가 시작됐습니다(점선 = 시작 시점). 지표만 보고 무슨 일인지, 무엇부터 확인할지 골라 보세요. 기록되지 않습니다.
        </p>
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
      {!puzzle && !error && <LoadingState />}
      {puzzle && (
        <>
          <PuzzleCharts points={puzzle.points} />
          <Card as="section" className="flex flex-col gap-3 text-sm">
            <h2 className="font-semibold">무슨 일이 일어나고 있나요?</h2>
            <Choices options={puzzle.patterns} value={pattern} onChange={setPattern} disabled={!!result} result={result ? [result.answerPattern] : null} />
            <h2 className="font-semibold">가장 먼저 무엇을 확인하겠습니까?</h2>
            <Choices options={puzzle.checks} value={check} onChange={setCheck} disabled={!!result} result={result?.acceptedChecks ?? null} />
            {!result ? (
              <Button onClick={submit} disabled={!pattern && !check} className="self-start">
                답 확인
              </Button>
            ) : (
              <div className="flex flex-col gap-2 rounded-lg border border-border p-3">
                <p className="font-medium">
                  {result.patternCorrect ? "✓ 맞혔습니다" : "✗ 아닙니다"} — {result.answerPatternName}
                </p>
                <p className="text-foreground-muted">{result.explanation}</p>
                <div className="flex flex-wrap gap-2">
                  <Button href={`/learning/failures/${result.answerPattern}`} size="sm" variant="secondary">
                    이 장애 패턴 자세히 →
                  </Button>
                  <Button size="sm" onClick={next}>
                    다음 퍼즐
                  </Button>
                </div>
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
