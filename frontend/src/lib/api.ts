import { clearStoredUser, getStoredToken, isLoggingOut } from "./localSession";

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

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

export interface OrganizationDashboardMember {
  userId: string;
  nickname: string;
  email: string;
  role: OrganizationRole;
  completedSessionCount: number;
  lastActiveAt: string | null;
  trendDirection: TrendDirection;
}

export interface OrganizationDashboard {
  members: OrganizationDashboardMember[];
}

export type OrganizationAuditAction =
  | "ORGANIZATION_CREATED"
  | "MEMBER_INVITED"
  | "INVITATION_REVOKED"
  | "MEMBER_JOINED"
  | "MEMBER_REMOVED"
  | "MEMBER_LEFT"
  | "CUSTOM_SCENARIO_CREATED"
  | "CURRICULUM_UPDATED";

export interface AuditLogEntry {
  id: string;
  actorNickname: string;
  actorEmail: string;
  action: OrganizationAuditAction;
  detail: Record<string, unknown> | null;
  createdAt: string | null;
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
  organizationId: string | null;
  creatorNickname?: string | null;
  /** docs/LEARNING_COMMUNITY_PLAN.md §6.3 — 공개 목록(listScenarios)과 마켓플레이스 목록에서 채워진다. */
  completedCount?: number | null;
  /** 실제로 푼 사람들의 평균 점수 — 별점보다 객관적인 난이도 신호. */
  averageScore?: number | null;
  /** docs/CODECRAFTERS_BENCHMARK.md §3.5 — published version's step types in order; filled by listScenarios(). */
  stepTypes?: string[] | null;
}

export interface ScenarioStepSummary {
  order: number;
  type: string;
}

export interface ScenarioDetail extends ScenarioSummary {
  baseRequirements: unknown;
  /** docs/CODECRAFTERS_BENCHMARK.md §3.1 — filled only by getScenario(), for the Drill overview roadmap. */
  steps?: ScenarioStepSummary[] | null;
  /** INITIAL step prompt only; FOLLOWUP/INCIDENT prompts are never exposed before the session reaches them. */
  initialPrompt?: string | null;
  /** Same difficulty signal as the marketplace list, but for every public scenario (that list omits official ones). */
  completedCount?: number | null;
  averageScore?: number | null;
}

export interface CreateCustomScenarioRequest {
  title: string;
  difficulty?: string;
  domain: string;
  initialPrompt: string;
  followupPrompt: string;
  /** ADR-0038 — only meaningful for createCustomScenario (organization scenarios); publishMarketplaceScenario ignores it. Requires `domain` to be one of the 7 known simulation domains. */
  incidentPrompt?: string;
  /** ROADMAP.md Phase 4 "커스텀 루브릭" — only meaningful for createCustomScenario; publishMarketplaceScenario ignores it. Omitted, evaluation uses the default 7-dimension rubric. Provided, must sum to 100. */
  rubricDimensions?: Record<string, number>;
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
  isOwner: boolean;
  /** docs/CODECRAFTERS_BENCHMARK.md §3.3 — this session's step types in order (INITIAL, FOLLOWUP[, INCIDENT]). */
  stepTypes?: string[];
  /** PLAN.md Round E3 — the scenario this session's version belongs to. */
  scenarioId?: string | null;
}

export interface ChatMessage {
  id: string;
  authorUserId: string;
  authorNickname: string;
  body: string;
  createdAt: string | null;
}

export interface GameDaySession {
  sessionId: string;
  scenarioTitle: string;
  ownerNickname: string;
  domain: string;
  status: SessionStatus;
  currentPhase: string | null;
  startedAt: string;
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
  /** Derived on the backend (SystemState.kt computed properties), not stored — see ADR for Round 3 of the UI/UX renewal. */
  cpuUtilization: number;
  memoryUtilization: number;
  /** Phase 3-A (docs/DRILLS_SIMULATION_VISION.md §6) — log-severity classification, moved from WargameLive.tsx's deriveLevel() to the backend. */
  level: "INFO" | "WARN" | "ERROR";
  /** AI 4역할 Slice 4 (Director) — LLM narration for a fresh incident start, rule-based sessions only. Null/absent otherwise (real-infra, LLM failure, or any other response) — callers should fall back to a static string. */
  narration?: string | null;
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
  coachStrengths: string[];
  coachGaps: string[];
  coachFollowupQuestions: string[];
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
  /** Drill Map 난이도 선행 추천 슬라이스 — 완료된 세션의 시나리오 난이도. */
  difficulty?: string | null;
}

