"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import CodeMirror from "@uiw/react-codemirror";
import { python } from "@codemirror/lang-python";
import { javascript } from "@codemirror/lang-javascript";
import { oneDark } from "@codemirror/theme-one-dark";
import { Lock } from "lucide-react";
import {
  ApiError,
  BuildChallenge,
  BuildSubmissionResponse,
  ScenarioSummary,
  getBuildChallenge,
  getBuildSubmission,
  getLatestBuildSubmission,
  getMyPreferences,
  listScenarios,
  startSession,
  submitBuildChallenge,
} from "@/lib/api";
import {
  getStoredToken,
  loadBuildDraft,
  saveBuildDraft,
  saveBuildSubmissionId,
} from "@/lib/localSession";
import { BridgeProgress } from "@/components/BridgeProgress";
import { StageList, type Stage } from "@/components/StageList";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";
import { TestLogPanel } from "./TestLogPanel";
import { LocalSolvePanel } from "./LocalSolvePanel";

const CHALLENGE_DIRS: Record<Language, string> = {
  python: "rate-limiter",
  typescript: "rate-limiter-ts",
};
const LOCAL_WATCH_INTERVAL_MS = 3000;

type Language = "python" | "typescript";

const SLUGS: Record<Language, string> = {
  python: "rate-limiter",
  typescript: "rate-limiter-ts",
};
const POLL_INTERVAL_MS = 1000;

// Module-scope so the array identity is stable across renders — CodeMirror
// reconfigures its extensions whenever this reference changes.
const PYTHON_EXTENSIONS = [python()];
const TS_EXTENSIONS = [javascript({ typescript: true })];

const PYTHON_STUB_TEMPLATE = `# Build your own Rate Limiter — challenges/rate-limiter/rate_limiter.py 와 동일한 스텁입니다.
# 로컬에서 git으로 받아 CLI(submit.sh)로 제출할 수도 있습니다 (README.md 참고).
# 6개 stage를 모두 통과하지 않아도 Bridge로 넘어갈 수 있습니다 — 제출이 완료(COMPLETED)되기만 하면 됩니다.

class InMemoryStore:
    """A minimal key -> counter store, shared by every RateLimiter
    instance that's constructed with the same InMemoryStore object.
    Passing the same store to two RateLimiter instances is how stage 4
    simulates "multiple instances behind a shared rate-limit store"
    without needing a real network call.
    """

    def __init__(self):
        self._data: dict[str, int] = {}

    def incr(self, key: str) -> int:
        self._data[key] = self._data.get(key, 0) + 1
        return self._data[key]

    def expire(self, key: str, seconds: float) -> None:
        # TODO(stage 2): make the counter for \`key\` reset to 0 after \`seconds\`.
        # Until you do, this does nothing — so a window never ends.
        pass


class FaultyStore:
    """Always raises — stage 5 uses this to simulate the backing store
    (e.g. Redis) being unavailable, so you can test fail_mode."""

    def incr(self, key: str) -> int:
        raise ConnectionError("store unavailable")

    def expire(self, key: str, seconds: float) -> None:
        raise ConnectionError("store unavailable")


class RateLimiter:
    def __init__(
        self,
        capacity: int,
        window_seconds: float = 1.0,
        store=None,
        fail_mode: str = "open",
    ):
        self.capacity = capacity
        self.window_seconds = window_seconds
        # Stage 4: callers may pass a *shared* store.
        self.store = store if store is not None else InMemoryStore()
        self.fail_mode = fail_mode

    def allow(self, key: str) -> bool:
        # Stage 1 — uncomment the four lines below and submit.
        # count = self.store.incr(key)
        # if count == 1:
        #     self.store.expire(key, self.window_seconds)
        # return count <= self.capacity
        # TODO(stage 3): make this safe under concurrent calls.
        # TODO(stage 5): when the store raises, admit if fail_mode == "open",
        # reject if fail_mode == "closed".
        # TODO(stage 6): track allowed/rejected counts for \`metrics\`.
        raise NotImplementedError

    @property
    def metrics(self) -> dict:
        # TODO(stage 6): return {"allowed": int, "rejected": int, "reject_rate": float}.
        raise NotImplementedError
`;

