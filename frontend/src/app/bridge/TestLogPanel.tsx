import type { BuildSubmissionResponse } from "@/lib/api";

type Line = { prefix: string; text: string; tone: "pass" | "fail" | "info" | "muted" | "plain" };

const TONE_CLASSES: Record<Line["tone"], string> = {
  pass: "text-success",
  fail: "text-danger",
  info: "text-accent",
  muted: "text-foreground-muted",
  plain: "text-foreground",
};

function outputLines(stageOrder: number, output: string | null | undefined): Line[] {
  const prefix = `[stage-${stageOrder}]`;
  if (!output) return [];
  return output.split("\n").map((text) => ({
    prefix: text.startsWith("RESULT:") ? prefix : "[your_code]",
    text,
    tone: text.startsWith("RESULT:PASS") ? "pass" : text.startsWith("RESULT:FAIL") ? "fail" : "plain",
  }));
}

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.3 — the Build test log, modelled on
 * CodeCrafters' `git push` output: every line is prefixed with its source,
 * results are colored, and a failing current stage ends with a next action.
 *
 * All six stages always run (BuildRunnerWorker doesn't stop at the first
 * failure), but the log is judged against the *current* stage: stages up to
 * it are shown in full, later ones are folded behind a spoiler toggle.
 */
export function TestLogPanel({
  submission,
  currentStage,
  grading,
}: {
  submission: BuildSubmissionResponse;
  currentStage: number;
  grading: boolean;
}) {
  const stages = submission.stages.slice().sort((a, b) => a.stageOrder - b.stageOrder);
  const visible = stages.filter((s) => s.stageOrder <= currentStage);
  const later = stages.filter((s) => s.stageOrder > currentStage && s.status);
  const nextToRun = grading ? stages.find((s) => !s.status) : undefined;

  const lines: Line[] = [{ prefix: "[runner]", text: `제출 ${submission.id.slice(0, 8)} — 샌드박스(네트워크 차단)에서 테스트를 실행합니다`, tone: "muted" }];
  for (const stage of visible) {
    if (!stage.status) continue;
    lines.push({
      prefix: `[stage-${stage.stageOrder}]`,
      text: `Stage #${stage.stageOrder}: ${stage.title} — ${stage.status === "PASSED" ? "통과" : "실패"}${
        typeof stage.durationMs === "number" ? ` (${(stage.durationMs / 1000).toFixed(1)}s)` : ""
      }`,
      tone: stage.status === "PASSED" ? "pass" : "fail",
    });
    // Passed stages stay compact; the failing current stage shows its full output.
    if (stage.status === "FAILED") {
      const raw = outputLines(stage.stageOrder, stage.output);
      lines.push(...(raw.length > 0 ? raw : [{ prefix: `[stage-${stage.stageOrder}]`, text: stage.feedback ?? "", tone: "fail" as const }]));
      if (stage.stageOrder === currentStage) {
        lines.push({
          prefix: "[next]",
          text: `왼쪽 Stage ${stage.stageOrder} 지시문의 "테스트가 확인하는 것"과 위 실패 메시지를 비교해 고친 뒤 다시 제출하세요.`,
          tone: "info",
        });
      }
    }
  }
  if (nextToRun) {
    lines.push({ prefix: `[stage-${nextToRun.stageOrder}]`, text: "실행 중...", tone: "muted" });
  }

  return (
    <section className="flex flex-col overflow-hidden rounded-xl border border-border bg-background">
      <div className="flex items-center justify-between border-b border-border px-4 py-2 text-xs text-foreground-muted">
        <span className="font-semibold uppercase tracking-wide">테스트 로그</span>
        <span>
          {/* Raw score across all stages — can exceed the stage list's progress when a later stage happens to pass. */}
          {grading ? "채점 중" : `전체 테스트 ${submission.score ?? 0} / ${submission.totalStages} 통과`}
        </span>
      </div>
      <pre className="max-h-80 overflow-auto px-4 py-3 font-mono text-xs leading-relaxed">
        {lines.map((line, i) => (
          <div key={i} className={TONE_CLASSES[line.tone]}>
            <span className="mr-2 select-none text-foreground-muted">{line.prefix.padEnd(12)}</span>
            {line.text}
          </div>
        ))}
      </pre>
      {later.length > 0 && (
        <details className="border-t border-border px-4 py-2 text-xs text-foreground-muted">
          <summary className="cursor-pointer">
            이후 단계 결과 {later.length}개 — 현재 단계를 통과하면 차례로 열립니다 (미리 보기)
          </summary>
          <ul className="mt-2 space-y-0.5 font-mono">
            {later.map((s) => (
              <li key={s.stageOrder} className={s.status === "PASSED" ? "text-success" : "text-danger"}>
                [stage-{s.stageOrder}] {s.title} — {s.status === "PASSED" ? "통과" : "실패"}
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}
