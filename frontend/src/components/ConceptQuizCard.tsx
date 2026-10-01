"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { CircleCheck, CircleX } from "lucide-react";
import { ConceptQuiz, getConceptQuiz } from "@/lib/api";
import { Card } from "@/components/ui/Card";

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.6 — one question at the end of a concept.
 * After answering, every option shows which concept it actually belongs to,
 * so a wrong pick still points somewhere useful. Purely a self-check.
 */
export function ConceptQuizCard({ riskKey }: { riskKey: string }) {
  const [quiz, setQuiz] = useState<ConceptQuiz | null>(null);
  const [picked, setPicked] = useState<number | null>(null);
  const [failed, setFailed] = useState(false);

  const fetchQuiz = useCallback(
    () =>
      getConceptQuiz(riskKey)
        .then(setQuiz)
        .catch(() => setFailed(true)),
    [riskKey],
  );

  useEffect(() => {
    fetchQuiz();
  }, [fetchQuiz]);

  function nextQuestion() {
    setPicked(null);
    fetchQuiz();
  }

  if (failed) return null; // the quiz is optional — the page reads fine without it
  if (!quiz) return null;

  const answered = picked !== null;
  const right = answered && quiz.options[picked].correct;

  return (
    <Card as="section">
      <h2 className="mb-1 text-sm font-semibold text-foreground-muted">확인 문제</h2>
      <p className="mb-3 text-sm">{quiz.question}</p>
      <ul className="flex flex-col gap-2">
        {quiz.options.map((option, i) => {
          const state = !answered ? "idle" : option.correct ? "correct" : i === picked ? "wrong" : "idle";
          return (
            <li key={option.text}>
              <button
                type="button"
                disabled={answered}
                onClick={() => setPicked(i)}
                className={`w-full rounded-lg border px-3 py-2 text-left text-sm transition-colors ${
                  state === "correct"
                    ? "border-success/60 bg-success/10"
                    : state === "wrong"
                      ? "border-danger/60 bg-danger/10"
                      : "border-border hover:border-accent/40 disabled:hover:border-border"
                }`}
              >
                <span className="flex items-start gap-2">
                  {state === "correct" && <CircleCheck className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-label="정답" />}
                  {state === "wrong" && <CircleX className="mt-0.5 h-4 w-4 shrink-0 text-danger" aria-label="오답" />}
                  <span>{option.text}</span>
                </span>
                {answered && !option.correct && (
                  <span className="mt-1 block text-xs text-foreground-muted">
                    →{" "}
                    <Link href={`/learning/${option.fromRiskKey}`} className="underline hover:text-foreground">
                      {option.fromLabel}
                    </Link>
                    의 해결 패턴
                  </span>
                )}
              </button>
            </li>
          );
        })}
      </ul>
      {answered && (
        <div className="mt-3 flex items-center justify-between text-sm">
          <span className={right ? "text-success" : "text-danger"}>{right ? "맞았습니다." : "다시 한 번 위의 해결 패턴을 확인해 보세요."}</span>
          <button type="button" onClick={nextQuestion} className="text-xs text-foreground-muted underline hover:text-foreground">
            다른 문제
          </button>
        </div>
      )}
    </Card>
  );
}
