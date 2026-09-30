import type { Stage } from "@/components/StageList";

/** Stage copy per backend step type, shared by the Drill overview roadmap and
 * the in-session stage list. Titles describe the *kind* of stage only —
 * FOLLOWUP/INCIDENT prompts stay hidden until the session reaches them. */
export const STAGE_COPY: Record<string, { title: string; description: string }> = {
  INITIAL: { title: "초기 설계", description: "요구사항을 정리하고 고수준 아키텍처를 제출합니다. 제출하면 AI가 루브릭 기준으로 채점합니다." },
  FOLLOWUP: { title: "꼬리설계", description: "조건이 바뀝니다. 트래픽·예산 같은 새 제약에 맞춰 설계를 다시 검토합니다." },
  INCIDENT: { title: "장애 대응 (Wargame)", description: "실시간 지표와 로그를 보며 장애를 진단·복구하고 회고를 작성합니다." },
};

export const REPORT_STAGE: Stage = {
  key: "report",
  title: "리포트",
  description: "단계별 점수·피드백과 다음 추천 Drill을 확인합니다.",
};

export function stageFromStepType(type: string, key: string): Stage {
  return { key, title: STAGE_COPY[type]?.title ?? type, description: STAGE_COPY[type]?.description };
}
