"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import CodeMirror, { type Extension } from "@uiw/react-codemirror";
import { python } from "@codemirror/lang-python";
import { javascript } from "@codemirror/lang-javascript";
import { java } from "@codemirror/lang-java";
import { go } from "@codemirror/lang-go";
import { StreamLanguage } from "@codemirror/language";
import { kotlin } from "@codemirror/legacy-modes/mode/clike";
import { Lock } from "lucide-react";
import {
  ApiError,
  BuildChallenge,
  BuildChallengeSummary,
  BuildSubmissionResponse,
  ScenarioSummary,
  getBuildChallenge,
  getBuildSubmission,
  getLatestBuildSubmission,
  getMyPreferences,
  listBuildChallenges,
  listScenarios,
  startSession,
  submitBuildChallenge,
} from "@/lib/api";
import {
  getStoredToken,
  loadBuildDraft,
  loadEditorTheme,
  saveBuildDraft,
  saveBuildSubmissionId,
  saveEditorTheme,
} from "@/lib/localSession";
import { APP_EDITOR_THEMES, AUTO_EDITOR_THEME, EXTRA_EDITOR_THEMES, resolveEditorTheme } from "@/lib/editorThemes";
import { APP_THEMES } from "@/lib/appTheme";
import { useThemeChoice } from "@/lib/useAppTheme";
import { BridgeProgress } from "@/components/BridgeProgress";
import { StageList, type Stage } from "@/components/StageList";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { LoadingState } from "@/components/ui/LoadingState";
import { TestLogPanel } from "./TestLogPanel";
import { LocalSolvePanel } from "./LocalSolvePanel";
import {
  type BuildLanguage as Language,
  CHALLENGE_FAMILIES,
  familyInfo,
  familyOf,
  languageLabel,
  languageOfSlug,
  slugFor,
  toBuildLanguage,
} from "@/lib/buildChallenges";

const LOCAL_WATCH_INTERVAL_MS = 3000;
const POLL_INTERVAL_MS = 1000;

// Module-scope so the array identity is stable across renders — CodeMirror
// reconfigures its extensions whenever this reference changes.
// Kotlin has no first-party CodeMirror 6 package, so it uses the legacy clike stream mode.
const EDITOR_EXTENSIONS: Record<Language, Extension[]> = {
  python: [python()],
  typescript: [javascript({ typescript: true })],
  java: [java()],
  kotlin: [StreamLanguage.define(kotlin)],
  go: [go()],
};

type PageState = "loading" | "ready" | "error";
type RunState = "idle" | "submitting" | "grading";

/** The official Drill this challenge leads into (Bridge Mode) — see lib/buildChallenges.ts for why each domain. */
function findBridgeScenario(scenarios: ScenarioSummary[], family: string): ScenarioSummary | null {
  const domain = familyInfo(family)?.domain ?? "coupon";
  return scenarios.find((s) => s.domain === domain && !s.creatorNickname && !s.organizationId) ?? null;
}