const TYPESCRIPT_STUB_TEMPLATE = `// Build your own Rate Limiter — challenges/rate-limiter-ts/rate_limiter.ts 와 동일한 스텁입니다.
// 로컬에서 git으로 받아 CLI(submit.sh)로 제출할 수도 있습니다 (README.md 참고).
// 6개 stage를 모두 통과하지 않아도 Bridge로 넘어갈 수 있습니다 — 제출이 완료(COMPLETED)되기만 하면 됩니다.
//
// 샌드박스는 node --experimental-strip-types로 실행됩니다(타입만 벗겨낼 뿐 완전한
// 트랜스파일이 아님) — 생성자 파라미터 프로퍼티 같은 일부 TS 문법은 지원하지 않습니다.

/**
 * A shared key -> counter store — provided as a working implementation,
 * not a TODO. It deliberately mirrors a real round trip to an external
 * store like Redis: \`incr()\` reads, awaits (simulating network I/O), then
 * writes — so it is NOT atomic on its own; two concurrent \`incr()\` calls
 * on the same key can race. That's intentional: making the overall
 * operation safe under concurrent calls is \`RateLimiter\`'s job (stage 3),
 * exactly like it would be against a real external store used without an
 * atomic command.
 */
export class InMemoryStore {
  private data: Map<string, number> = new Map();

  async incr(key: string): Promise<number> {
    const current = this.data.get(key) ?? 0;
    await new Promise((resolve) => setImmediate(resolve));
    const next = current + 1;
    this.data.set(key, next);
    return next;
  }

  async expire(key: string, seconds: number): Promise<void> {
    await new Promise((resolve) => setImmediate(resolve));
    setTimeout(() => this.data.delete(key), seconds * 1000).unref();
  }
}

/** Always rejects — stage 5 uses this to simulate the backing store (e.g. Redis) being unavailable, so you can test failMode. */
export class FaultyStore {
  async incr(_key: string): Promise<number> {
    throw new Error("store unavailable");
  }

  async expire(_key: string, _seconds: number): Promise<void> {
    throw new Error("store unavailable");
  }
}

export type FailMode = "open" | "closed";

export class RateLimiter {
  private capacity: number;
  private windowSeconds: number;
  private store: InMemoryStore | FaultyStore;
  private failMode: FailMode;

  constructor(capacity: number, windowSeconds: number = 1.0, store?: InMemoryStore | FaultyStore, failMode: FailMode = "open") {
    this.capacity = capacity;
    this.windowSeconds = windowSeconds;
    // Stage 4: callers may pass a *shared* store.
    this.store = store ?? new InMemoryStore();
    this.failMode = failMode;
  }

  async allow(key: string): Promise<boolean> {
    // Stage 1 — uncomment the three lines below and submit.
    // const count = await this.store.incr(key);
    // if (count === 1) await this.store.expire(key, this.windowSeconds);
    // return count <= this.capacity;
    // TODO(stage 3): the store's incr() is NOT atomic (see its own doc
    // comment) — make this method safe when many calls race on the same
    // key at once.
    // TODO(stage 5): when the store throws, admit if failMode === "open",
    // reject if failMode === "closed".
    // TODO(stage 6): track allowed/rejected counts for \`metrics\`.
    throw new Error("not implemented");
  }

  get metrics(): { allowed: number; rejected: number; rejectRate: number } {
    // TODO(stage 6): return { allowed, rejected, rejectRate }.
    throw new Error("not implemented");
  }
}
`;

const STUB_TEMPLATES: Record<Language, string> = {
  python: PYTHON_STUB_TEMPLATE,
  typescript: TYPESCRIPT_STUB_TEMPLATE,
};

type PageState = "loading" | "ready" | "error";
type RunState = "idle" | "submitting" | "grading";

function findBridgeScenario(scenarios: ScenarioSummary[]): ScenarioSummary | null {
  return scenarios.find((s) => s.domain === "coupon") ?? scenarios.find((s) => s.title.includes("쿠폰")) ?? scenarios[0] ?? null;
}

/** The first stage (by order) the latest graded submission didn't pass — where the learner is now.
 * With no graded submission yet, that's stage 1; with everything passed, it's past the last stage. */