export type TrendDirection = "IMPROVING" | "DECLINING" | "STABLE" | "INSUFFICIENT_DATA";

export interface SkillProfile {
  userId: string;
  weaknessesByDomain: Record<string, Record<string, number>>;
  weaknessesByCategory: Record<string, Record<string, number>>;
  trend: number[];
  trendDirection: TrendDirection;
  recommendedCategory: string | null;
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
  /** Integer mean of the evaluated phases — the session's one headline score, same number the summary sentence quotes. */
  averageScore: number | null;
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
  /** docs/CODECRAFTERS_BENCHMARK.md §3.3 — raw sandbox output for the test log (null before the stage ran, or for pre-V49 results). */
  output?: string | null;
  durationMs?: number | null;
}

export interface BuildStageInfo {
  stageOrder: number;
  title: string;
  /** One-line learning point. */
  spec: string | null;
  /** Goal · what the test checks · hint. Null for challenges without authored instructions — show `spec` instead. */
  instructions: string | null;
}

export interface BuildChallenge {
  slug: string;
  title: string;
  language: string;
  sourceFileName: string;
  stages: BuildStageInfo[];
  /** PLAN.md Round E2 — the stub /bridge starts from (same file as challenges/<slug>/). */
  starterCode: string | null;
}

export interface BuildChallengeSummary {
  slug: string;
  title: string;
  language: string;
  stageCount: number;
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
    // docs/COMMERCIALIZATION.md — a 401 mid-session means the token is no
    // longer valid (expired, or the user behind it is gone from the DB, see
    // UserExistenceCache). /auth/* is excluded: login/page.tsx already
    // treats its own 401 as "wrong email or password", not "session
    // expired" -- redirecting there would just bounce the login page off
    // itself.
    if (res.status === 401 && !path.startsWith("/auth/") && !isLoggingOut() && typeof window !== "undefined") {
      clearStoredUser();
      window.location.href = "/login?reason=expired";
    }
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
  preferredLanguage?: PreferredLanguage;
  trainingGoal?: TrainingGoal;
  termsAccepted: boolean;
}): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/auth/signup", { method: "POST", body: JSON.stringify(input) });
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
}

/** docs/COMMERCIALIZATION.md — revokes every token this user currently holds (server-side), not just this browser's copy. */
export function logout(): Promise<void> {
  return apiFetch<void>("/auth/logout", { method: "POST" });
}

export function requestPasswordReset(email: string): Promise<void> {
  return apiFetch<void>("/auth/password-reset/request", { method: "POST", body: JSON.stringify({ email }) });
}

export function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  return apiFetch<void>("/auth/password-reset/confirm", { method: "POST", body: JSON.stringify({ token, newPassword }) });
}

export function verifyEmail(token: string): Promise<void> {
  return apiFetch<void>(`/auth/verify-email?token=${encodeURIComponent(token)}`);
}

export function listScenarios(): Promise<ScenarioSummary[]> {
  return apiFetch<ScenarioSummary[]>("/scenarios");
}

/** Public, like listScenarios() — the Drill overview page works logged out. */
export function getScenario(scenarioId: string): Promise<ScenarioDetail> {
  return apiFetch<ScenarioDetail>(`/scenarios/${scenarioId}`);
}

/** PLAN.md step 31 — no userId param: GET /sessions lists the caller's own sessions, derived from their token. */
export function getUserSessions(): Promise<SessionSummary[]> {
  return apiFetch<SessionSummary[]>("/sessions");
}

/** PLAN.md step 31 — no userId param: GET /skill-profile is the caller's own profile, derived from their token. */
export function getSkillProfile(): Promise<SkillProfile> {
  return apiFetch<SkillProfile>("/skill-profile");
}

export interface PostmortemDomainSummary {
  domain: string;
  incidentCount: number;
  avgMttdSeconds: number | null;
  avgMttrSeconds: number | null;
}

/** Phase 3-C (docs/DRILLS_SIMULATION_VISION.md §6) — cross-session MTTD/MTTR aggregation, always recomputed at read time. */
export interface PostmortemSummary {
  totalIncidents: number;
  avgMttdSeconds: number | null;
  avgMttrSeconds: number | null;
  mttdTrend: TrendDirection;
  mttrTrend: TrendDirection;
  byDomain: PostmortemDomainSummary[];
}

