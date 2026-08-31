"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ApiError,
  EvaluationFeedback,
  SessionResponse,
  SessionStatus,
  advanceSession,
  getFeedback,
  getSession,
  submitAnswer,
} from "@/lib/api";
import {
  clearDraft,
  getStoredUserId,
  loadDraft,
  loadSubmissionId,
  saveDraft,
  saveSubmissionId,
} from "@/lib/localSession";
import { WargameLive } from "./WargameLive";
import { BridgeProgress } from "@/components/BridgeProgress";
import { PhaseTimer } from "@/components/PhaseTimer";

const DESIGN_GUIDANCE_BY_DOMAIN: Record<string, string[]> = {
  coupon: [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "고수준 아키텍처와 요청 흐름",
    "저장소 선택과 읽기/쓰기 패턴",
    "동시성·멱등성 처리 (중복 발급 방지)",
    "캐시/락 전략과 실패 시 대응",
    "Rate limit 등 트래픽 보호 전략",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
  notification: [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "비동기 처리 경계 (이벤트 발행 vs 알림 전송)",
    "idempotent consumer — 재시도로 인한 중복 발송 방지",
    "retry/backoff 전략",
    "DLQ(dead letter queue) — poison message 격리",
    "provider별 circuit breaker",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
  "product-browsing": [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "고수준 아키텍처와 요청 흐름",
    "데이터별 캐시 정책 분리 (가격 vs 리뷰)",
    "hot key 분산 전략",
    "single-flight/lock — 동시 cache miss 중복 요청 방지",
    "read replica를 통한 읽기 확장",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
  payment: [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "트랜잭션 경계 — DB 트랜잭션과 외부 PG 호출을 어떻게 분리했는지 (outbox/saga)",
    "결제 멱등성 — 재시도 시 이중 결제 방지",
    "PG 장애 시 retry/backoff 전략과 partial failure 대응",
    "결제 상태와 주문 상태의 일관성 보장 방법",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
  reservation: [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "동시 예약 요청에 대한 락 전략 (세분화 수준 포함)",
    "재고 정합성 — 예약 가능 수량 확인과 확정의 원자성",
    "예약 홀드(hold) 타임아웃과 자동 해제",
    "중복 예약(overbooking) 방지 방법",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
  "batch-settlement": [
    "기능/비기능 요구사항 요약 (무엇을 보장하고 무엇을 포기할지)",
    "청킹(chunking) — 대용량 레코드를 어떤 단위로 나누어 처리하는지",
    "재시작성(restartability) — 중간 실패 시 처음부터 재실행 vs 체크포인트 재개",
    "정산 정합성(reconciliation) — 재처리 시 중복 반영 방지",
    "장시간 실행(long transaction)에 대한 대응",
    "관측(metrics/logs/alert) 계획",
    "예상 병목과 트레이드오프",
  ],
};

const INCIDENT_GUIDANCE = [
  "가장 먼저 확인한 지표와 그 이유",
  "원인 가설과 근거",
  "선택한 조치와 그 이유",
  "조치의 예상 부작용",
  "조치 이후 지표가 어떻게 바뀌었는지",
  "재발 방지를 위한 구조 개선 아이디어",
];

const POLL_INTERVAL_MS = 1500;

type ViewState =
  | "loading"
  | "error"
  | "editing"
  | "submitting"
  | "waiting"
  | "result"
  | "advancing"
  | "failed"
  | "completed";

export default function DesignWorkspacePage() {
  const params = useParams<{ sessionId: string }>();
  const router = useRouter();
  const sessionId = params.sessionId;

  const [view, setView] = useState<ViewState>("loading");
  const [session, setSession] = useState<SessionResponse | null>(null);
  const [answer, setAnswer] = useState("");
  const [feedback, setFeedback] = useState<EvaluationFeedback | null>(null);
  const [error, setError] = useState<string | null>(null);
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollTimer.current) {
      clearInterval(pollTimer.current);
      pollTimer.current = null;
    }
  }, []);

  const resolveOutcome = useCallback(
    async (status: SessionStatus) => {
      if (status === "FEEDBACK_READY") {
        stopPolling();
        const submissionId = loadSubmissionId(sessionId);
        if (!submissionId) {
          setError("제출 기록을 찾을 수 없습니다. 새로고침 후 다시 시도해주세요.");
          setView("error");
          return;
        }
        try {
          const result = await getFeedback(submissionId);
          setFeedback(result);
          setView("result");
        } catch (err) {
          setError(err instanceof ApiError ? err.message : "평가 결과를 불러오지 못했습니다.");
          setView("error");
        }
      } else if (status === "EVALUATION_FAILED") {
        stopPolling();
        setView("failed");
      } else if (status === "COMPLETED") {
        stopPolling();
        setView("completed");
      } else if (status === "SUBMITTED" || status === "EVALUATING") {
        setView("waiting");
      }
    },
    [sessionId, stopPolling],
  );

  const startPolling = useCallback(() => {
    stopPolling();
    pollTimer.current = setInterval(async () => {
      try {
        const updated = await getSession(sessionId);
        setSession(updated);
        await resolveOutcome(updated.status);
      } catch {
        // transient failure — keep polling, the next tick may succeed
      }
    }, POLL_INTERVAL_MS);
  }, [resolveOutcome, sessionId, stopPolling]);

  const loadAndDecideView = useCallback(async () => {
    const fetched = await getSession(sessionId);
    setSession(fetched);
    setFeedback(null);
    if (fetched.status === "IN_PROGRESS") {
      setAnswer(loadDraft(sessionId));
      setView("editing");
    } else {
      await resolveOutcome(fetched.status);
      if (fetched.status === "SUBMITTED" || fetched.status === "EVALUATING") {
        startPolling();
      }
    }
  }, [sessionId, resolveOutcome, startPolling]);

  useEffect(() => {
    if (!getStoredUserId()) {
      router.replace("/onboarding");
      return;
    }

    // Data fetch on mount, not a cascading render loop.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadAndDecideView().catch((err) => {
      setError(err instanceof ApiError ? err.message : "세션을 불러오지 못했습니다.");
      setView("error");
    });

    return () => stopPolling();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  function handleAnswerChange(value: string) {
    setAnswer(value);
    saveDraft(sessionId, value);
  }

  async function handleSubmit(auto = false) {
    if (!auto && !answer.trim()) {
      setError("답안을 입력해주세요.");
      return;
    }
    const textToSubmit = answer.trim() ? answer : "(시간 초과로 자동 제출됨 — 작성한 내용 없음)";
    setView("submitting");
    setError(null);
    try {
      const clientRequestId =
        typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `${Date.now()}`;
      const submission = await submitAnswer(sessionId, textToSubmit, clientRequestId);
      saveSubmissionId(sessionId, submission.id);
      clearDraft(sessionId);
      setView("waiting");
      startPolling();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "제출에 실패했습니다.");
      setView("editing");
    }
  }

  async function handleAdvance() {
    setView("advancing");
    setError(null);
    try {
      await advanceSession(sessionId);
      await loadAndDecideView();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "다음 단계로 진행하지 못했습니다.");
      setView("result");
    }
  }

  const isFollowup = session?.currentPhase === "FOLLOWUP";
  const isIncident = session?.currentPhase === "INCIDENT";
  const domain = session?.domain ?? "coupon";
  const guidance = isIncident ? INCIDENT_GUIDANCE : (DESIGN_GUIDANCE_BY_DOMAIN[domain] ?? DESIGN_GUIDANCE_BY_DOMAIN.coupon);

  return (
    <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-6 p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">
          {isIncident ? "Wargame Live" : "System Design Workspace"}
        </h1>
        <div className="flex items-center gap-3">
          {session?.interviewMode && session.phaseDeadlineAt && view === "editing" && (
            <PhaseTimer deadlineAt={session.phaseDeadlineAt} onExpire={() => handleSubmit(true)} />
          )}
          {session?.buildSubmissionId && <BridgeProgress current={isIncident ? "wargame" : "design"} />}
        </div>
      </div>

      {view === "loading" && <p className="text-sm text-zinc-500">불러오는 중...</p>}

      {view === "error" && <p className="text-sm text-red-600">{error ?? "오류가 발생했습니다."}</p>}

      {isFollowup && (view === "editing" || view === "submitting") && (
        <div className="rounded border border-amber-400 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-700 dark:bg-amber-950 dark:text-amber-200">
          조건이 변경되었습니다 — 아래 새 조건을 반영해 설계를 다시 검토하세요 (꼬리설계).
        </div>
      )}

      {session?.currentStepPrompt && (view === "editing" || view === "submitting") && (
        <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
          <h2 className="mb-2 text-sm font-semibold text-zinc-500">문제</h2>
          <p className="whitespace-pre-wrap text-sm">{session.currentStepPrompt}</p>
        </section>
      )}

      {isIncident && (view === "editing" || view === "submitting") && <WargameLive sessionId={sessionId} domain={domain} />}

      {(view === "editing" || view === "submitting") && (
        <>
          <section className="rounded border border-zinc-300 p-4 text-sm dark:border-zinc-700">
            <h2 className="mb-2 font-semibold text-zinc-500">
              {isIncident ? "회고에 포함하면 좋은 항목" : "답안에 포함하면 좋은 항목"}
            </h2>
            <ul className="list-inside list-disc space-y-1 text-zinc-600 dark:text-zinc-400">
              {guidance.map((section) => (
                <li key={section}>{section}</li>
              ))}
            </ul>
          </section>

          <textarea
            className="min-h-[200px] rounded border border-zinc-300 p-3 font-mono text-sm dark:border-zinc-700 dark:bg-zinc-900"
            value={answer}
            onChange={(e) => handleAnswerChange(e.target.value)}
            placeholder={
              isIncident
                ? "대응 회고를 작성하세요. 입력 내용은 자동으로 이 브라우저에 저장됩니다."
                : "설계를 자유롭게 작성하세요. 입력 내용은 자동으로 이 브라우저에 저장됩니다."
            }
          />

          {error && <p className="text-sm text-red-600">{error}</p>}

          <button
            onClick={() => handleSubmit()}
            disabled={view === "submitting"}
            className="self-start rounded bg-foreground px-5 py-2 font-medium text-background disabled:opacity-50"
          >
            {view === "submitting" ? "제출하는 중..." : "제출하기"}
          </button>
        </>
      )}

      {(view === "waiting" || view === "advancing") && (
        <div className="flex flex-col items-center gap-3 rounded border border-zinc-300 p-8 dark:border-zinc-700">
          <p className="text-sm text-zinc-500">
            {view === "advancing"
              ? "다음 단계로 이동하는 중..."
              : `제출한 답안을 평가하는 중입니다 (${session?.status ?? "..."})...`}
          </p>
        </div>
      )}

      {view === "failed" && (
        <div className="rounded border border-red-300 p-6 dark:border-red-800">
          <p className="text-sm text-red-600">평가에 실패했습니다. 잠시 후 다시 시도해주세요.</p>
        </div>
      )}

      {view === "completed" && (
        <div className="flex flex-col items-start gap-3 rounded border border-zinc-300 p-6 dark:border-zinc-700">
          <p className="text-sm text-zinc-500">이 세션은 이미 종료되었습니다.</p>
          <Link href={`/report/${sessionId}`} className="rounded bg-foreground px-4 py-2 text-sm font-medium text-background">
            리포트 보기
          </Link>
        </div>
      )}

      {view === "result" && feedback && (
        <>
          <FeedbackView feedback={feedback} />
          {error && <p className="text-sm text-red-600">{error}</p>}
          <button
            onClick={handleAdvance}
            className="self-start rounded bg-foreground px-5 py-2 font-medium text-background"
          >
            다음 단계로
          </button>
        </>
      )}
    </div>
  );
}

