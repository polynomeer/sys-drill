import type { PreferredLanguage, TrainingGoal } from "@/lib/api";

/** docs/CODECRAFTERS_BENCHMARK.md §3.4 — labels for the two onboarding answers, shared by signup and profile. */
export const LANGUAGE_OPTIONS: { value: PreferredLanguage; label: string }[] = [
  { value: "PYTHON", label: "Python" },
  { value: "TYPESCRIPT", label: "TypeScript" },
  { value: "JAVA", label: "Java" },
  { value: "KOTLIN", label: "Kotlin" },
  { value: "GO", label: "Go" },
];

export const GOAL_OPTIONS: { value: TrainingGoal; label: string; hint: string }[] = [
  { value: "INTERVIEW", label: "면접 준비", hint: "Drill마다 면접형 타이머가 기본으로 켜집니다" },
  { value: "SKILLS", label: "실무 역량", hint: "시간 제한 없이 깊게 훈련합니다" },
  { value: "TEAM", label: "팀 온보딩", hint: "Home에 조직·커리큘럼 기능을 안내합니다" },
];

/** Stack suggestions for the free-text field — a datalist, not a closed choice. */
export const STACK_SUGGESTIONS = [
  "Kotlin / Spring Boot",
  "Java / Spring Boot",
  "Go",
  "Node.js / TypeScript",
  "Python / Django",
  "Python / FastAPI",
  "Ruby on Rails",
  "C# / .NET",
];
