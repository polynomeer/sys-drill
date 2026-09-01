import { getStoredToken } from "./localSession";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

export interface UserResponse {
  id: string;
  nickname: string;
  experienceYears: number | null;
  primaryStack: string | null;
}

export interface AuthResponse {
  token: string;
  user: UserResponse;
}

export type OrganizationRole = "ADMIN" | "MEMBER";

export interface OrganizationSummary {
  id: string;
  name: string;
  myRole: OrganizationRole;
}

export interface OrganizationMember {
  userId: string;
  nickname: string;
  email: string;
  role: OrganizationRole;
  joinedAt: string | null;
}

export interface OrganizationDetail {
  id: string;
  name: string;
  myRole: OrganizationRole;
  members: OrganizationMember[];
}

export interface OrganizationInvitation {
  id: string;
  inviteeEmail: string;
  role: OrganizationRole;
  token: string;
  expiresAt: string;
  expired: boolean;
}

export interface InvitationPreview {
  organizationName: string;
  inviteeEmail: string;
  role: OrganizationRole;
  expired: boolean;
  alreadyResolved: boolean;
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
  domain: string;
  buildSubmissionId: string | null;
  interviewMode: boolean;
  phaseDeadlineAt: string | null;
  startedAt: string;
  completedAt: string | null;
}