function FeedbackView({ feedback }: { feedback: EvaluationFeedback }) {
  return (
    <div className="flex flex-col gap-4">
      <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
        <p className="text-sm text-zinc-500">총점</p>
        <p className="text-3xl font-semibold">{feedback.totalScore ?? "-"} / 100</p>
        {(feedback.modelProvider || feedback.modelName) && (
          <p className="mt-1 text-xs text-zinc-400">
            {feedback.modelProvider} · {feedback.modelName}
          </p>
        )}
      </section>

      {Object.keys(feedback.rubricScores).length > 0 && (
        <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
          <h2 className="mb-2 text-sm font-semibold text-zinc-500">항목별 점수</h2>
          <ul className="space-y-1 text-sm">
            {Object.entries(feedback.rubricScores).map(([name, score]) => (
              <li key={name} className="flex justify-between">
                <span>{name}</span>
                <span className="font-mono">{score}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <FeedbackList title="잘한 점" items={feedback.strengths} />
      <FeedbackList title="놓친 점" items={feedback.weaknesses} />

      {feedback.riskFlags.length > 0 && (
        <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
          <h2 className="mb-2 text-sm font-semibold text-zinc-500">실무 리스크</h2>
          <ul className="space-y-2 text-sm">
            {feedback.riskFlags.map((flag, i) => (
              <li key={i}>
                <span className="mr-2 rounded bg-red-100 px-1.5 py-0.5 text-xs font-medium text-red-700 dark:bg-red-950 dark:text-red-300">
                  {flag.severity}
                </span>
                {flag.description}
              </li>
            ))}
          </ul>
        </section>
      )}

      <FeedbackList title="꼬리질문" items={feedback.followupQuestions} />
      <FeedbackList title="권장 변경사항" items={feedback.recommendedChanges} />
    </div>
  );
}

function FeedbackList({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null;
  return (
    <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
      <h2 className="mb-2 text-sm font-semibold text-zinc-500">{title}</h2>
      <ul className="list-inside list-disc space-y-1 text-sm">
        {items.map((item, i) => (
          <li key={i}>{item}</li>
        ))}
      </ul>
    </section>
  );
}