function currentStageOf(submission: BuildSubmissionResponse | null, totalStages: number): number {
  if (!submission || submission.status !== "COMPLETED") return 1;
  const firstUnpassed = submission.stages
    .slice()
    .sort((a, b) => a.stageOrder - b.stageOrder)
    .find((s) => s.status !== "PASSED");
  return firstUnpassed ? firstUnpassed.stageOrder : totalStages + 1;
}

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.2·§3.3 (PLAN.md Round B6) — Build as a
 * stage-by-stage drill. Grading still runs all stages every time (the worker
 * doesn't stop at the first failure); the page judges progress against the
 * *current* stage: its instructions are shown, earlier ones are done, later
 * ones stay locked until it passes. The editor stays open after grading so
 * the fix → resubmit loop never leaves the page.
 */
export default function BridgePage() {
  const router = useRouter();
  const [pageState, setPageState] = useState<PageState>("loading");
  const [runState, setRunState] = useState<RunState>("idle");
  const [language, setLanguage] = useState<Language>("python");
  const [sourceCode, setSourceCode] = useState("");
  const [challenge, setChallenge] = useState<BuildChallenge | null>(null);
  const [scenario, setScenario] = useState<ScenarioSummary | null>(null);
  const [submission, setSubmission] = useState<BuildSubmissionResponse | null>(null);
  // The last *fully graded* submission — progress is judged against it, so the stage
  // list doesn't jump back to Stage 1 while a resubmission is still being graded.
  const [lastGraded, setLastGraded] = useState<BuildSubmissionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [startingSession, setStartingSession] = useState(false);
  const [mode, setMode] = useState<"web" | "local">("web");
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  const slug = SLUGS[language];

  const stopPolling = useCallback(() => {
    if (pollTimer.current) {
      clearInterval(pollTimer.current);
      pollTimer.current = null;
    }
  }, []);

  const startPolling = useCallback(
    (submissionId: string) => {
      stopPolling();
      pollTimer.current = setInterval(async () => {
        try {
          const updated = await getBuildSubmission(submissionId);
          setSubmission(updated);
          if (updated.status === "COMPLETED") setLastGraded(updated);
          if (updated.status === "COMPLETED" || updated.status === "ERROR") {
            stopPolling();
            setRunState("idle");
          }
        } catch {
          // transient failure — keep polling, the next tick may succeed
        }
      }, POLL_INTERVAL_MS);
    },
    [stopPolling],
  );

  /** Challenge roadmap + the learner's last submission for this language (restored from localStorage). */
  const loadLanguage = useCallback(
    async (lang: Language) => {
      stopPolling();
      setRunState("idle");
      setSubmission(null);
      setLastGraded(null);
      const nextSlug = SLUGS[lang];
      setSourceCode(loadBuildDraft(nextSlug) || STUB_TEMPLATES[lang]);
      // Restore from the server, not this browser — submissions from submit.sh or another device count too.
      const [nextChallenge, last] = await Promise.all([
        getBuildChallenge(nextSlug),
        getLatestBuildSubmission(nextSlug).catch(() => null),
      ]);
      setChallenge(nextChallenge);
      setSubmission(last);
      if (last?.status === "COMPLETED") setLastGraded(last);
      if (last && (last.status === "QUEUED" || last.status === "RUNNING")) {
        setRunState("grading");
        startPolling(last.id);
      }
    },
    [startPolling, stopPolling],
  );

  useEffect(() => {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    // docs/CODECRAFTERS_BENCHMARK.md §3.4 — open in the learner's preferred language (default Python).
    const initialLanguage = getMyPreferences()
      .then((p): Language => (p.preferredLanguage === "TYPESCRIPT" ? "typescript" : "python"))
      .catch((): Language => "python");
    Promise.all([
      listScenarios(),
      initialLanguage.then((lang) => {
        setLanguage(lang);
        return loadLanguage(lang);
      }),
    ])
      .then(([scenarios]) => {
        setScenario(findBridgeScenario(scenarios));
        setPageState("ready");
      })
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : "챌린지를 불러오지 못했습니다.");
        setPageState("error");
      });
    return () => stopPolling();
  }, [router, loadLanguage, stopPolling]);

  // PLAN.md Round B12 — while "로컬에서 풀기" is open and nothing is grading, watch for a
  // new submission (typically from ./submit.sh) and pick it up into the test log.
  const submissionId = submission?.id;
  useEffect(() => {
    if (mode !== "local" || runState !== "idle" || pageState !== "ready") return;
    const timer = setInterval(async () => {
      try {
        const latest = await getLatestBuildSubmission(slug);
        if (!latest || latest.id === submissionId) return;
        setSubmission(latest);
        if (latest.status === "COMPLETED") {
          setLastGraded(latest);
        } else if (latest.status === "QUEUED" || latest.status === "RUNNING") {
          setRunState("grading");
          startPolling(latest.id);
        }
      } catch {
        // transient — the next tick retries
      }
    }, LOCAL_WATCH_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [mode, runState, pageState, slug, submissionId, startPolling]);

  function handleLanguageChange(next: Language) {
    setLanguage(next);
    setError(null);
    loadLanguage(next).catch((err) => setError(err instanceof ApiError ? err.message : "챌린지를 불러오지 못했습니다."));
  }

  function handleSourceChange(value: string) {
    setSourceCode(value);
    saveBuildDraft(slug, value);
  }

  async function handleSubmit() {
    if (!getStoredToken()) {
      router.replace("/onboarding");
      return;
    }
    if (!sourceCode.trim()) {
      setError("코드를 입력해주세요.");
      return;
    }
    setRunState("submitting");
    setError(null);
    try {
      const created = await submitBuildChallenge(slug, sourceCode);
      saveBuildSubmissionId(slug, created.id);
      setSubmission(created);
      setRunState("grading");
      startPolling(created.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "제출에 실패했습니다.");
      setRunState("idle");
    }
  }

  async function handleContinueToDesign() {
    if (!getStoredToken() || !scenario || !submission) return;
    setStartingSession(true);
    setError(null);
    try {
      const session = await startSession(scenario.id, submission.id);
      router.push(`/design/${session.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "설계 세션을 시작하지 못했습니다.");
      setStartingSession(false);
    }
  }

  const totalStages = challenge?.stages.length ?? 0;
  const graded = lastGraded;
  const currentStage = currentStageOf(graded, totalStages);
  const allPassed = totalStages > 0 && currentStage > totalStages;
  const current = challenge?.stages.find((s) => s.stageOrder === currentStage);
  const stages: Stage[] = (challenge?.stages ?? []).map((s) => ({
    key: String(s.stageOrder),
    title: s.title,
    description: s.stageOrder <= currentStage ? (s.spec ?? undefined) : undefined,
    lockedHint: s.stageOrder > currentStage ? "이전 단계를 통과하면 열립니다" : undefined,
    status: s.stageOrder < currentStage ? "done" : s.stageOrder === currentStage ? "current" : "upcoming",
  }));
  const busy = runState !== "idle";

  return (
    <div className="mx-auto flex min-h-screen w-full max-w-7xl flex-col gap-6 p-6 md:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">Build your own Rate Limiter</h1>
          <p className="mt-1 text-sm text-foreground-muted">
            단계를 하나씩 통과하세요. 제출이 끝나면 언제든 {scenario ? `"${scenario.title}"` : "연결된"} 설계 → 꼬리설계 →
            Wargame으로 넘어갈 수 있습니다.
          </p>
        </div>
        <BridgeProgress current="build" />
      </div>

      {pageState === "loading" && <LoadingState />}
      {pageState === "error" && <p className="text-sm text-danger">{error}</p>}

      {pageState === "ready" && challenge && (
        <div className="flex flex-col gap-6 lg:grid lg:grid-cols-[340px_minmax(0,1fr)] lg:items-start lg:gap-8">
          <aside className="flex flex-col gap-4 lg:sticky lg:top-6 lg:max-h-[calc(100vh-3rem)] lg:overflow-y-auto">
            <Card as="section">
              <div className="mb-3 flex items-center justify-between text-xs text-foreground-muted">
                <span className="font-semibold uppercase tracking-wide">단계</span>
                <span>
                  {Math.min(currentStage - 1, totalStages)} / {totalStages} 단계 완료
                </span>
              </div>
              <StageList stages={stages} />
            </Card>

            {allPassed ? (
              <Card as="section" className="border-success/40">
                <p className="font-medium text-success">모든 단계를 통과했습니다</p>
                <p className="mt-1 text-sm text-foreground-muted">
                  직접 만든 Rate Limiter가 설계 단계에서 어떤 선택으로 이어지는지 확인해보세요.
                </p>
              </Card>
            ) : (
              current && (
                <Card as="section" className="border-accent/40">
                  <p className="text-xs font-semibold uppercase tracking-wide text-accent">Stage {current.stageOrder}</p>
                  <h2 className="mt-1 font-semibold">{current.title}</h2>
                  <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-foreground">
                    {current.instructions ?? current.spec}
                  </p>
                  {current.instructions && current.spec && (
                    <p className="mt-3 border-t border-border pt-3 text-xs text-foreground-muted">학습 포인트 — {current.spec}</p>
                  )}
                </Card>
              )
            )}

            {!allPassed && currentStage < totalStages && (
              <p className="flex items-center gap-1.5 text-xs text-foreground-muted">
                <Lock className="h-3.5 w-3.5" aria-hidden /> 다음 단계 지시문은 Stage {currentStage}를 통과하면 열립니다.
              </p>
            )}
          </aside>

          <main className="flex min-w-0 flex-col gap-4">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs text-foreground-muted">언어</span>
              {(["python", "typescript"] as const).map((lang) => (
                <Button
                  key={lang}
                  size="sm"
                  variant={language === lang ? "primary" : "secondary"}
                  onClick={() => handleLanguageChange(lang)}
                  disabled={busy}
                >
                  {lang === "python" ? "Python" : "TypeScript"}
                </Button>
              ))}
              <span className="ml-auto font-mono text-xs text-foreground-muted">{challenge.sourceFileName}</span>
            </div>

            <div className="flex gap-1 border-b border-border text-sm">
              {(
                [
                  ["web", "웹 에디터"],
                  ["local", "로컬에서 풀기"],
                ] as const
              ).map(([m, label]) => (
                <button
                  key={m}
                  type="button"
                  onClick={() => setMode(m)}
                  aria-pressed={mode === m}
                  className={`-mb-px border-b-2 px-3 py-2 ${mode === m ? "border-accent text-foreground" : "border-transparent text-foreground-muted hover:text-foreground"}`}
                >
                  {label}
                </button>
              ))}
            </div>

            {mode === "local" ? (
              <LocalSolvePanel dir={CHALLENGE_DIRS[language]} waiting={runState === "idle"} />
            ) : (
              // docs/CODECRAFTERS_BENCHMARK.md §3.9 — ⌘↵ / Ctrl↵ submits. Caught in the capture phase so
              // CodeMirror's own Mod-Enter ("insert blank line") never sees it.
              <div
                onKeyDownCapture={(e) => {
                  if ((e.metaKey || e.ctrlKey) && e.key === "Enter") {
                    e.preventDefault();
                    e.stopPropagation();
                    if (!busy) handleSubmit();
                  }
                }}
              >
                <CodeMirror
                  value={sourceCode}
                  onChange={handleSourceChange}
                  height="420px"
                  theme={oneDark}
                  extensions={language === "python" ? PYTHON_EXTENSIONS : TS_EXTENSIONS}
                  className="overflow-hidden rounded-lg border border-border text-sm"
                  basicSetup={{ tabSize: 4 }}
                  editable={!busy}
                />
              </div>
            )}

            {error && <p className="text-sm text-danger">{error}</p>}

            <div className="flex flex-wrap items-center gap-3">
              {mode === "web" && <Button onClick={handleSubmit} disabled={busy}>
                {runState === "submitting" ? "제출하는 중..." : runState === "grading" ? "채점 중..." : graded ? "다시 제출하기" : "제출하기"}
                <kbd className="ml-2 rounded border border-accent-foreground/30 px-1 font-mono text-[10px] opacity-80">⌘↵</kbd>
              </Button>}
              {graded && (
                <Button variant="secondary" onClick={handleContinueToDesign} disabled={startingSession || !scenario || busy}>
                  {startingSession ? "이동하는 중..." : `다음: ${scenario?.title ?? "설계"}로 이동 →`}
                </Button>
              )}
            </div>

            {submission && <TestLogPanel submission={submission} currentStage={Math.min(currentStage, totalStages)} grading={runState === "grading"} />}
          </main>
        </div>
      )}
    </div>
  );
}
