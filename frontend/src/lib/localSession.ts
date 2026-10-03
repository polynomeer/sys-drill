// Browser-only persistence for the signed-in identity (PLAN.md step 30's
// real auth) and per-session drafts, so a refresh doesn't lose the user's
// typed answer or their place in a submit -> poll -> feedback flow.

const USER_NICKNAME_KEY = "sysdrill:userNickname";
const TOKEN_KEY = "sysdrill:token";

export function getStoredToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

/** PLAN.md step 31 — every endpoint is now token-derived, so only the token and a display nickname need to persist. */
export function storeUser(nickname: string, token: string): void {
  window.localStorage.setItem(USER_NICKNAME_KEY, nickname);
  window.localStorage.setItem(TOKEN_KEY, token);
}

export function getStoredNickname(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(USER_NICKNAME_KEY);
}

export function clearStoredUser(): void {
  window.localStorage.removeItem(USER_NICKNAME_KEY);
  window.localStorage.removeItem(TOKEN_KEY);
}

// docs/COMMERCIALIZATION.md — module-level, not persisted: once the revoke
// call in a deliberate logout reaches the server, the token is dead
// immediately, so any other request still in flight (e.g. NotificationBell's
// poll) can legitimately 401 in the same instant. Without this flag, api.ts's
// generic 401 handler would hard-redirect to /login?reason=expired and race
// the logout flow's own (correct) redirect to plain /login. Reset on the
// next full page load regardless, since logout always ends in one.
let loggingOut = false;

export function markLoggingOut(): void {
  loggingOut = true;
}

export function isLoggingOut(): boolean {
  return loggingOut;
}

function draftKey(sessionId: string): string {
  return `sysdrill:draft:${sessionId}`;
}

export function saveDraft(sessionId: string, text: string): void {
  window.localStorage.setItem(draftKey(sessionId), text);
}

export function loadDraft(sessionId: string): string {
  return window.localStorage.getItem(draftKey(sessionId)) ?? "";
}

export function clearDraft(sessionId: string): void {
  window.localStorage.removeItem(draftKey(sessionId));
}

function submissionKey(sessionId: string): string {
  return `sysdrill:submission:${sessionId}`;
}

export function saveSubmissionId(sessionId: string, submissionId: string): void {
  window.localStorage.setItem(submissionKey(sessionId), submissionId);
}

export function loadSubmissionId(sessionId: string): string | null {
  return window.localStorage.getItem(submissionKey(sessionId));
}

function canvasDraftKey(sessionId: string): string {
  return `sysdrill:canvas:${sessionId}`;
}

/** ADR-0036 — the canvas's node/edge graph is not submitted anywhere (only
 * its Mermaid serialization, embedded in the answer draft, is); this is what
 * lets the canvas survive a page reload without needing a Mermaid parser to
 * reconstruct it from the answer text. */
export function saveCanvasDraft(sessionId: string, graphJson: string): void {
  window.localStorage.setItem(canvasDraftKey(sessionId), graphJson);
}

export function loadCanvasDraft(sessionId: string): string | null {
  return window.localStorage.getItem(canvasDraftKey(sessionId));
}

function buildDraftKey(slug: string): string {
  return `sysdrill:build-draft:${slug}`;
}

export function saveBuildDraft(slug: string, sourceCode: string): void {
  window.localStorage.setItem(buildDraftKey(slug), sourceCode);
}

export function loadBuildDraft(slug: string): string {
  return window.localStorage.getItem(buildDraftKey(slug)) ?? "";
}

function buildSubmissionKey(slug: string): string {
  return `sysdrill:build-submission:${slug}`;
}

export function saveBuildSubmissionId(slug: string, submissionId: string): void {
  window.localStorage.setItem(buildSubmissionKey(slug), submissionId);
}

export function loadBuildSubmissionId(slug: string): string | null {
  return window.localStorage.getItem(buildSubmissionKey(slug));
}

// docs/CODECRAFTERS_BENCHMARK.md §3.4 — the Home "처음이세요?" banner stays
// dismissed per browser; a per-viewer convenience, not account state.
const START_HERE_DISMISSED_KEY = "sysdrill:start-here-dismissed";

export function isStartHereDismissed(): boolean {
  if (typeof window === "undefined") return false;
  return window.localStorage.getItem(START_HERE_DISMISSED_KEY) === "1";
}

export function dismissStartHere(): void {
  window.localStorage.setItem(START_HERE_DISMISSED_KEY, "1");
}

// docs/CODECRAFTERS_BENCHMARK.md §3.6 — concepts read to the end open fully
// expanded next time. Per-browser convenience, not learning-progress data.
const READ_CONCEPTS_KEY = "sysdrill:read-concepts";

export function isConceptRead(riskKey: string): boolean {
  if (typeof window === "undefined") return false;
  try {
    return (JSON.parse(window.localStorage.getItem(READ_CONCEPTS_KEY) ?? "[]") as string[]).includes(riskKey);
  } catch {
    return false;
  }
}

export function markConceptRead(riskKey: string): void {
  try {
    const read = new Set(JSON.parse(window.localStorage.getItem(READ_CONCEPTS_KEY) ?? "[]") as string[]);
    read.add(riskKey);
    window.localStorage.setItem(READ_CONCEPTS_KEY, JSON.stringify([...read]));
  } catch {
    // storage unavailable (private mode, quota) — the page just won't remember
  }
}

/** A display theme the viewer picked by hand, remembered together with the app theme it was picked under. */
export type ThemeChoice = { id: string; appTheme: string };

/**
 * The app theme is stored with the pick so that switching app themes falls
 * back to that theme's own matching default (see useThemeChoice) instead of
 * keeping a stale pick.
 */
function saveThemeChoice(key: string, choice: ThemeChoice | null): void {
  if (choice) window.localStorage.setItem(key, JSON.stringify(choice));
  else window.localStorage.removeItem(key);
}

function loadThemeChoice(key: string): ThemeChoice | null {
  if (typeof window === "undefined") return null;
  try {
    const parsed = JSON.parse(window.localStorage.getItem(key) ?? "null") as ThemeChoice | null;
    return parsed && typeof parsed.id === "string" && typeof parsed.appTheme === "string" ? parsed : null;
  } catch {
    // A pre-app-theme plain id — treat as no pick, so the app theme's default applies.
    return null;
  }
}

const CANVAS_THEME_KEY = "sysdrill:canvas-theme";

/** Per-viewer display preference for the design canvas — not part of the graph or the submitted answer. */
export function saveCanvasTheme(choice: ThemeChoice | null): void {
  saveThemeChoice(CANVAS_THEME_KEY, choice);
}

export function loadCanvasTheme(): ThemeChoice | null {
  return loadThemeChoice(CANVAS_THEME_KEY);
}

const EDITOR_THEME_KEY = "sysdrill:editor-theme";

/** Per-viewer display preference for the Build-mode code editor. */
export function saveEditorTheme(choice: ThemeChoice | null): void {
  saveThemeChoice(EDITOR_THEME_KEY, choice);
}

export function loadEditorTheme(): ThemeChoice | null {
  return loadThemeChoice(EDITOR_THEME_KEY);
}
