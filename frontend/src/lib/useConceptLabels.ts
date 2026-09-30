"use client";

import { useEffect, useState } from "react";
import { getLearningConcepts } from "./api";

/**
 * riskKey → 한국어 라벨 조회.
 *
 * ADR-0039 이후 라벨의 단일 출처는 DB(`learning_concepts`)다. 예전에는
 * `riskLabels.ts` 상수가 같은 25개 키를 손으로 들고 있었는데, `RuleEvaluator`
 * 쪽과 어긋나도 아무도 모르는 구조였다.
 *
 * 로딩 중이거나 조회에 실패하면 riskKey 를 그대로 돌려준다 — 약점 목록은
 * 부가 정보라 라벨 때문에 화면이 비면 안 된다.
 */
export function useConceptLabels(): (riskKey: string) => string {
  const [labels, setLabels] = useState<Record<string, string>>({});

  useEffect(() => {
    let cancelled = false;
    getLearningConcepts()
      .then((categories) => {
        if (cancelled) return;
        const next: Record<string, string> = {};
        categories.forEach((c) => c.concepts.forEach((concept) => {
          next[concept.riskKey] = concept.label;
        }));
        setLabels(next);
      })
      .catch(() => {
        /* 라벨 없이 raw key 로 렌더링된다 */
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (riskKey: string) => labels[riskKey] ?? riskKey;
}
