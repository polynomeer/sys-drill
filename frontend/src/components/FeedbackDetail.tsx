import { EvaluationFeedback } from "@/lib/api";
import { Badge } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";

/**
 * Full AI evaluation feedback (총점/루브릭 점수/잘한 점/놓친 점/실무 리스크/
 * 꼬리질문/권장 변경사항) — extracted from `design/[sessionId]/page.tsx`'s
 * live post-submission view (UX_STRATEGY.md UI/UX 리뉴얼 Round 5) so
 * `report/[sessionId]/page.tsx` can show the same detail per phase instead
 * of only the summary fields `Report.timelineFeedback` carries.
 */
export function FeedbackDetail({ feedback }: { feedback: EvaluationFeedback }) {
  return (
    <div className="flex flex-col gap-4">
      <Card as="section">
        <p className="text-sm text-foreground-muted">총점</p>
        <p className="text-3xl font-semibold">{feedback.totalScore ?? "-"} / 100</p>
        {(feedback.modelProvider || feedback.modelName) && (
          <p className="mt-1 text-xs text-foreground-muted">
            {feedback.modelProvider} · {feedback.modelName}
          </p>
        )}
      </Card>

      {Object.keys(feedback.rubricScores).length > 0 && (
        <Card as="section">
          <h2 className="mb-2 text-sm font-semibold text-foreground-muted">항목별 점수</h2>
          <ul className="space-y-1 text-sm">
            {Object.entries(feedback.rubricScores).map(([name, score]) => (
              <li key={name} className="flex justify-between">
                <span>{name}</span>
                <span className="font-mono">{score}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <FeedbackList title="잘한 점" items={feedback.strengths} />
      <FeedbackList title="놓친 점" items={feedback.weaknesses} />

      {feedback.riskFlags.length > 0 && (
        <Card as="section">
          <h2 className="mb-2 text-sm font-semibold text-foreground-muted">실무 리스크</h2>
          <ul className="space-y-2 text-sm">
            {feedback.riskFlags.map((flag, i) => (
              <li key={i}>
                <Badge variant="danger" className="mr-2">
                  {flag.severity}
                </Badge>
                {flag.description}
              </li>
            ))}
          </ul>
        </Card>
      )}

      <FeedbackList title="꼬리질문" items={feedback.followupQuestions} />
      <FeedbackList title="권장 변경사항" items={feedback.recommendedChanges} />
    </div>
  );
}

function FeedbackList({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null;
  return (
    <Card as="section">
      <h2 className="mb-2 text-sm font-semibold text-foreground-muted">{title}</h2>
      <ul className="list-inside list-disc space-y-1 text-sm">
        {items.map((item, i) => (
          <li key={i}>{item}</li>
        ))}
      </ul>
    </Card>
  );
}
