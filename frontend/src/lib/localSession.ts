// Browser-only persistence for the "guest identity" onboarding flow (no real
// auth yet — see backend UserController's kdoc) and per-session drafts, so a
// refresh doesn't lose the user's typed answer or their place in a submit ->
// poll -> feedback flow.

const USER_ID_KEY = "sysdrill:userId";
const USER_NICKNAME_KEY = "sysdrill:userNickname";

export function getStoredUserId(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(USER_ID_KEY);
}

export function storeUser(id: string, nickname: string): void {
  window.localStorage.setItem(USER_ID_KEY, id);
  window.localStorage.setItem(USER_NICKNAME_KEY, nickname);
}

export function getStoredNickname(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(USER_NICKNAME_KEY);
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
