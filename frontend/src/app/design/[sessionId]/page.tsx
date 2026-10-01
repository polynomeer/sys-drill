"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  ApiError,
  EvaluationFeedback,
  SessionResponse,
  SessionStatus,
  advanceSession,
  getFeedback,
  getMentorHint,
  getSession,
  submitAnswer,
} from "@/lib/api";
import {
  clearDraft,
  getStoredToken,
  loadDraft,
  loadSubmissionId,
  saveDraft,
  saveSubmissionId,
} from "@/lib/localSession";
import { ClarificationPanel } from "./ClarificationPanel";
import { WargameLive } from "./WargameLive";
import { BridgeProgress } from "@/components/BridgeProgress";
import { PhaseTimer } from "@/components/PhaseTimer";
import { DiagramPreview } from "./DiagramPreview";
import { DiagramCanvas } from "./DiagramCanvas";
import { FeedbackDetail } from "@/components/FeedbackDetail";
import { StageList, type Stage } from "@/components/StageList";
import { REPORT_STAGE, stageFromStepType } from "@/lib/stageCopy";
import { DESIGN_GUIDANCE_BY_DOMAIN, INCIDENT_GUIDANCE } from "@/lib/designGuidance";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Textarea } from "@/components/ui/Input";
import { LoadingState } from "@/components/ui/LoadingState";

const POLL_INTERVAL_MS = 1500;

// ADR-0036 — the canvas never becomes a second source of truth: its output is
// always spliced into this same marked region of the free-text answer, so
// `rawText` submitted to the backend is completely unchanged by Round 2.
const CANVAS_BLOCK_START = "<!-- sysdrill-canvas:start -->";
const CANVAS_BLOCK_END = "<!-- sysdrill-canvas:end -->";
const CANVAS_BLOCK_RE = /<!-- sysdrill-canvas:start -->[\s\S]*?<!-- sysdrill-canvas:end -->/;

function upsertCanvasBlock(answer: string, mermaidText: string): string {
  const block = `${CANVAS_BLOCK_START}\n\`\`\`mermaid\n${mermaidText}\n\`\`\`\n${CANVAS_BLOCK_END}`;
  if (CANVAS_BLOCK_RE.test(answer)) return answer.replace(CANVAS_BLOCK_RE, block);
  return `${answer}${answer.trim() ? "\n\n" : ""}${block}`;
}

type ViewState =
  | "loading"
  | "error"
  | "editing"
  | "submitting"
  | "waiting"
  | "result"
  | "advancing"
  | "failed"
  | "completed"
  | "spectating";

const SPECTATOR_POLL_INTERVAL_MS = 3000;