/** No sessionId/userId param — the caller's own aggregation, derived from their token, same as getSkillProfile(). */
export function getPostmortemSummary(): Promise<PostmortemSummary> {
  return apiFetch<PostmortemSummary>("/postmortem-summary");
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
  /** PLAN.md Round E8 — mission inputs that are fixed at submit time (estimates, defense answers, …). */
  structured?: Record<string, unknown>,
): Promise<SubmissionResponse> {
  const structuredJson = structured && Object.keys(structured).length > 0 ? JSON.stringify(structured) : undefined;
  return apiFetch<SubmissionResponse>(`/sessions/${sessionId}/submissions`, {
    method: "POST",
    body: JSON.stringify({ rawText, clientRequestId, structuredJson }),
  });
}

/** docs/DRILLS_EXPANSION_PLAN.md M1 — the deliberately incomplete brief's clarifying questions. */
export interface ClarificationQuestion {
  id: string;
  question: string;
  asked: boolean;
  /** Only once asked (or after the INITIAL submit, for the report). */
  answer: string | null;
  /** Only after the INITIAL submit. */
  critical: boolean | null;
}

export interface Clarifications {
  available: boolean;
  canAsk: boolean;
  questions: ClarificationQuestion[];
  criticalAsked: number | null;
  criticalTotal: number | null;
}

export function getClarifications(sessionId: string): Promise<Clarifications> {
  return apiFetch<Clarifications>(`/sessions/${sessionId}/clarifications`);
}

export function askClarification(sessionId: string, questionId: string): Promise<Clarifications> {
  return apiFetch<Clarifications>(`/sessions/${sessionId}/clarifications/${questionId}`, { method: "POST" });
}

export function getFeedback(submissionId: string): Promise<EvaluationFeedback> {
  return apiFetch<EvaluationFeedback>(`/submissions/${submissionId}/feedback`);
}

export function advanceSession(sessionId: string): Promise<SessionResponse> {
  return apiFetch<SessionResponse>(`/sessions/${sessionId}/advance`, { method: "POST" });
}

/** ADR-0037 — `traits` carries the Architecture Canvas's node config (e.g. a DB node's pool size) as this session's starting DesignTraits; the backend fills in defaults for any field omitted. */
/** Phase 3-B (docs/DRILLS_SIMULATION_VISION.md §6) — the Traffic Lab's target RPS / load duration override for a real-infra coupon incident. Ignored for every other domain/mode. */
export interface LoadProfileOverride {
  targetRps?: number;
  loadDurationSeconds?: number;
}

