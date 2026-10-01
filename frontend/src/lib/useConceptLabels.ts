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
  return useConceptLookup().label;
}

export interface ConceptLookup {
  label: (riskKey: string) => string;
  /**
   * 개념 문서가 있는 riskKey인가. LLM이 낸 리스크(`LLM_TOP_RISK`)처럼 개념이
   * 없는 키에는 `/learning/{riskKey}` 링크를 걸면 404가 된다.
   */
  isConcept: (riskKey: string) => boolean;
}

// 리포트 화면은 단계마다 FeedbackDetail을 하나씩 그려서 같은 목록을 여러 번
// 요청하게 된다. 한 페이지 생애 동안 한 번만 부르도록 모듈 범위에서 공유한다.
let labelsPromise: Promise<Record<string, string>> | null = null;

function loadLabels(): Promise<Record<string, string>> {
  if (!labelsPromise) {
    labelsPromise = getLearningConcepts()
      .then((categories) => {
        const next: Record<string, string> = {};
        categories.forEach((c) => c.concepts.forEach((concept) => {
          next[concept.riskKey] = concept.label;
        }));
        return next;
      })
      .catch((err) => {
        labelsPromise = null; // 다음 마운트에서 다시 시도
        throw err;
      });
  }
  return labelsPromise;
}

export function useConceptLookup(): ConceptLookup {
  const [labels, setLabels] = useState<Record<string, string>>({});

  useEffect(() => {
    let cancelled = false;
    loadLabels()
      .then((next) => {
        if (!cancelled) setLabels(next);
      })
      .catch(() => {
        /* 라벨 없이 raw key 로 렌더링된다 */
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return {
    label: (riskKey: string) => labels[riskKey] ?? riskKey,
    isConcept: (riskKey: string) => riskKey in labels,
  };
}