export default function DesignWorkspacePage() {
  const params = useParams<{ sessionId: string }>();
  const router = useRouter();
  const sessionId = params.sessionId;

  const [view, setView] = useState<ViewState>("loading");
  const [session, setSession] = useState<SessionResponse | null>(null);
  const [answer, setAnswer] = useState("");
  const [feedback, setFeedback] = useState<EvaluationFeedback | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [diagramMode, setDiagramMode] = useState<"canvas" | "text">("canvas");
  const [canvasTraits, setCanvasTraits] = useState<Record<string, number>>({});
  const [hints, setHints] = useState<string[] | null>(null);
  const [hintLoading, setHintLoading] = useState(false);
  const [hintError, setHintError] = useState<string | null>(null);
  // System Sandbox — the simulation endpoints (startIncident/applyAction/getState)
  // have no SessionStatus check at all (confirmed by reading SimulationService),
  // so reopening WargameLive for an already-COMPLETED incident session just works
  // unmodified: it resumes the cached state if the 6h Redis TTL hasn't expired, or
  // re-seeds a fresh one from the session's saved SystemTopology if it has —
  // exactly WargameLive's existing 404-recovery path for a normal live incident.
  // Deliberately NOT wired into submit/advance/SessionStateMachine — those stay
  // COMPLETED-terminal so re-experimenting here can never re-grade the session or
  // skew CertificationService's per-session average.
  const [sandboxOpen, setSandboxOpen] = useState(false);
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
    // PLAN.md step 36 — a Game Day spectator never enters the owner's submit/advance
    // state machine below; they get a separate read-only view entirely.
    if (!fetched.isOwner) {
      setView("spectating");
      return;
    }
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
    if (!getStoredToken()) {
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

  // PLAN.md step 36 — a spectator keeps polling the session itself (not just
  // WargameLive's own state poll) to notice phase changes and completion.
  useEffect(() => {
    if (view !== "spectating") return;
    const timer = setInterval(() => {
      getSession(sessionId)
        .then(setSession)
        .catch(() => {
          // transient failure — the next poll tick may succeed
        });
    }, SPECTATOR_POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [view, sessionId]);

  function handleAnswerChange(value: string) {
    setAnswer(value);
    saveDraft(sessionId, value);
  }

  function handleCanvasMermaidChange(mermaidText: string) {
    handleAnswerChange(upsertCanvasBlock(answer, mermaidText));
  }

  /** ADR-0037 — canvas node config becomes this session's starting DesignTraits when the incident starts (WargameLive's `initialTraits` prop). */
  function handleCanvasTraitsChange(traits: Record<string, number>) {
    setCanvasTraits(traits);
  }

  /** AI 4역할 Slice 3 (Mentor) — on-demand hint for the current draft, requested explicitly (not auto/debounced). */
  async function handleRequestHint() {
    setHintLoading(true);
    setHintError(null);
    try {
      const result = await getMentorHint(sessionId, answer);
      setHints(result.hints);
    } catch {
      setHintError("힌트를 가져오지 못했습니다.");
    } finally {
      setHintLoading(false);
    }
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
  const isEditing = view === "editing" || view === "submitting";

  // docs/CODECRAFTERS_BENCHMARK.md §3.3 — the stage list comes from the session's
  // real step types (community scenarios have no INCIDENT step), not fixed labels.
  const stepTypes = session?.stepTypes ?? [];
  const currentIndex = session?.currentPhase ? stepTypes.indexOf(session.currentPhase) : -1;
  const sessionDone = view === "completed" || session?.status === "COMPLETED";
  const stages: Stage[] = [
    ...stepTypes.map((type, i) => ({
      ...stageFromStepType(type, `${i}-${type}`),
      status: sessionDone || i < currentIndex ? ("done" as const) : i === currentIndex ? ("current" as const) : ("upcoming" as const),
    })),
    { ...REPORT_STAGE, status: sessionDone ? ("current" as const) : ("upcoming" as const) },
  ];
  // Incident's live dashboard needs the width, so it only splits at xl; design splits at lg.
  const splitClass = isIncident
    ? "xl:grid xl:grid-cols-[320px_minmax(0,1fr)] xl:items-start"
    : "lg:grid lg:grid-cols-[340px_minmax(0,1fr)] lg:items-start";
  // Sticky only where the panes sit side by side — stacked, a sticky pane would slide over the work area.
  // Capped to the viewport so a long checklist can still scroll into view.
  const stickyClass = isIncident
    ? "xl:sticky xl:top-6 xl:max-h-[calc(100vh-3rem)] xl:overflow-y-auto"
    : "lg:sticky lg:top-6 lg:max-h-[calc(100vh-3rem)] lg:overflow-y-auto";
  const showSplit = session && view !== "loading" && view !== "error" && view !== "spectating";

  return (
    <div className={`mx-auto flex min-h-screen w-full flex-col gap-6 p-6 md:p-8 ${showSplit ? "max-w-7xl" : "max-w-3xl"}`}>
      <div className="flex flex-wrap items-center justify-between gap-3">
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

      {view === "loading" && <LoadingState />}

      {view === "error" && <p className="text-sm text-danger">{error ?? "오류가 발생했습니다."}</p>}

      {view === "spectating" && session && (
        <div className="flex flex-col gap-4">
          <p className="rounded border border-border bg-surface p-3 text-sm text-foreground-muted">
            관전 중입니다 — {session.status === "COMPLETED" ? "이 세션은 종료되었습니다." : "오너가 진행 중인 세션을 실시간으로 보고 있습니다."}
          </p>
          {session.currentPhase === "INCIDENT" ? (
            <WargameLive sessionId={sessionId} domain={domain} isOwner={false} />
          ) : (
            <p className="text-sm text-foreground-muted">아직 장애 대응 단계가 아닙니다. 오너가 설계를 진행 중입니다.</p>
          )}
        </div>
      )}

      {showSplit && (
        <div className={`flex flex-col gap-6 ${splitClass} lg:gap-8`}>
          {/* Left pane — where am I, what's the task, what to cover. Sticky so it stays beside a long answer. */}
          <aside className={`flex flex-col gap-4 ${stickyClass}`}>
            {stages.length > 1 && (
              <Card as="section">
                <h2 className="mb-3 text-xs font-semibold uppercase tracking-wide text-foreground-muted">단계</h2>
                <StageList stages={stages} />
              </Card>
            )}

            {isFollowup && isEditing && (
              <Alert>조건이 변경되었습니다 — 아래 새 조건을 반영해 설계를 다시 검토하세요 (꼬리설계).</Alert>
            )}

            {session?.currentStepPrompt && isEditing && (
              <Card as="section" className="border-accent/40">
                <h2 className="mb-2 text-xs font-semibold uppercase tracking-wide text-accent">문제</h2>
                <p className="whitespace-pre-wrap text-sm leading-relaxed">{session.currentStepPrompt}</p>
              </Card>
            )}

            {isEditing && session?.currentPhase === "INITIAL" && <ClarificationPanel sessionId={sessionId} />}

            {isEditing && (
              <Card as="section" className="text-sm">
                <h2 className="mb-2 text-xs font-semibold uppercase tracking-wide text-foreground-muted">
                  {isIncident ? "회고에 포함하면 좋은 항목" : "답안에 포함하면 좋은 항목"}
                </h2>
                <ul className="list-inside list-disc space-y-1 text-foreground-muted">
                  {guidance.map((section) => (
                    <li key={section}>{section}</li>
                  ))}
                </ul>
              </Card>
            )}

            {isEditing && (
              <div className="flex flex-col gap-2">
                <Button variant="secondary" size="sm" onClick={handleRequestHint} disabled={hintLoading} className="self-start">
                  {hintLoading ? "힌트 불러오는 중..." : "힌트 받기"}
                </Button>
                {hintError && <p className="text-sm text-danger">{hintError}</p>}
                {hints && (
                  <Card as="section" className="text-sm">
                    <h2 className="mb-2 font-semibold text-foreground-muted">멘토 힌트</h2>
                    {hints.length === 0 ? (
                      <p className="text-foreground-muted">지금은 특별히 짚어줄 부분이 없습니다.</p>
                    ) : (
                      <ul className="list-inside list-disc space-y-1 text-foreground-muted">
                        {hints.map((hint, i) => (
                          <li key={i}>{hint}</li>
                        ))}
                      </ul>
                    )}
                  </Card>
                )}
              </div>
            )}
          </aside>

          {/* Right pane — the work itself. */}
          <main className="flex min-w-0 flex-col gap-6">
            {isIncident && isEditing && (
              <WargameLive sessionId={sessionId} domain={domain} isOwner initialTraits={canvasTraits} />
            )}

            {isEditing && (
              <>
                <Textarea
                  label={isIncident ? "대응 회고" : "설계 답안"}
                  className="min-h-[280px] font-mono text-sm [field-sizing:content]"
                  value={answer}
                  onChange={(e) => handleAnswerChange(e.target.value)}
                  onKeyDown={(e) => {
                    // docs/CODECRAFTERS_BENCHMARK.md §3.9 — ⌘↵ / Ctrl↵ submits.
                    if ((e.metaKey || e.ctrlKey) && e.key === "Enter" && view === "editing") {
                      e.preventDefault();
                      handleSubmit();
                    }
                  }}
                  placeholder={
                    isIncident
                      ? "대응 회고를 작성하세요. 입력 내용은 자동으로 이 브라우저에 저장됩니다."
                      : "설계를 자유롭게 작성하세요. 입력 내용은 자동으로 이 브라우저에 저장됩니다."
                  }
                />

                {!isIncident && (
                  <section className="flex flex-col gap-2">
                    <div className="flex items-center gap-1.5 text-xs">
                      <span className="mr-1 text-sm text-foreground-muted">다이어그램</span>
                      <button
                        type="button"
                        onClick={() => setDiagramMode("canvas")}
                        className={`rounded-lg px-2 py-1 font-medium ${diagramMode === "canvas" ? "bg-accent text-accent-foreground" : "border border-border text-foreground-muted"}`}
                      >
                        캔버스
                      </button>
                      <button
                        type="button"
                        onClick={() => setDiagramMode("text")}
                        className={`rounded-lg px-2 py-1 font-medium ${diagramMode === "text" ? "bg-accent text-accent-foreground" : "border border-border text-foreground-muted"}`}
                      >
                        텍스트 (Mermaid)
                      </button>
                    </div>
                    {diagramMode === "canvas" ? (
                      <DiagramCanvas
                        sessionId={sessionId}
                        domain={domain}
                        onMermaidChange={handleCanvasMermaidChange}
                        onTraitsChange={handleCanvasTraitsChange}
                      />
                    ) : (
                      <DiagramPreview answer={answer} onAppend={(text) => handleAnswerChange(answer + text)} />
                    )}
                  </section>
                )}

                {error && <p className="text-sm text-danger">{error}</p>}

                <Button onClick={() => handleSubmit()} disabled={view === "submitting"} className="self-start gap-2">
                  {view === "submitting" ? "제출하는 중..." : "제출하기"}
                  <kbd className="rounded border border-accent-foreground/30 px-1 font-mono text-[10px] opacity-80">⌘↵</kbd>
                </Button>
              </>
            )}

            {(view === "waiting" || view === "advancing") && (
              <Card className="flex flex-col items-center gap-3 p-8 text-center">
                <span className="h-6 w-6 animate-spin rounded-full border-2 border-border border-t-accent" aria-hidden />
                <p className="font-medium">{view === "advancing" ? "다음 단계를 준비하는 중..." : "AI가 답안을 채점하고 있습니다"}</p>
                {view === "waiting" && (
                  <p className="text-sm text-foreground-muted">
                    루브릭 7개 항목으로 평가하고 잘한 점·놓친 점·꼬리질문을 정리합니다. 보통 수십 초 걸립니다.
                  </p>
                )}
              </Card>
            )}

            {view === "failed" && <Alert variant="danger">평가에 실패했습니다. 잠시 후 다시 시도해주세요.</Alert>}

            {view === "completed" && (
              <>
                <Card className="flex flex-col items-start gap-3 p-6">
                  <p className="text-sm text-foreground-muted">이 세션은 이미 종료되었습니다.</p>
                  <div className="flex gap-2">
                    <Button href={`/report/${sessionId}`}>리포트 보기</Button>
                    {isIncident && (
                      <Button variant="secondary" onClick={() => setSandboxOpen((open) => !open)}>
                        {sandboxOpen ? "샌드박스 닫기" : "샌드박스에서 계속 실험하기"}
                      </Button>
                    )}
                  </div>
                </Card>
                {isIncident && sandboxOpen && <WargameLive sessionId={sessionId} domain={domain} isOwner initialTraits={{}} />}
              </>
            )}

            {view === "result" && feedback && (
              <>
                <FeedbackDetail feedback={feedback} />
                {error && <p className="text-sm text-danger">{error}</p>}
                <Button onClick={handleAdvance} className="self-start">
                  다음 단계로
                </Button>
              </>
            )}
          </main>
        </div>
      )}
    </div>
  );
}
