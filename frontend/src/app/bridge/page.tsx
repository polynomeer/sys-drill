"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ApiError,
  BuildSubmissionResponse,
  ScenarioSummary,
  getBuildSubmission,
  listScenarios,
  startSession,
  submitBuildChallenge,
} from "@/lib/api";
import { getStoredUserId, loadBuildDraft, saveBuildDraft, saveBuildSubmissionId } from "@/lib/localSession";
import { BridgeProgress } from "@/components/BridgeProgress";

const CHALLENGE_SLUG = "rate-limiter";
const POLL_INTERVAL_MS = 1000;

const STUB_TEMPLATE = `# Build your own Rate Limiter — challenges/rate-limiter/rate_limiter.py 와 동일한 스텁입니다.
# 로컬에서 git으로 받아 CLI(submit.sh)로 제출할 수도 있습니다 (README.md 참고).
# 6개 stage를 모두 통과하지 않아도 Bridge로 넘어갈 수 있습니다 — 제출이 완료(COMPLETED)되기만 하면 됩니다.

class InMemoryStore:
    def __init__(self):
        self._data = {}

    def incr(self, key):
        raise NotImplementedError  # TODO(stage 1)

    def expire(self, key, seconds):
        raise NotImplementedError  # TODO(stage 1)


class FaultyStore:
    def incr(self, key):
        raise ConnectionError("store unavailable")

    def expire(self, key, seconds):
        raise ConnectionError("store unavailable")


class RateLimiter:
    def __init__(self, capacity, window_seconds=1.0, store=None, fail_mode="open"):
        raise NotImplementedError  # TODO(stage 1)

    def allow(self, key):
        raise NotImplementedError  # TODO(stage 1-6)

    @property
    def metrics(self):
        raise NotImplementedError  # TODO(stage 6)
`;

type ViewState = "loading" | "editing" | "submitting" | "waiting" | "result" | "error";

function findBridgeScenario(scenarios: ScenarioSummary[]): ScenarioSummary | null {
  return scenarios.find((s) => s.domain === "coupon") ?? scenarios.find((s) => s.title.includes("쿠폰")) ?? scenarios[0] ?? null;
}