export function startIncident(
  sessionId: string,
  realInfra = false,
  traits?: Record<string, number>,
  loadProfile?: LoadProfileOverride,
): Promise<SystemState> {
  const query = realInfra ? "?realInfra=true" : "";
  const hasTraits = traits && Object.keys(traits).length > 0;
  const hasLoadProfile = loadProfile && (loadProfile.targetRps !== undefined || loadProfile.loadDurationSeconds !== undefined);
  return apiFetch<SystemState>(`/sessions/${sessionId}/simulation/incident${query}`, {
    method: "POST",
    body: hasTraits || hasLoadProfile ? JSON.stringify({ traits, ...loadProfile }) : undefined,
  });
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

/** docs/OBSERVABILITY_UI_PLAN.md O1 — derived on the server from the series (ADR-0045). */
export type HealthStatus = "HEALTHY" | "DEGRADED" | "CRITICAL" | "RECOVERING" | "RECOVERED";

export interface SeriesPoint {
  t: string;
  state: SystemState;
  status: HealthStatus;
  /** Accumulated backlog — already in `state.queueLag` for notification/payment/reservation. */
  backlog: number;
}

export interface SimulationSeries {
  engineMode: "RULE_BASED" | "REAL_INFRA";
  incidentStartedAt: string | null;
  points: SeriesPoint[];
}

/** PLAN.md Round E4/E5 — a minute before the incident through now, ≤120 points, nothing stored server-side. */
export function getSimulationSeries(sessionId: string): Promise<SimulationSeries> {
  return apiFetch<SimulationSeries>(`/sessions/${sessionId}/simulation/series`);
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

export interface MentorHint {
  hints: string[];
}

/** AI 4역할 Slice 3 (Mentor) — on-demand hint for a draft that hasn't been submitted yet. */
export function getMentorHint(sessionId: string, rawText: string): Promise<MentorHint> {
  return apiFetch<MentorHint>(`/sessions/${sessionId}/mentor-hint`, {
    method: "POST",
    body: JSON.stringify({ rawText }),
  });
}

export interface SystemTopology {
  sessionId: string;
  saved: boolean;
  graph: string;
  updatedAt: string | null;
}

export function getSystemTopology(sessionId: string): Promise<SystemTopology> {
  return apiFetch<SystemTopology>(`/sessions/${sessionId}/topology`);
}

export function saveSystemTopology(sessionId: string, graph: string): Promise<SystemTopology> {
  return apiFetch<SystemTopology>(`/sessions/${sessionId}/topology`, {
    method: "PUT",
    body: JSON.stringify({ graph }),
  });
}

/** docs/LEARNING_COMMUNITY_PLAN.md §6.1 — 한 지표의 "나 vs 커뮤니티". */
export interface BenchmarkMetric {
  mine: number | null;
  /** 이 지표의 표본 수 — 전체 완료 세션 수(Benchmark.sampleSize)와 다를 수 있다. */
  sampleSize: number;
  /** 표본이 minSampleSize 미만이면 null — 분포를 감춘다. */
  distribution: { p50: number; p90: number } | null;
  /** "상위 N%". distribution 과 함께 null 이 된다. */
  topPercent: number | null;
  /** 점수는 높을수록, MTTD/MTTR 은 낮을수록 좋다. */
  higherIsBetter: boolean;
}

export interface Benchmark {
  scenarioVersionId: string;
  sampleSize: number;
  minSampleSize: number;
  score: BenchmarkMetric;
  mttdSeconds: BenchmarkMetric;
  mttrSeconds: BenchmarkMetric;
}

/** docs/LEARNING_COMMUNITY_PLAN.md §5.2 — 개념 라이브러리 (ADR-0039: 콘텐츠는 DB가 단일 출처). */
export interface LearningConceptSummary {
  riskKey: string;
  label: string;
  summary: string;
  /** 내가 이 개념을 지적받은 횟수. 0이면 배지 없음. */
  myWeaknessCount: number;
  /** docs/CODECRAFTERS_BENCHMARK.md §3.7 — lets the domain track page group concepts without fetching each detail. */
  relatedDomains?: string[];
  /** Server-estimated reading time of the full concept (minutes, ≥ 1). */
  readingMinutes?: number;
}

export interface LearningCategory {
  category: string;
  label: string;
  concepts: LearningConceptSummary[];
  myWeaknessCount: number;
}

export interface LearningConceptDetail {
  riskKey: string;
  category: string;
  categoryLabel: string;
  label: string;
  summary: string;
  whyItMatters: string;
  symptoms: string[];
  patterns: string[];
  tradeoffs: string;
  relatedDomains: string[];
  relatedActions: string[];
  relatedChallenges: string[];
  myWeaknessCount: number;
  readingMinutes?: number;
}

/** docs/LEARNING_COMMUNITY_PLAN.md §5.3 — 내 약점에서 파생한 학습 경로 (저장되지 않음). */
export type LearningStepStatus = "NOT_STARTED" | "IN_PROGRESS" | "ADDRESSED";

export interface LearningPathStep {
  riskKey: string;
  label: string;
  summary: string;
  status: LearningStepStatus;
  weaknessCount: number;
  /** 왜 이 상태인지 — 추천에 근거를 붙이는 것이 이 화면의 요점이다. */
  evidence: string;
  relatedDomains: string[];
  relatedChallenges: string[];
}

export interface LearningPath {
  recommendedCategory: string | null;
  categoryLabel: string | null;
  rationale: string;
  steps: LearningPathStep[];
}

export function getLearningPath(): Promise<LearningPath> {
  return apiFetch<LearningPath>("/learning/path");
}

export function getLearningConcepts(): Promise<LearningCategory[]> {
  return apiFetch<LearningCategory[]>("/learning/concepts");
}

/** docs/CODECRAFTERS_BENCHMARK.md §3.6 — a self-check built from concept data; graded on the page, nothing recorded. */
export interface ConceptQuiz {
  riskKey: string;
  question: string;
  options: { text: string; correct: boolean; fromRiskKey: string; fromLabel: string }[];
}

export function getConceptQuiz(riskKey: string): Promise<ConceptQuiz> {
  return apiFetch<ConceptQuiz>(`/learning/concepts/${riskKey}/quiz`);
}

export function getLearningConcept(riskKey: string): Promise<LearningConceptDetail> {
  return apiFetch<LearningConceptDetail>(`/learning/concepts/${riskKey}`);
}

export function getBenchmark(sessionId: string): Promise<Benchmark> {
  return apiFetch<Benchmark>(`/sessions/${sessionId}/benchmark`);
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

/** docs/CODECRAFTERS_BENCHMARK.md §3.9 (PLAN.md Round B16) — derived on the server from existing rows; only "last opened" is stored. */
export type NotificationType = "EVALUATION_READY" | "BUILD_GRADED" | "ORGANIZATION_INVITATION" | "DISCUSSION_MESSAGE";

export interface NotificationItem {
  type: NotificationType;
  title: string;
  body: string | null;
  href: string;
  at: string;
  unseen: boolean;
}

export interface NotificationFeed {
  unseenCount: number;
  items: NotificationItem[];
}

export function getNotifications(): Promise<NotificationFeed> {
  return apiFetch<NotificationFeed>("/me/notifications");
}

export function markNotificationsSeen(): Promise<void> {
  return apiFetch<void>("/me/notifications/seen", { method: "POST" });
}

/** docs/CODECRAFTERS_BENCHMARK.md §3.4 — optional onboarding answers; each drives exactly one UI default. */
export type PreferredLanguage = "PYTHON" | "TYPESCRIPT";
export type TrainingGoal = "INTERVIEW" | "SKILLS" | "TEAM";

export interface UserPreferences {
  preferredLanguage: PreferredLanguage | null;
  trainingGoal: TrainingGoal | null;
}

export function getMyPreferences(): Promise<UserPreferences> {
  return apiFetch<UserPreferences>("/me/preferences");
}

/** Replaces both fields — pass null to clear one. */
export function setMyPreferences(preferences: UserPreferences): Promise<UserPreferences> {
  return apiFetch<UserPreferences>("/me/preferences", { method: "PUT", body: JSON.stringify(preferences) });
}

/** docs/CODECRAFTERS_BENCHMARK.md §3.8 — completions only; ranking-hidden users and assessment sessions are excluded server-side. */
export interface RecentCompletion {
  nickname: string;
  completedAt: string;
}

export interface ActivityEntry {
  scenarioTitle: string;
  domain: string;
  completedAt: string;
}

export interface UserActivity {
  nickname: string;
  /** The user hid themselves from rankings — the empty timeline is intentional. */
  hidden: boolean;
  entries: ActivityEntry[];
}

export function getRecentCompletions(scenarioId: string): Promise<RecentCompletion[]> {
  return apiFetch<RecentCompletion[]>(`/community/scenarios/${scenarioId}/recent-completions`);
}

export function getUserActivity(userId: string): Promise<UserActivity> {
  return apiFetch<UserActivity>(`/community/users/${userId}/activity`);
}

/** The caller's newest submission to this challenge from any source (/bridge or submit.sh), or null if none yet. */
export async function getLatestBuildSubmission(slug: string): Promise<BuildSubmissionResponse | null> {
  try {
    return await apiFetch<BuildSubmissionResponse>(`/build-challenges/${slug}/submissions/latest`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

/** Stage roadmap + instructions, available before any submission exists. */
export function getBuildChallenge(slug: string): Promise<BuildChallenge> {
  return apiFetch<BuildChallenge>(`/build-challenges/${slug}`);
}

/** PLAN.md Round E2 — every Build challenge, for the /bridge picker. */
export function listBuildChallenges(): Promise<BuildChallengeSummary[]> {
  return apiFetch<BuildChallengeSummary[]>("/build-challenges");
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

export function getOrganizationDashboard(orgId: string): Promise<OrganizationDashboard> {
  return apiFetch<OrganizationDashboard>(`/organizations/${orgId}/dashboard`);
}

/** PLAN.md step 38 — organization admin-action audit log. */
export function listAuditLog(orgId: string): Promise<AuditLogEntry[]> {
  return apiFetch<AuditLogEntry[]>(`/organizations/${orgId}/audit-log`);
}

export interface CurriculumStep {
  scenarioId: string;
  title: string;
  domain: string;
  order: number;
  completed: boolean;
}

export interface Curriculum {
  steps: CurriculumStep[];
}

/** PLAN.md step 39 — the organization's single onboarding curriculum (advisory, not gated). */
export function getCurriculum(orgId: string): Promise<Curriculum> {
  return apiFetch<Curriculum>(`/organizations/${orgId}/curriculum`);
}

export function setCurriculum(orgId: string, scenarioIds: string[]): Promise<Curriculum> {
  return apiFetch<Curriculum>(`/organizations/${orgId}/curriculum`, {
    method: "PUT",
    body: JSON.stringify({ scenarioIds }),
  });
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

/** PLAN.md step 34 — org-scoped custom scenarios (docs/adr/0024). */
export function createCustomScenario(orgId: string, request: CreateCustomScenarioRequest): Promise<ScenarioDetail> {
  return apiFetch<ScenarioDetail>(`/organizations/${orgId}/scenarios`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function listOrganizationScenarios(orgId: string): Promise<ScenarioSummary[]> {
  return apiFetch<ScenarioSummary[]>(`/organizations/${orgId}/scenarios`);
}

/** Phase 5 — Scenario Marketplace (docs/adr/0031): any authenticated user, no organization needed. */
export function publishMarketplaceScenario(request: CreateCustomScenarioRequest): Promise<ScenarioDetail> {
  return apiFetch<ScenarioDetail>("/marketplace/scenarios", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function listMarketplaceScenarios(): Promise<ScenarioSummary[]> {
  return apiFetch<ScenarioSummary[]>("/marketplace/scenarios");
}

export function listMyMarketplaceScenarios(): Promise<ScenarioSummary[]> {
  return apiFetch<ScenarioSummary[]>("/marketplace/scenarios/mine");
}

export interface DomainCertificationStatus {
  domain: string;
  title: string;
  passed: boolean;
  bestScore: number | null;
}

export interface CertificationStatus {
  userId: string;
  nickname: string;
  certified: boolean;
  domains: DomainCertificationStatus[];
}

/** Phase 5 — "SysDrill Certified Incident Responder" (docs/adr/0032): live-computed, never issued or stored. */
export function getMyCertification(): Promise<CertificationStatus> {
  return apiFetch<CertificationStatus>("/certifications/me");
}

/** Public verification page — works whether or not the caller is logged in. */
export function getCertification(userId: string): Promise<CertificationStatus> {
  return apiFetch<CertificationStatus>(`/certifications/${userId}`);
}

/** PLAN.md step 36 — Game Day: active sessions on this org's custom scenarios, spectatable by any member. */
export function listGameDaySessions(orgId: string): Promise<GameDaySession[]> {
  return apiFetch<GameDaySession[]>(`/organizations/${orgId}/game-day-sessions`);
}

export function listChatMessages(sessionId: string): Promise<ChatMessage[]> {
  return apiFetch<ChatMessage[]>(`/sessions/${sessionId}/chat`);
}

export function postChatMessage(sessionId: string, body: string): Promise<ChatMessage> {
  return apiFetch<ChatMessage>(`/sessions/${sessionId}/chat`, {
    method: "POST",
    body: JSON.stringify({ body }),
  });
}

/** Phase 5 — 채용/역량 평가 상품화 (docs/adr/0033). */
export type AssessmentStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";

export interface Assessment {
  id: string;
  organizationId: string;
  scenarioId: string;
  scenarioTitle: string;
  candidateEmail: string;
  token: string;
  status: AssessmentStatus;
  resultSessionId: string | null;
  expiresAt: string;
  createdAt: string | null;
}

export interface AssessmentPreview {
  organizationName: string;
  scenarioTitle: string;
  scenarioDomain: string;
  candidateEmail: string;
  expired: boolean;
  alreadyStarted: boolean;
}

export function createAssessment(orgId: string, candidateEmail: string, scenarioId: string): Promise<Assessment> {
  return apiFetch<Assessment>(`/organizations/${orgId}/assessments`, {
    method: "POST",
    body: JSON.stringify({ candidateEmail, scenarioId }),
  });
}

export function listAssessments(orgId: string): Promise<Assessment[]> {
  return apiFetch<Assessment[]>(`/organizations/${orgId}/assessments`);
}

export function getAssessmentReport(orgId: string, assessmentId: string): Promise<Report> {
  return apiFetch<Report>(`/organizations/${orgId}/assessments/${assessmentId}/report`);
}

export function previewAssessment(token: string): Promise<AssessmentPreview> {
  return apiFetch<AssessmentPreview>(`/organizations/assessments/${token}`);
}

export function startAssessment(token: string): Promise<SessionResponse> {
  return apiFetch<SessionResponse>(`/organizations/assessments/${token}/start`, { method: "POST" });
}

/** Phase 6 — Architecture Linter v1 (docs/adr/0034). The raw spec is never persisted server-side. */
export interface ArchitectureAnalysis {
  scenario: ScenarioDetail;
  findings: string[];
  diagram: string;
}

export function analyzeArchitecture(openApiSpec: string): Promise<ArchitectureAnalysis> {
  return apiFetch<ArchitectureAnalysis>("/architecture-analysis", {
    method: "POST",
    body: JSON.stringify({ openApiSpec }),
  });
}

export function listMyArchitectureScenarios(): Promise<ScenarioSummary[]> {
  return apiFetch<ScenarioSummary[]>("/architecture-analysis/scenarios");
}

/** docs/COMMERCIALIZATION.md — PLATFORM_ADMIN-only; api.ts callers get a 403 ApiError for anyone else. */
export interface AdminDashboardStats {
  totalUsers: number;
  newUsersToday: number;
  totalOrganizations: number;
  sessionsCompletedToday: number;
}

/** docs/CODECRAFTERS_BENCHMARK.md §6 (PLAN.md Round B17) — aggregates only, PLATFORM_ADMIN-only. */
export interface SuccessMetrics {
  cohortSize: number;
  firstDrillWithin7DaysPercent: number | null;
  medianMinutesToFirstBuildPass: number | null;
  buildProgressDistribution: Record<string, number>;
  events30d: Record<string, number>;
  overviewToStartPercent: number | null;
}

export function getSuccessMetrics(): Promise<SuccessMetrics> {
  return apiFetch<SuccessMetrics>("/admin/dashboard/metrics");
}

export function getAdminDashboardStats(): Promise<AdminDashboardStats> {
  return apiFetch<AdminDashboardStats>("/admin/dashboard/stats");
}

/** ADR-0042 — Drill Score·티어·랭킹. 점수는 서버에서 매번 계산되며 저장되지 않는다. */
export interface DomainBest {
  domain: string;
  title: string;
  difficulty: string;
  bestScore: number;
  weight: number;
  points: number;
}

export interface MyRanking {
  score: number;
  tier: string;
  tierLabel: string;
  topPercent?: number | null;
  rank?: number | null;
  participantCount: number;
  pointsToNextTier?: number | null;
  nextTierLabel?: string | null;
  /** 점수의 계산 근거 — 화면은 이걸 반드시 함께 보여준다. */
  breakdown: DomainBest[];
  optedOut: boolean;
}

export interface RankingEntry {
  rank: number;
  nickname: string;
  score: number;
  tier: string;
  tierLabel: string;
  isMe: boolean;
}

export interface RankingBoard {
  board: "OVERALL" | "DOMAIN" | "RECENT";
  domain?: string | null;
  entries: RankingEntry[];
  participantCount: number;
}

export function getMyRanking(): Promise<MyRanking> {
  return apiFetch<MyRanking>("/community/rankings/me");
}

export function getRankingBoard(board: "overall" | "domain" | "recent", domain?: string): Promise<RankingBoard> {
  const query = new URLSearchParams({ board });
  if (domain) query.set("domain", domain);
  return apiFetch<RankingBoard>(`/community/rankings?${query.toString()}`);
}

export function setRankingVisibility(optOut: boolean): Promise<MyRanking> {
  return apiFetch<MyRanking>("/community/rankings/visibility", {
    method: "PUT",
    body: JSON.stringify({ optOut }),
  });
}

/** ADR-0041 — 풀이 공유. 기본은 비공개이고, 열람은 그 시나리오를 완료한 사람에게만 열린다. */
export interface SessionVisibility {
  sessionId: string;
  visibility: "PRIVATE" | "PUBLIC";
  anonymous: boolean;
  sharedAt?: string | null;
  scenarioId?: string | null;
  scenarioTitle?: string | null;
  completed: boolean;
}

export interface WriteupSummary {
  sessionId: string;
  /** 익명 공개면 null. */
  authorNickname?: string | null;
  anonymous: boolean;
  averageScore?: number | null;
  completedAt?: string | null;
  sharedAt?: string | null;
  mine: boolean;
}

export interface WriteupPhase {
  phase: string;
  answer?: string | null;
  score?: number | null;
  topRisks: string[];
}

export interface WriteupDetail {
  sessionId: string;
  scenarioId: string;
  scenarioTitle: string;
  domain: string;
  authorNickname?: string | null;
  anonymous: boolean;
  averageScore?: number | null;
  completedAt?: string | null;
  mine: boolean;
  phases: WriteupPhase[];
  rootCause?: string | null;
  preventionItems: string[];
  mttdSeconds?: number | null;
  mttrSeconds?: number | null;
}

/** locked 는 오류가 아니라 정상 상태다 — 미완료자에게 "먼저 직접 풀어보세요"를 띄운다. */
export interface WriteupList {
  scenarioId: string;
  scenarioTitle: string;
  locked: boolean;
  count: number;
  writeups: WriteupSummary[];
}

export function getSessionVisibility(sessionId: string): Promise<SessionVisibility> {
  return apiFetch<SessionVisibility>(`/sessions/${sessionId}/visibility`);
}

export function setSessionVisibility(
  sessionId: string,
  visibility: "PRIVATE" | "PUBLIC",
  anonymous = false,
): Promise<SessionVisibility> {
  return apiFetch<SessionVisibility>(`/sessions/${sessionId}/visibility`, {
    method: "PUT",
    body: JSON.stringify({ visibility, anonymous }),
  });
}

export function listWriteups(scenarioId: string): Promise<WriteupList> {
  return apiFetch<WriteupList>(`/scenarios/${scenarioId}/writeups`);
}

export function getWriteup(sessionId: string): Promise<WriteupDetail> {
  return apiFetch<WriteupDetail>(`/writeups/${sessionId}`);
}

/** ADR-0040 — 시나리오 버전 단위 토론 스레드. 전송은 폴링(ADR-0026 연장). */
export interface QuotedWriteup {
  sessionId: string;
  /** ADR-0041 — 이 시나리오를 완료하지 않았으면 인용이 잠긴다. */
  locked: boolean;
  authorNickname?: string | null;
}

export interface DiscussionMessage {
  id: string;
  authorUserId: string;
  authorNickname: string;
  body: string;
  createdAt?: string | null;
  mine: boolean;
  quoted?: QuotedWriteup | null;
  reportedByMe: boolean;
  /** PLAN.md Round E3 — set on a reply (one level only). */
  parentId?: string | null;
  kind: DiscussionKind;
  containsSpoiler: boolean;
  /** Spoiler post and the viewer hasn't completed the scenario — `body` is empty. */
  spoilerLocked: boolean;
}

export type DiscussionKind = "QUESTION" | "DESIGN" | "RESPONSE" | "INSIGHT";

export interface PreviousVersionThread {
  versionNo: number;
  messages: DiscussionMessage[];
}

export interface DiscussionThread {
  scenarioId: string;
  scenarioVersionId: string;
  scenarioTitle: string;
  /** 스레드가 비어 있을 때 화면이 초라해지지 않도록 함께 오는 집계 신호. */
  completedCount: number;
  averageScore?: number | null;
  completedByMe: boolean;
  messages: DiscussionMessage[];
  currentVersionNo: number;
  /** PLAN.md Round E3 — older versions' threads, read-only, newest first. */
  previousVersions: PreviousVersionThread[];
}

export function getDiscussion(scenarioId: string): Promise<DiscussionThread> {
  return apiFetch<DiscussionThread>(`/scenarios/${scenarioId}/discussion`);
}

export function postDiscussion(
  scenarioId: string,
  body: string,
  options: { quotedSessionId?: string; parentId?: string; kind?: DiscussionKind; containsSpoiler?: boolean } = {},
): Promise<DiscussionMessage> {
  return apiFetch<DiscussionMessage>(`/scenarios/${scenarioId}/discussion`, {
    method: "POST",
    body: JSON.stringify({ body, ...options }),
  });
}

/** PLAN.md Round E3 — PLATFORM_ADMIN moderation queue (reported posts, most reports first). */
export interface ReportedDiscussion {
  id: string;
  scenarioVersionId: string;
  scenarioId: string | null;
  scenarioTitle: string | null;
  authorNickname: string;
  body: string;
  reportCount: number;
  hidden: boolean;
  createdAt: string | null;
}

export function getReportedDiscussions(): Promise<ReportedDiscussion[]> {
  return apiFetch<ReportedDiscussion[]>("/admin/discussions/reported");
}

export function setDiscussionHidden(discussionId: string, hidden: boolean): Promise<ReportedDiscussion> {
  return apiFetch<ReportedDiscussion>(`/admin/discussions/${discussionId}/hidden`, {
    method: "PUT",
    body: JSON.stringify({ hidden }),
  });
}

export function reportDiscussion(discussionId: string, reason?: string): Promise<void> {
  return apiFetch<void>(`/discussions/${discussionId}/reports`, {
    method: "POST",
    body: JSON.stringify({ reason: reason ?? null }),
  });
}
