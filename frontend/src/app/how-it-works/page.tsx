"use client";

import { useEffect, useState } from "react";
import { ScenarioSummary, listScenarios } from "@/lib/api";
import { pickFirstDrill } from "@/lib/drillPrereq";
import { BlockReader } from "@/components/BlockReader";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

const BLOCKS: { title: string; body: React.ReactNode }[] = [
  {
    title: "세 가지 모드",
    body: (
      <ul className="list-inside list-disc space-y-1">
        <li>
          <b>Design</b> — 실제 서비스 요구사항으로 아키텍처를 설계하고 제출합니다.
        </li>
        <li>
          <b>Build</b> — Rate Limiter 같은 핵심 컴포넌트를 코드로 구현해 단계별 테스트를 통과합니다.
        </li>
        <li>
          <b>Incident</b> — 장애가 난 시스템의 지표와 로그를 보며 대응 액션을 실행하고 회고를 씁니다.
        </li>
      </ul>
    ),
  },
  {
    title: "한 Drill은 이렇게 흘러갑니다",
    body: (
      <p>
        초기 설계를 제출하면 피드백을 받고, 다음 단계로 넘어가면 그때 <b>바뀐 조건</b>이 공개됩니다(꼬리설계). 공식 Drill은
        마지막에 장애 대응 단계가 있고, 모두 마치면 단계별 점수와 피드백, 다음 추천 Drill이 담긴 리포트가 나옵니다. 다음
        단계의 조건은 미리 보여주지 않습니다 — 실무에서도 조건은 예고 없이 바뀝니다.
      </p>
    ),
  },
  {
    title: "채점 방식",
    body: (
      <p>
        답안마다 AI가 루브릭 7개 항목으로 점수를 매기고, 잘한 점·놓친 점·실무 리스크·꼬리질문을 돌려줍니다. 반복해서
        지적받은 개념은 Learning 탭의 &ldquo;내 학습 경로&rdquo;에 모여, 무엇을 더 공부하면 되는지 보여줍니다.
      </p>
    ),
  },
  {
    title: "Build는 단계별로 열립니다",
    body: (
      <p>
        1단계는 에디터에 주석으로 들어 있는 코드의 주석만 풀고 제출하면 통과합니다 — 제출 흐름부터 한 번 경험해 보세요.
        단계를 통과할 때마다 다음 단계의 지시문이 열리고, 테스트 로그에서 무엇이 실패했는지 바로 볼 수 있습니다. 자기
        에디터가 편하면 &ldquo;로컬에서 풀기&rdquo;로 저장소를 받아 터미널에서 제출할 수도 있습니다.
      </p>
    ),
  },
  {
    title: "무엇이 공개되나요",
    body: (
      <p>
        답안은 기본적으로 비공개입니다. 끝까지 마친 풀이를 직접 공개하기로 해야만 같은 Drill을 완료한 사람에게 보입니다.
        랭킹과 &ldquo;최근 완료&rdquo;에는 닉네임과 완료 사실만 나오고, 프로필에서 랭킹 숨기기를 켜면 둘 다에서 빠집니다.
      </p>
    ),
  },
];

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.4 — "SysDrill은 어떻게 동작하나요", read
 * block by block like a concept page. A static page rather than a concept
 * row: the concept table stays 1:1 with the grader's risk keys (ADR-0039).
 * Public — it's linked from the landing page too.
 */
export default function HowItWorksPage() {
  const [revealed, setRevealed] = useState(1);
  const [firstDrill, setFirstDrill] = useState<ScenarioSummary | undefined>();

  useEffect(() => {
    listScenarios()
      .then((scenarios) => setFirstDrill(pickFirstDrill(scenarios)))
      .catch(() => setFirstDrill(undefined));
  }, []);

  const shown = Math.min(revealed, BLOCKS.length);
  const done = shown >= BLOCKS.length;

  return (
    <BlockReader done={done} onContinue={() => setRevealed((n) => n + 1)} onExpandAll={() => setRevealed(BLOCKS.length)}>
      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-foreground-muted">시작하기 전에</p>
        <h1 className="mt-1 break-keep text-2xl font-semibold">SysDrill은 어떻게 동작하나요</h1>
        <p className="mt-1 text-xs text-foreground-muted">
          읽는 데 약 2분 · {shown} / {BLOCKS.length} 블록
        </p>
      </div>
      {BLOCKS.slice(0, shown).map((block) => (
        <Card as="section" key={block.title}>
          <h2 className="mb-2 text-sm font-semibold text-foreground-muted">{block.title}</h2>
          <div className="text-sm leading-relaxed">{block.body}</div>
        </Card>
      ))}
      {done && (
        <Button href={firstDrill ? `/drills/${firstDrill.id}` : "/marketplace"} className="self-start">
          {firstDrill ? `첫 Drill 살펴보기 — ${firstDrill.title} →` : "Drill 둘러보기 →"}
        </Button>
      )}
    </BlockReader>
  );
}