export default function BridgePage() {
  const router = useRouter();
  const [view, setView] = useState<ViewState>("loading");
  const [sourceCode, setSourceCode] = useState("");
  const [scenario, setScenario] = useState<ScenarioSummary | null>(null);
  const [submission, setSubmission] = useState<BuildSubmissionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [startingSession, setStartingSession] = useState(false);
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollTimer.current) {
      clearInterval(pollTimer.current);
      pollTimer.current = null;
    }
  }, []);

  useEffect(() => {
    const userId = getStoredUserId();
    if (!userId) {
      router.replace("/onboarding");
      return;
    }

    // Data fetch + localStorage read on mount, not a cascading render loop.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setSourceCode(loadBuildDraft(CHALLENGE_SLUG) || STUB_TEMPLATE);

    listScenarios()
      .then((scenarios) => {
        setScenario(findBridgeScenario(scenarios));
        setView("editing");
      })
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : "시나리오를 불러오지 못했습니다.");
        setView("error");
      });

    return () => stopPolling();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [router]);

  function handleSourceChange(value: string) {
    setSourceCode(value);
    saveBuildDraft(CHALLENGE_SLUG, value);
  }

  function startPolling(submissionId: string) {
    stopPolling();
    pollTimer.current = setInterval(async () => {
      try {
        const updated = await getBuildSubmission(submissionId);
        setSubmission(updated);
        if (updated.status === "COMPLETED" || updated.status === "ERROR") {
          stopPolling();
          setView("result");
        }
      } catch {
        // transient failure — keep polling, the next tick may succeed
      }
    }, POLL_INTERVAL_MS);
  }

  async function handleSubmit() {
    const userId = getStoredUserId();
    if (!userId) {
      router.replace("/onboarding");
      return;
    }
    if (!sourceCode.trim()) {
      setError("코드를 입력해주세요.");
      return;
    }
    setView("submitting");
    setError(null);
    try {
      const created = await submitBuildChallenge(CHALLENGE_SLUG, userId, sourceCode);
      saveBuildSubmissionId(CHALLENGE_SLUG, created.id);
      setSubmission(created);
      setView("waiting");
      startPolling(created.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "제출에 실패했습니다.");
      setView("editing");
    }
  }

  async function handleContinueToDesign() {
    const userId = getStoredUserId();
    if (!userId || !scenario || !submission) return;
    setStartingSession(true);
    setError(null);
    try {
      const session = await startSession(userId, scenario.id, submission.id);
      router.push(`/design/${session.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "설계 세션을 시작하지 못했습니다.");
      setStartingSession(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-3xl flex-col gap-6 p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Build your own Rate Limiter</h1>
        <BridgeProgress current="build" />
      </div>

      <p className="text-sm text-zinc-500">
        Rate Limiter를 구현해 제출하면, 완료 즉시 이어서 {scenario ? `"${scenario.title}"` : "연결된"} 시스템 설계 →
        꼬리설계 → Wargame으로 넘어갑니다. 실제로 6개 stage를 모두 통과하지 못해도 제출이 완료되기만 하면 다음 단계로 진행할
        수 있습니다.
      </p>

      {view === "loading" && <p className="text-sm text-zinc-500">불러오는 중...</p>}
      {error && <p className="text-sm text-red-600">{error}</p>}

      {(view === "editing" || view === "submitting") && (
        <>
          <textarea
            className="min-h-[360px] rounded border border-zinc-300 p-3 font-mono text-sm dark:border-zinc-700 dark:bg-zinc-900"
            value={sourceCode}
            onChange={(e) => handleSourceChange(e.target.value)}
            spellCheck={false}
          />
          <button
            onClick={handleSubmit}
            disabled={view === "submitting"}
            className="self-start rounded bg-foreground px-5 py-2 font-medium text-background disabled:opacity-50"
          >
            {view === "submitting" ? "제출하는 중..." : "제출하기"}
          </button>
        </>
      )}

      {view === "waiting" && (
        <div className="flex flex-col items-center gap-3 rounded border border-zinc-300 p-8 dark:border-zinc-700">
          <p className="text-sm text-zinc-500">샌드박스에서 stage를 채점하는 중입니다 ({submission?.status ?? "..."})...</p>
        </div>
      )}

      {view === "result" && submission && (
        <>
          <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
            <p className="text-sm text-zinc-500">점수</p>
            <p className="text-3xl font-semibold">
              {submission.score ?? 0} / {submission.totalStages}
            </p>
          </section>

          <section className="rounded border border-zinc-300 p-4 dark:border-zinc-700">
            <h2 className="mb-3 text-sm font-semibold text-zinc-500">Stage별 결과</h2>
            <ul className="flex flex-col gap-3">
              {submission.stages.map((stage) => (
                <li
                  key={stage.stageOrder}
                  className="border-t border-zinc-200 pt-3 first:border-t-0 first:pt-0 dark:border-zinc-800"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">
                      {stage.stageOrder}. {stage.title}
                    </span>
                    <span
                      className={
                        stage.status === "PASSED"
                          ? "rounded bg-emerald-100 px-1.5 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300"
                          : "rounded bg-red-100 px-1.5 py-0.5 text-xs font-medium text-red-700 dark:bg-red-950 dark:text-red-300"
                      }
                    >
                      {stage.status ?? "-"}
                    </span>
                  </div>
                  {stage.feedback && <p className="mt-1 text-xs text-zinc-500">{stage.feedback}</p>}
                </li>
              ))}
            </ul>
          </section>

          <button
            onClick={handleContinueToDesign}
            disabled={startingSession || !scenario}
            className="self-start rounded bg-foreground px-5 py-2 font-medium text-background disabled:opacity-50"
          >
            {startingSession ? "이동하는 중..." : `다음: ${scenario?.title ?? "설계"}로 이동`}
          </button>
        </>
      )}
    </div>
  );
}