/** `?challenge=<slug>` — read once on mount (no useSearchParams, so the page needs no Suspense boundary). */
function requestedChallenge(): string | null {
  if (typeof window === "undefined") return null;
  return new URLSearchParams(window.location.search).get("challenge");
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
  const [family, setFamily] = useState<string>("rate-limiter");
  const [catalog, setCatalog] = useState<BuildChallengeSummary[]>([]);
  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [sourceCode, setSourceCode] = useState("");
  const [challenge, setChallenge] = useState<BuildChallenge | null>(null);
  const [submission, setSubmission] = useState<BuildSubmissionResponse | null>(null);
  // The last *fully graded* submission — progress is judged against it, so the stage
  // list doesn't jump back to Stage 1 while a resubmission is still being graded.
  const [lastGraded, setLastGraded] = useState<BuildSubmissionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [startingSession, setStartingSession] = useState(false);
  const [mode, setMode] = useState<"web" | "local">("web");
  // Defaults to the app theme's own editor; a hand pick holds until the app theme changes.
  const {
    appTheme,
    themeId: editorThemeId,
    selectTheme: selectEditorTheme,
  } = useThemeChoice(AUTO_EDITOR_THEME, loadEditorTheme, saveEditorTheme);
  const editorTheme = resolveEditorTheme(editorThemeId, appTheme);
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  const slug = slugFor(family, language);
  const scenario = findBridgeScenario(scenarios, family);
  /** Languages this challenge actually ships in (every challenge has Java/Kotlin/Go twins; only rate-limiter also has TypeScript). */
  const familyLanguages: Language[] = catalog
    .filter((c) => familyOf(c.slug) === family)
    .map((c) => toBuildLanguage(c.language));

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

  /** Challenge roadmap + the learner's last submission for this challenge (restored from the server). */
  const loadChallenge = useCallback(
    async (nextSlug: string) => {
      stopPolling();
      setRunState("idle");
      setSubmission(null);
      setLastGraded(null);
      // Restore from the server, not this browser — submissions from submit.sh or another device count too.
      const [nextChallenge, last] = await Promise.all([
        getBuildChallenge(nextSlug),
        getLatestBuildSubmission(nextSlug).catch(() => null),
      ]);
      // PLAN.md Round E2 — the stub comes from the DB (same file as challenges/<slug>/), not a frontend constant.
      setSourceCode(loadBuildDraft(nextSlug) || nextChallenge.starterCode || "");
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
    const preferred = getMyPreferences()
      .then((p): Language => (p.preferredLanguage ? toBuildLanguage(p.preferredLanguage.toLowerCase()) : "python"))
      .catch((): Language => "python");
    Promise.all([listScenarios(), listBuildChallenges(), preferred])
      .then(async ([nextScenarios, nextCatalog, preferredLanguage]) => {
        setScenarios(nextScenarios);
        setCatalog(nextCatalog);
        // PLAN.md Round E2 — `?challenge=<slug>` (from a concept page or a track) picks the challenge;
        // an explicit language slug (`-ts`, `-go`, …) also picks the language, otherwise the preference applies where it exists.
        const requested = requestedChallenge();
        const known = nextCatalog.some((c) => c.slug === requested) ? requested : null;
        const nextFamily = known ? familyOf(known) : "rate-limiter";
        const available = nextCatalog.filter((c) => familyOf(c.slug) === nextFamily).map((c) => c.slug);
        const wanted = known && known !== familyOf(known) ? languageOfSlug(known) : preferredLanguage;
        const nextLanguage: Language = available.includes(slugFor(nextFamily, wanted)) ? wanted : "python";
        setFamily(nextFamily);
        setLanguage(nextLanguage);
        await loadChallenge(slugFor(nextFamily, nextLanguage));
        setPageState("ready");
      })
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : "챌린지를 불러오지 못했습니다.");
        setPageState("error");
      });
    return () => stopPolling();
  }, [router, loadChallenge, stopPolling]);

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
    router.replace(`/bridge?challenge=${slugFor(family, next)}`, { scroll: false });
    loadChallenge(slugFor(family, next)).catch((err) => setError(err instanceof ApiError ? err.message : "챌린지를 불러오지 못했습니다."));
  }

  function handleFamilyChange(next: string) {
    const nextLanguage: Language = catalog.some((c) => c.slug === slugFor(next, language)) ? language : "python";
    setFamily(next);
    setLanguage(nextLanguage);
    setError(null);
    router.replace(`/bridge?challenge=${slugFor(next, nextLanguage)}`, { scroll: false });
    loadChallenge(slugFor(next, nextLanguage)).catch((err) => setError(err instanceof ApiError ? err.message : "챌린지를 불러오지 못했습니다."));
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
    <div className="mx-auto flex min-h-screen w-full max-w-none flex-col gap-6 p-6 md:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">{challenge?.title ?? "Build"}</h1>
          <p className="mt-1 text-sm text-foreground-muted">
            단계를 하나씩 통과하세요. 제출이 끝나면 언제든 {scenario ? `"${scenario.title}"` : "연결된"} 설계 → 꼬리설계 →
            Wargame으로 넘어갈 수 있습니다.
          </p>
        </div>
        <BridgeProgress current="build" />
      </div>

      {pageState === "ready" && (
        <nav aria-label="Build 챌린지" className="-mt-2 flex flex-wrap gap-2">
          {CHALLENGE_FAMILIES.filter((f) => catalog.some((c) => familyOf(c.slug) === f.family)).map((f) => (
            <Button
              key={f.family}
              size="sm"
              variant={f.family === family ? "primary" : "secondary"}
              onClick={() => handleFamilyChange(f.family)}
              disabled={busy || f.family === family}
              aria-pressed={f.family === family}
            >
              {f.title}
            </Button>
          ))}
        </nav>
      )}

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
                  직접 만든 {familyInfo(family)?.title ?? "컴포넌트"}가 설계 단계에서 어떤 선택으로 이어지는지 확인해보세요.
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
              {familyLanguages.map((lang) => (
                <Button
                  key={lang}
                  size="sm"
                  variant={language === lang ? "primary" : "secondary"}
                  onClick={() => handleLanguageChange(lang)}
                  disabled={busy}
                >
                  {languageLabel(lang)}
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
              {mode === "web" && (
                <label className="ml-auto flex items-center gap-1.5 self-center text-xs text-foreground-muted">
                  테마
                  <select
                    className="rounded border border-border bg-surface px-2 py-1 text-xs text-foreground"
                    value={editorThemeId}
                    onChange={(e) => selectEditorTheme(e.target.value)}
                  >
                    <option value={AUTO_EDITOR_THEME}>
                      앱 테마에 맞춤 ({APP_THEMES.find((t) => t.id === appTheme)?.label})
                    </option>
                    <optgroup label="앱 테마">
                      {APP_THEMES.map((t) => (
                        <option key={t.id} value={APP_EDITOR_THEMES[t.id].id}>
                          {APP_EDITOR_THEMES[t.id].label}
                        </option>
                      ))}
                    </optgroup>
                    <optgroup label="기타">
                      {EXTRA_EDITOR_THEMES.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.label}
                        </option>
                      ))}
                    </optgroup>
                  </select>
                </label>
              )}
            </div>

            {mode === "local" ? (
              <LocalSolvePanel dir={slug} waiting={runState === "idle"} />
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
                  theme={editorTheme.extension}
                  extensions={EDITOR_EXTENSIONS[language]}
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