export interface SubmissionResponse {
  id: string;
  sessionId: string;
  phase: string;
  revisionNo: number;
  onTime: boolean | null;
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

export type SimulationActionType =
  | "STRENGTHEN_RATE_LIMIT"
  | "INCREASE_CACHE_TTL"
  | "INCREASE_DB_POOL"
  | "ADD_CONSUMERS"
  | "ENABLE_CIRCUIT_BREAKER"
  | "ADJUST_RETRY_BACKOFF"
  | "SPLIT_CACHE_POLICY"
  | "ENABLE_SINGLE_FLIGHT"
  | "ADD_READ_REPLICA"
  | "ADD_DISPATCHER_WORKERS"
  | "ENABLE_IDEMPOTENT_PG_RETRY"
  | "ISOLATE_PAYMENT_POOL"
  | "ENABLE_FINE_GRAINED_LOCKING"
  | "SHORTEN_HOLD_TIMEOUT"
  | "ENABLE_ATOMIC_INVENTORY_CHECK"
  | "ENABLE_CHECKPOINT_RESTART"
  | "REDUCE_CHUNK_SIZE"
  | "ENABLE_IDEMPOTENT_RECONCILIATION"
  | "SCALE_OUT_REPLICAS"
  | "TUNE_RESOURCE_LIMITS"
  | "ENABLE_ROLLOUT_SAFEGUARD";

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

export interface TimelineStep {
  step: number;
  actionType: string | null;
  label: string;
  appliedAt: string;
  systemState: SystemState;
}

export interface PostmortemActionSummary {
  actionType: string;
  label: string;
  elapsedSeconds: number;
}

export interface Postmortem {
  sessionId: string;
  saved: boolean;
  mttdSeconds: number | null;
  mttrSeconds: number | null;
  actionsTimeline: PostmortemActionSummary[];
  metricsBefore: SystemState | null;
  metricsAfter: SystemState | null;
  rootCause: string | null;
  mitigationActions: string[];
  rootFixActions: string[];
  preventionItems: string[];
  updatedAt: string | null;
}

export interface SavePostmortemRequest {
  rootCause: string;
  mitigationActions: string[];
  rootFixActions: string[];
  preventionItems: string[];
}

export interface SessionSummary {
  id: string;
  status: SessionStatus;
  scenarioTitle: string;
  startedAt: string;
  completedAt: string | null;
}

export type TrendDirection = "IMPROVING" | "DECLINING" | "STABLE" | "INSUFFICIENT_DATA";

export interface SkillProfile {
  userId: string;
  weaknessesByDomain: Record<string, Record<string, number>>;
  trend: number[];
  trendDirection: TrendDirection;
  recommendedDomain: string | null;
}

export interface TimelineEntry {
  phase: string;
  submissionId: string;
  totalScore: number | null;
  topRisks: string[];
  onTime: boolean | null;
}

export interface BuildSummary {
  submissionId: string;
  challengeTitle: string;
  score: number | null;
  totalStages: number;
}

export interface Report {
  id: string;
  sessionId: string;
  version: number;
  summary: string | null;
  timelineFeedback: TimelineEntry[];
  improvementGuide: string[];
  buildSummary: BuildSummary | null;
  createdAt: string | null;
}

export type BuildSubmissionStatus = "QUEUED" | "RUNNING" | "COMPLETED" | "ERROR";
export type BuildStageStatus = "PASSED" | "FAILED";

export interface BuildStageResultResponse {
  stageOrder: number;
  title: string;
  status: BuildStageStatus | null;
  feedback: string | null;
}

export interface BuildSubmissionResponse {
  id: string;
  status: BuildSubmissionStatus;
  score: number | null;
  totalStages: number;
  stages: BuildStageResultResponse[];
  createdAt: string | null;
  completedAt: string | null;
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
  const token = getStoredToken();
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new ApiError(res.status, `${path} failed: ${res.status} ${body}`);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export function signup(input: {
  email: string;
  password: string;
  nickname: string;
  experienceYears?: number;
  primaryStack?: string;
}): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/auth/signup", { method: "POST", body: JSON.stringify(input) });
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
}

export function listScenarios(): Promise<ScenarioSummary[]> {
  return apiFetch<ScenarioSummary[]>("/scenarios");
}

/** PLAN.md step 31 — no userId param: GET /sessions lists the caller's own sessions, derived from their token. */
export function getUserSessions(): Promise<SessionSummary[]> {
  return apiFetch<SessionSummary[]>("/sessions");
}

/** PLAN.md step 31 — no userId param: GET /skill-profile is the caller's own profile, derived from their token. */
export function getSkillProfile(): Promise<SkillProfile> {
  return apiFetch<SkillProfile>("/skill-profile");
}

/** PLAN.md step 30 — no userId param: POST /sessions derives the owner from the caller's stored auth token (see apiFetch), not from client-supplied input. */
export function startSession(
  scenarioId: string,
  buildSubmissionId?: string,
  seed?: string,
  interviewMode?: boolean,
): Promise<SessionResponse> {
  return apiFetch<SessionResponse>("/sessions", {
    method: "POST",
    body: JSON.stringify({ scenarioId, buildSubmissionId, seed, interviewMode }),
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

export function startIncident(sessionId: string, realInfra = false): Promise<SystemState> {
  const query = realInfra ? "?realInfra=true" : "";
  return apiFetch<SystemState>(`/sessions/${sessionId}/simulation/incident${query}`, { method: "POST" });
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

export function getSimulationTimeline(sessionId: string): Promise<TimelineStep[]> {
  return apiFetch<TimelineStep[]>(`/sessions/${sessionId}/simulation/timeline`);
}

export function getPostmortem(sessionId: string): Promise<Postmortem> {
  return apiFetch<Postmortem>(`/sessions/${sessionId}/postmortem`);
}

export function savePostmortem(sessionId: string, request: SavePostmortemRequest): Promise<Postmortem> {
  return apiFetch<Postmortem>(`/sessions/${sessionId}/postmortem`, {
    method: "PUT",
    body: JSON.stringify(request),
  });
}

export function getReport(sessionId: string): Promise<Report> {
  return apiFetch<Report>(`/sessions/${sessionId}/report`);
}

/** PLAN.md step 31 — no userId param: the submission's owner is derived from the caller's token. */
export function submitBuildChallenge(slug: string, sourceCode: string): Promise<BuildSubmissionResponse> {
  return apiFetch<BuildSubmissionResponse>(`/build-challenges/${slug}/submissions`, {
    method: "POST",
    body: JSON.stringify({ sourceCode }),
  });
}

export function getBuildSubmission(submissionId: string): Promise<BuildSubmissionResponse> {
  return apiFetch<BuildSubmissionResponse>(`/build-submissions/${submissionId}`);
}

export function createOrganization(name: string): Promise<OrganizationDetail> {
  return apiFetch<OrganizationDetail>("/organizations", { method: "POST", body: JSON.stringify({ name }) });
}

export function listOrganizations(): Promise<OrganizationSummary[]> {
  return apiFetch<OrganizationSummary[]>("/organizations");
}

export function getOrganization(orgId: string): Promise<OrganizationDetail> {
  return apiFetch<OrganizationDetail>(`/organizations/${orgId}`);
}

export function inviteMember(orgId: string, email: string, role: OrganizationRole): Promise<OrganizationInvitation> {
  return apiFetch<OrganizationInvitation>(`/organizations/${orgId}/invitations`, {
    method: "POST",
    body: JSON.stringify({ email, role }),
  });
}

export function listInvitations(orgId: string): Promise<OrganizationInvitation[]> {
  return apiFetch<OrganizationInvitation[]>(`/organizations/${orgId}/invitations`);
}

export function revokeInvitation(orgId: string, invitationId: string): Promise<void> {
  return apiFetch<void>(`/organizations/${orgId}/invitations/${invitationId}`, { method: "DELETE" });
}

export function previewInvitation(token: string): Promise<InvitationPreview> {
  return apiFetch<InvitationPreview>(`/organizations/invitations/${token}`);
}

export function acceptInvitation(token: string): Promise<OrganizationDetail> {
  return apiFetch<OrganizationDetail>(`/organizations/invitations/${token}/accept`, { method: "POST" });
}

export function removeMember(orgId: string, targetUserId: string): Promise<void> {
  return apiFetch<void>(`/organizations/${orgId}/members/${targetUserId}`, { method: "DELETE" });
}

export function leaveOrganization(orgId: string): Promise<void> {
  return apiFetch<void>(`/organizations/${orgId}/leave`, { method: "POST" });
}
