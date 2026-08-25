const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

export interface UserResponse {
  id: string;
  nickname: string;
  experienceYears: number | null;
  primaryStack: string | null;
}

export interface ScenarioSummary {
  id: string;
  domain: string;
  title: string;
  difficulty: string | null;
}

export type SessionStatus =
  | "IN_PROGRESS"
  | "SUBMITTED"
  | "EVALUATING"
  | "FEEDBACK_READY"
  | "EVALUATION_FAILED"
  | "COMPLETED"
  | "ABANDONED";

export interface SessionResponse {
  id: string;
  status: SessionStatus;
  currentPhase: string | null;
  currentStepPrompt: string | null;
  scenarioVersionId: string;
  startedAt: string;
  completedAt: string | null;
}

export interface SubmissionResponse {
  id: string;
  sessionId: string;
  phase: string;
  revisionNo: number;
}

export interface RiskFlag {
  riskKey: string;
  severity: string;
  description: string | null;
}

export interface EvaluationFeedback {
  id: string;
  submissionId: string;
  rubricVersion: string | null;
  totalScore: number | null;
  rubricScores: Record<string, number>;
  strengths: string[];
  weaknesses: string[];
  riskFlags: RiskFlag[];
  followupQuestions: string[];
  recommendedChanges: string[];
  modelProvider: string | null;
  modelName: string | null;
  createdAt: string | null;
}

export type SimulationActionType = "STRENGTHEN_RATE_LIMIT" | "INCREASE_CACHE_TTL" | "INCREASE_DB_POOL";

export interface SystemState {
  trafficRps: number;
  p95LatencyMs: number;
  errorRate: number;
  availability: number;
  dbReadLoad: number;
  dbWriteLoad: number;
  connectionPoolUsage: number;
  cacheHitRatio: number;
  cacheLatencyMs: number;
  queueLag: number;
  consumerThroughput: number;
  externalDependencyLatencyMs: number;
}

export interface SessionSummary {
  id: string;
  status: SessionStatus;
  scenarioTitle: string;
  startedAt: string;
  completedAt: string | null;
}

export interface SkillProfile {
  userId: string;
  weaknesses: Record<string, number>;
  trend: number[];
}

export interface TimelineEntry {
  phase: string;
  submissionId: string;
  totalScore: number | null;
  topRisks: string[];
}

export interface Report {
  id: string;
  sessionId: string;
  version: number;
  summary: string | null;
  timelineFeedback: TimelineEntry[];
  improvementGuide: string[];
  createdAt: string | null;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new ApiError(res.status, `${path} failed: ${res.status} ${body}`);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export function createUser(input: {
  nickname: string;
  experienceYears?: number;
  primaryStack?: string;
}): Promise<UserResponse> {
  return apiFetch<UserResponse>("/users", { method: "POST", body: JSON.stringify(input) });
}

export function listScenarios(): Promise<ScenarioSummary[]> {
  return apiFetch<ScenarioSummary[]>("/scenarios");
}

export function startSession(userId: string, scenarioId: string): Promise<SessionResponse> {
  return apiFetch<SessionResponse>("/sessions", {
    method: "POST",
    body: JSON.stringify({ userId, scenarioId }),
  });
}

export function getSession(sessionId: string): Promise<SessionResponse> {
  return apiFetch<SessionResponse>(`/sessions/${sessionId}`);
}

export function submitAnswer(
  sessionId: string,
  rawText: string,
  clientRequestId: string,
): Promise<SubmissionResponse> {
  return apiFetch<SubmissionResponse>(`/sessions/${sessionId}/submissions`, {
    method: "POST",
    body: JSON.stringify({ rawText, clientRequestId }),
  });
}

export function getFeedback(submissionId: string): Promise<EvaluationFeedback> {
  return apiFetch<EvaluationFeedback>(`/submissions/${submissionId}/feedback`);
}

export function advanceSession(sessionId: string): Promise<SessionResponse> {
  return apiFetch<SessionResponse>(`/sessions/${sessionId}/advance`, { method: "POST" });
}

export function startIncident(sessionId: string): Promise<SystemState> {
  return apiFetch<SystemState>(`/sessions/${sessionId}/simulation/incident`, { method: "POST" });
}

export function getSimulationState(sessionId: string): Promise<SystemState> {
  return apiFetch<SystemState>(`/sessions/${sessionId}/simulation/state`);
}

export function applySimulationAction(
  sessionId: string,
  actionType: SimulationActionType,
): Promise<SystemState> {
  return apiFetch<SystemState>(`/sessions/${sessionId}/simulation/actions`, {
    method: "POST",
    body: JSON.stringify({ actionType }),
  });
}

export function getUserSessions(userId: string): Promise<SessionSummary[]> {
  return apiFetch<SessionSummary[]>(`/users/${userId}/sessions`);
}

export function getSkillProfile(userId: string): Promise<SkillProfile> {
  return apiFetch<SkillProfile>(`/users/${userId}/skill-profile`);
}

export function getReport(sessionId: string): Promise<Report> {
  return apiFetch<Report>(`/sessions/${sessionId}/report`);
}
