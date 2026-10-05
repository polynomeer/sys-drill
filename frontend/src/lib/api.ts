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
  | "ENABLE_ROLLOUT_SAFEGUARD"
  | "CONTINUE_ROLLOUT"
  | "PAUSE_ROLLOUT"
  | "ROLLBACK";

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
  /** PLAN.md Round E12 (O5) — incident start → first real alert; null with no rules or none fired. */
  firstAlertSeconds: number | null;
  alertRuleCount: number;
  falseAlarmCount: number;
  /** PLAN.md Round E13 (M5) — incident start → "복구 선언"; null until declared. */
  resolvedSeconds: number | null;
  recoveryStatus: "RECOVERED" | "PARTIAL" | "NOT_RECOVERED" | null;
  residualBacklog: number;
  integrity: IntegrityCheck[];
  /** PLAN.md Round E17 (O0-b) — what was looked at; seconds from the incident start (negative = before). */
  investigations: PostmortemInvestigation[];
}

export type InvestigationKind = "OPEN_PANEL" | "INSPECT_NODE" | "QUERY_LOGS" | "OPEN_TRACE";

export interface PostmortemInvestigation {
  kind: InvestigationKind;
  target: string | null;
  elapsedSeconds: number;
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

/** docs/DRILLS_EXPANSION_PLAN.md M2 / docs/LEARNING_EXPANSION_PLAN.md L6 — one order-of-magnitude judgement. */
export interface EstimateResult {
  key: string;
  estimate: number | null;
  truth: number;
  ratio: number | null;
  onTarget: boolean;
  direction: "ON_TARGET" | "UNDER" | "OVER" | "MISSING";
}

export interface EstimationField {
  key: string;
  label: string;
  unit: string;
  hint: string | null;
}

export interface Estimation {
  available: boolean;
  open: boolean;
  fields: EstimationField[];
  results: EstimateResult[] | null;
}

/** docs/DRILLS_EXPANSION_PLAN.md M4 — the INITIAL feedback's follow-up questions to answer with FOLLOWUP. */
export interface Defense {
  available: boolean;
  /** Interview-timer sessions must answer before submitting. */
  required: boolean;
  questions: string[];
}

export function getDefense(sessionId: string): Promise<Defense> {
  return apiFetch<Defense>(`/sessions/${sessionId}/defense`);
}

/** docs/DRILLS_EXPANSION_PLAN.md M7 (PLAN.md Round E20) — assumptions stated at INITIAL, broken by the FOLLOWUP. */
export interface Assumption {
  id: string;
  text: string;
}

export interface Assumptions {
  available: boolean;
  open: boolean;
  options: Assumption[];
  selected: string[];
  custom: string[];
  /** Null before FOLLOWUP; then the candidates the pinned variant broke. */
  broken: Assumption[] | null;
}

export function getAssumptions(sessionId: string): Promise<Assumptions> {
  return apiFetch<Assumptions>(`/sessions/${sessionId}/assumptions`);
}

/** M8 (PLAN.md Round E20) — estimated monthly cost and ops complexity of the saved canvas. */
export interface CostEstimate {
  available: boolean;
  drawn: boolean;
  monthlyCost: number;
  budgetPerMonth: number | null;
  budgetDeltaPct: number | null;
  lines: { key: string; label: string; units: number; unitCost: number; cost: number }[];
  complexity: number;
  teamCapacity: number | null;
  teamSize: number | null;
  opsExperience: Record<string, string>;
  actionCostDeltas: Record<string, number>;
}

export function getCostEstimate(sessionId: string): Promise<CostEstimate> {
  return apiFetch<CostEstimate>(`/sessions/${sessionId}/cost-estimate`);
}

export function getEstimation(sessionId: string): Promise<Estimation> {
  return apiFetch<Estimation>(`/sessions/${sessionId}/estimation`);
}

export interface LabSummary {
  slug: string;
  kind: "CAPACITY" | "ENGINE";
  title: string;
  summary: string;
  riskKey: string | null;
  domain: string | null;
}

export interface CapacityInput {
  key: string;
  label: string;
  value: number;
  unit: string;
}

export interface CapacityLab {
  slug: string;
  title: string;
  summary: string;
  variants: { label: string; inputs: CapacityInput[] }[];
  asks: { key: string; label: string; unit: string }[];
}

export interface CapacityCheckResult {
  variant: number;
  results: { key: string; label: string; unit: string; formula: string; result: EstimateResult }[];
}

export interface EngineKnob {
  trait: string;
  label: string;
  type: "number" | "boolean";
  min: number | null;
  max: number | null;
  step: number | null;
  default: number | boolean;
}

export interface EngineLab {
  slug: string;
  title: string;
  summary: string;
  riskKey: string | null;
  domain: string;
  knobs: EngineKnob[];
  watch: (keyof SystemState)[];
  predict: (keyof SystemState)[];
}

export function getEngineLab(slug: string): Promise<EngineLab> {
  return apiFetch<EngineLab>(`/learning/labs/${slug}/engine`);
}

/** ADR-0047 — the domain's own formula, no session; nothing is stored. */
export function runEngineLab(slug: string, traits: Record<string, number | boolean>, incidentActive: boolean): Promise<SystemState> {
  return apiFetch<SystemState>(`/learning/labs/${slug}/engine/run`, {
    method: "POST",
    body: JSON.stringify({ traits, incidentActive }),
  });
}

export function listLabs(): Promise<LabSummary[]> {
  return apiFetch<LabSummary[]>("/learning/labs");
}

export function getCapacityLab(slug: string): Promise<CapacityLab> {
  return apiFetch<CapacityLab>(`/learning/labs/${slug}/capacity`);
}

export function checkCapacityLab(slug: string, variant: number, answers: Record<string, number | null>): Promise<CapacityCheckResult> {
  return apiFetch<CapacityCheckResult>(`/learning/labs/${slug}/capacity/check`, {
    method: "POST",
    body: JSON.stringify({ variant, answers }),
  });
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
  /** PLAN.md Round E13 — the learner's "복구 선언"; the series ends two minutes after. */
  resolvedAt: string | null;
  points: SeriesPoint[];
  /** PLAN.md Round E12 (O5) — fired alerts over this window. */
  alerts: AlertEvent[];
  /** M3 — SLO against the latest point, with error budget. */
  slo: SloStatus | null;
}

export interface SloTargets {
  availabilityPct: number;
  p95Ms: number;
  errorRatePct: number;
}

export interface SloStatus {
  targets: SloTargets;
  availabilityMet: boolean;
  p95Met: boolean;
  errorRateMet: boolean;
  budgetSpentSeconds: number;
  monthlyBudgetSeconds: number;
  burnRate: number;
}

export interface AlertRule {
  id: string;
  metric: string;
  op: ">" | "<";
  threshold: number;
  forSeconds: number;
  severity: "WARN" | "CRITICAL";
  createdAt: string | null;
}

export interface AlertEvent {
  ruleId: string;
  metric: string;
  label: string;
  unit: string;
  op: string;
  threshold: number;
  severity: "WARN" | "CRITICAL";
  firedAt: string;
  resolvedAt: string | null;
  value: number;
  falseAlarm: boolean;
}

export interface OpsConfig {
  slo: SloTargets | null;
  sloDefaults: SloTargets;
  alertRules: AlertRule[];
  suggestedRules: AlertRule[];
  metrics: { key: string; label: string; unit: string }[];
}

/** docs/DRILLS_EXPANSION_PLAN.md M6 (ADR-0046) — a fork: Redis-only, 1h, never a session. */
export interface Fork {
  forkId: string;
  sourceSessionId: string;
  domain: string;
  atStep: number;
  forkedAtSeconds: number;
  prefixActions: string[];
  actions: string[];
  state: SystemState;
}

export interface ForkSide {
  actions: string[];
  recoveredAtSeconds: number | null;
  impactSeconds: number;
  finalErrorRate: number;
  finalBacklog: number;
}

export interface ForkComparison {
  horizonSeconds: number;
  original: ForkSide;
  fork: ForkSide;
}

export function createFork(sessionId: string, atStep: number): Promise<Fork> {
  return apiFetch<Fork>(`/sessions/${sessionId}/forks`, { method: "POST", body: JSON.stringify({ atStep }) });
}

export function getFork(forkId: string): Promise<Fork> {
  return apiFetch<Fork>(`/forks/${forkId}`);
}

export function getForkSeries(forkId: string): Promise<SimulationSeries> {
  return apiFetch<SimulationSeries>(`/forks/${forkId}/series`);
}

export function applyForkAction(forkId: string, actionType: SimulationActionType): Promise<Fork> {
  return apiFetch<Fork>(`/forks/${forkId}/actions`, { method: "POST", body: JSON.stringify({ actionType }) });
}

export function getForkComparison(forkId: string): Promise<ForkComparison> {
  return apiFetch<ForkComparison>(`/forks/${forkId}/comparison`);
}

/** docs/DRILLS_EXPANSION_PLAN.md M5 — mitigation vs recovery. */
export interface IntegrityCheck {
  key: string;
  label: string;
  ok: boolean;
  detail: string;
}

export interface RecoveryReport {
  started: boolean;
  resolved: boolean;
  resolvedAt: string | null;
  resolvedSeconds: number | null;
  status: "RECOVERED" | "PARTIAL" | "NOT_RECOVERED" | "NOT_STARTED";
  healthStatus: HealthStatus | null;
  symptomsOk: boolean;
  backlog: number;
  integrity: IntegrityCheck[];
}

/** Before the declaration: the "if declared now" checklist. After: the judged report. */
export function getRecovery(sessionId: string): Promise<RecoveryReport> {
  return apiFetch<RecoveryReport>(`/sessions/${sessionId}/simulation/recovery`);
}

export function resolveIncident(sessionId: string): Promise<RecoveryReport> {
  return apiFetch<RecoveryReport>(`/sessions/${sessionId}/simulation/resolve`, { method: "POST" });
}

/** docs/OBSERVABILITY_UI_PLAN.md O7 (PLAN.md Round E26) — the pre-incident readiness check. */
export interface Readiness {
  items: { key: string; label: string; checked: boolean; auto: boolean }[];
  confirmed: boolean;
  locked: boolean;
}

export function getReadiness(sessionId: string): Promise<Readiness> {
  return apiFetch<Readiness>(`/sessions/${sessionId}/readiness`);
}

export function confirmReadiness(sessionId: string, input: { structuredLogging: boolean; tracing: boolean }): Promise<Readiness> {
  return apiFetch<Readiness>(`/sessions/${sessionId}/readiness`, { method: "PUT", body: JSON.stringify(input) });
}

/** docs/DRILLS_EXPANSION_PLAN.md M12 (PLAN.md Round E27) — my runbook per domain, checked against the next incident. */
export type RunbookStepType = "OPEN_PANEL" | "INSPECT_NODE" | "QUERY_LOGS" | "OPEN_TRACE" | "ACTION" | "NOTE";

export interface RunbookStep {
  type: RunbookStepType;
  target: string | null;
  text: string;
}

export interface Runbook {
  domain: string;
  steps: RunbookStep[];
  updatedAt: string | null;
}

export interface RunbookCheck {
  available: boolean;
  domain: string | null;
  steps: { index: number; step: RunbookStep; done: boolean | null; atSeconds: number | null }[];
}

export function getRunbook(domain: string): Promise<Runbook> {
  return apiFetch<Runbook>(`/me/runbooks/${domain}`);
}

export function saveRunbook(domain: string, steps: RunbookStep[]): Promise<Runbook> {
  return apiFetch<Runbook>(`/me/runbooks/${domain}`, { method: "PUT", body: JSON.stringify(steps) });
}

export function getRunbookCheck(sessionId: string): Promise<RunbookCheck> {
  return apiFetch<RunbookCheck>(`/sessions/${sessionId}/runbook-check`);
}

/** docs/DRILLS_EXPANSION_PLAN.md M9 (PLAN.md Round E30) — the riskiest change in the release, picked before the incident. */
export interface ChangeReview {
  available: boolean;
  changes: { id: string; text: string }[];
  pick: string | null;
  locked: boolean;
  culpritId: string | null;
  explanation: string | null;
}

export function getChangeReview(sessionId: string): Promise<ChangeReview> {
  return apiFetch<ChangeReview>(`/sessions/${sessionId}/change-review`);
}

export function pickChangeReview(sessionId: string, pick: string): Promise<ChangeReview> {
  return apiFetch<ChangeReview>(`/sessions/${sessionId}/change-review`, { method: "PUT", body: JSON.stringify({ pick }) });
}

export function getOpsConfig(sessionId: string): Promise<OpsConfig> {
  return apiFetch<OpsConfig>(`/sessions/${sessionId}/ops`);
}

export function updateSlo(sessionId: string, slo: SloTargets): Promise<OpsConfig> {
  return apiFetch<OpsConfig>(`/sessions/${sessionId}/ops/slo`, { method: "PUT", body: JSON.stringify(slo) });
}

/** Full replace; pass an existing rule's id to keep its createdAt (a rule only fires from when it existed). */
export function updateAlertRules(
  sessionId: string,
  rules: { id?: string; metric: string; op: string; threshold: number; forSeconds: number; severity: string }[],
): Promise<OpsConfig> {
  return apiFetch<OpsConfig>(`/sessions/${sessionId}/ops/alert-rules`, { method: "PUT", body: JSON.stringify(rules) });
}

/** PLAN.md Round E4/E5 — a minute before the incident through now, ≤120 points, nothing stored server-side. */
export function getSimulationSeries(sessionId: string): Promise<SimulationSeries> {
  return apiFetch<SimulationSeries>(`/sessions/${sessionId}/simulation/series`);
}

/** PLAN.md Round E17 (O4) — server logs generated from the same series the charts show. */
export interface SimulationLogLine {
  at: string;
  level: "INFO" | "WARN" | "ERROR";
  service: string;
  message: string;
  traceId: string | null;
}

export function getSimulationLogs(sessionId: string): Promise<SimulationLogLine[]> {
  return apiFetch<SimulationLogLine[]>(`/sessions/${sessionId}/simulation/logs`);
}

/** PLAN.md Round E25 (O6) — real Jaeger spans (real-infra coupon) or a waterfall decomposed from the rule engine. */
export interface TraceSpan {
  spanId: string;
  parentSpanId: string | null;
  name: string;
  service: string;
  startMs: number;
  durationMs: number;
  error: boolean;
}

export interface TraceList {
  source: "JAEGER" | "SYNTHETIC";
  available: boolean;
  traces: { traceId: string; at: string; rootName: string; service: string; durationMs: number; error: boolean }[];
  note: string | null;
}

export interface TraceView {
  traceId: string;
  source: "JAEGER" | "SYNTHETIC";
  at: string;
  durationMs: number;
  spans: TraceSpan[];
}

export function getTraces(sessionId: string): Promise<TraceList> {
  return apiFetch<TraceList>(`/sessions/${sessionId}/simulation/traces`);
}

export function getTrace(sessionId: string, traceId: string): Promise<TraceView> {
  return apiFetch<TraceView>(`/sessions/${sessionId}/simulation/traces/${traceId}`);
}

/** O0-b — fire-and-forget; the server debounces repeats of the same look within 30s. */
export function recordInvestigation(sessionId: string, kind: InvestigationKind, target?: string): Promise<void> {
  return apiFetch<void>(`/sessions/${sessionId}/simulation/investigations`, {
    method: "POST",
    body: JSON.stringify({ kind, target }),
  });
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
  /** PLAN.md Round E18 (L7) — derived from Drill results only. */
  mastery?: MasteryLevel;
}

/** PLAN.md Round E18 (L7) — 미시작 / 약점 / 연습함 / 신뢰. */
export type MasteryLevel = "NOT_STARTED" | "WEAK" | "PRACTICED" | "CONFIDENT";

export interface RelatedConceptLink {
  riskKey: string;
  label: string;
  /** PREREQUISITE: comes before this one. NEXT: this one unlocks it. RELATED: undirected. */
  relation: "PREREQUISITE" | "NEXT" | "RELATED";
}

export interface KnowledgeMap {
  nodes: { riskKey: string; label: string; category: string; categoryLabel: string; mastery: MasteryLevel }[];
  edges: { source: string; target: string; relation: "PREREQUISITE" | "RELATED" }[];
}

export function getKnowledgeMap(): Promise<KnowledgeMap> {
  return apiFetch<KnowledgeMap>("/learning/map");
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
  mastery?: MasteryLevel;
  /** Different variants where this concept wasn't flagged (2+ = 신뢰). */
  cleanVariants?: number;
  relatedConcepts?: RelatedConceptLink[];
  /** Lab slugs (L5) for this concept. */
  labs?: string[];
  /** PLAN.md Round E19 (L8) — fixes that look right but aren't, and when this pattern is the wrong tool. */
  badFixes?: string[];
  whenNotToUse?: string;
  /** Incident domains whose failure pattern involves this concept. */
  failurePatterns?: string[];
  /** docs/LEARNING_DEEPENING_PLAN.md L12 — engine values already filled in by the backend. */
  blocks?: ContentBlock[];
}

/**
 * docs/LEARNING_DEEPENING_PLAN.md L12 — mirrors backend learning/ContentBlocks.kt. `numbers` and
 * `system` blocks carry no numbers of their own: `resolved` / `state` are the rule engine's output.
 */
export interface ComparePane {
  label: string;
  mermaid: string;
  body: string;
  alt: string;
}

export type ContentBlock =
  | { type: "text"; body: string }
  | { type: "diagram"; mermaid: string; caption: string; alt: string }
  | { type: "steps"; title: string; items: { title: string; body: string }[] }
  | { type: "callout"; tone: "tip" | "warning" | "tradeoff"; title: string; body: string }
  | { type: "compare"; before: ComparePane; after: ComparePane; caption: string }
  | {
      type: "numbers";
      title: string;
      domain: string;
      incident: boolean;
      changeLabel: string;
      metrics: (keyof SystemState)[];
      claims: { metric: keyof SystemState; direction: "UP" | "SAME" | "DOWN" }[];
      resolved: Partial<Record<keyof SystemState, { before: number; after: number }>> | null;
    }
  | { type: "system"; domain: string; incident: boolean; caption: string; state: SystemState | null };

/** PLAN.md Round E19 (L8) — 장애 패턴 사전, 인시던트 도메인과 1:1. */
export interface FailurePatternSummary {
  domain: string;
  name: string;
  summary: string;
  symptoms: string[];
}

export interface FailurePatternDetail extends FailurePatternSummary {
  typicalMetrics: string[];
  typicalLogs: string[];
  commonCauses: string[];
  badFixes: { fix: string; why: string }[];
  mitigations: string[];
  prevention: string[];
  relatedConcepts: RelatedConceptLink[];
  /** docs/LEARNING_DEEPENING_PLAN.md L12. */
  blocks?: ContentBlock[];
}

/** docs/LEARNING_EXPANSION_PLAN.md L9 (PLAN.md Round E28) — metrics only; open without login. */
export interface PuzzlePoint {
  second: number;
  trafficRps: number;
  p95LatencyMs: number;
  errorRatePct: number;
  dbReadLoadPct: number;
  dbWriteLoadPct: number;
  poolUsagePct: number;
  cacheHitPct: number;
  cacheLatencyMs: number;
  queueLag: number;
  externalLatencyMs: number;
}

export interface DiagnosticPuzzle {
  seed: number;
  points: PuzzlePoint[];
  patterns: { key: string; label: string }[];
  checks: { key: string; label: string }[];
}

export interface PuzzleResult {
  patternCorrect: boolean | null;
  checkCorrect: boolean | null;
  answerPattern: string;
  answerPatternName: string;
  acceptedChecks: string[];
  explanation: string;
}

export function getPuzzle(seed?: number): Promise<DiagnosticPuzzle> {
  return apiFetch<DiagnosticPuzzle>(`/learning/puzzles${seed ? `?seed=${seed}` : ""}`);
}

export function answerPuzzle(seed: number, answer: { pattern?: string; check?: string }): Promise<PuzzleResult> {
  return apiFetch<PuzzleResult>(`/learning/puzzles/${seed}/answer`, { method: "POST", body: JSON.stringify(answer) });
}

/** docs/COMMUNITY_EXPANSION_PLAN.md C12 — the weekly puzzle; split and reasons only after answering. */
export interface WeeklyPuzzle {
  week: number;
  puzzle: DiagnosticPuzzle;
  myChoice: string | null;
  distribution: Record<string, number> | null;
  total: number | null;
  result: PuzzleResult | null;
  reasons: { nickname: string; choice: string; reason: string }[] | null;
}

export function getWeeklyPuzzle(): Promise<WeeklyPuzzle> {
  return apiFetch<WeeklyPuzzle>("/community/wwyd");
}

export function answerWeeklyPuzzle(input: { choice: string; reason?: string; reasonPublic: boolean }): Promise<WeeklyPuzzle> {
  return apiFetch<WeeklyPuzzle>("/community/wwyd", { method: "POST", body: JSON.stringify(input) });
}

export function listFailurePatterns(): Promise<FailurePatternSummary[]> {
  return apiFetch<FailurePatternSummary[]>("/learning/failures");
}

export function getFailurePattern(domain: string): Promise<FailurePatternDetail> {
  return apiFetch<FailurePatternDetail>(`/learning/failures/${domain}`);
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
  mastery?: MasteryLevel;
}

export interface LearningPath {
  recommendedCategory: string | null;
  categoryLabel: string | null;
  rationale: string;
  steps: LearningPathStep[];
}

/** docs/LEARNING_EXPANSION_PLAN.md L10 (PLAN.md Round E29) — read from my first incident actions, not my answers. */
export interface MisconceptionCard {
  key: string;
  belief: string;
  correction: string;
  evidence: string;
  labSlug: string | null;
  failureDomain: string | null;
}

export function getMisconceptions(): Promise<MisconceptionCard[]> {
  return apiFetch<MisconceptionCard[]>("/learning/misconceptions");
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
export type PreferredLanguage = "PYTHON" | "TYPESCRIPT" | "JAVA" | "KOTLIN" | "GO";
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
  /** PLAN.md Round E21 (M10) — different tail-design variants passed; never part of DrillScore. */
  passedVariants?: number;
  totalVariants?: number;
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
  /** PLAN.md Round E15 (C8) — structural distance from my design (0 same … 1 nothing shared); null without canvases. */
  distance?: number | null;
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
  /** PLAN.md Round E15 (C8) — the author's note; the summary is generated. */
  note?: string | null;
  summary?: WriteupDesignSummary | null;
}

export interface WriteupDesignSummary {
  nodeKinds: Record<string, number>;
  changedTraits: { key: string; value: number; defaultValue: number }[];
  actions: string[];
  /** PLAN.md Round E16 (C9) — seconds into the incident per action. */
  actionSeconds: number[];
  /** Rule-based incidents only can be forked (ADR-0046). */
  forkable: boolean;
}

export interface CompareSide {
  sessionId: string;
  averageScore: number | null;
  mttrSeconds: number | null;
  nodeKinds: Record<string, number>;
  traits: Record<string, number>;
  actions: string[];
}

export interface WriteupComparison {
  mine: CompareSide | null;
  theirs: CompareSide;
  onlyMine: string[];
  onlyTheirs: string[];
  shared: string[];
  traitDiffs: { key: string; mine: number; theirs: number }[];
  largestDifference: string | null;
  distance: number | null;
}

export function getWriteupComparison(sessionId: string): Promise<WriteupComparison> {
  return apiFetch<WriteupComparison>(`/writeups/${sessionId}/compare`);
}

export function setWriteupNote(sessionId: string, note: string): Promise<WriteupDetail> {
  return apiFetch<WriteupDetail>(`/sessions/${sessionId}/writeup-note`, { method: "PUT", body: JSON.stringify({ note }) });
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

/** `sort`: different (default when you drew a canvas) / score / recent. */
export function listWriteups(scenarioId: string, sort?: "different" | "score" | "recent"): Promise<WriteupList> {
  return apiFetch<WriteupList>(`/scenarios/${scenarioId}/writeups${sort ? `?sort=${sort}` : ""}`);
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
  /** PLAN.md Round E32 (C14). */
  reactions?: ReactionSummary | null;
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

/** docs/COMMUNITY_EXPANSION_PLAN.md C10 (PLAN.md Round E22) — anchored reviews on a public writeup. */
export type ReviewKind = "QUESTION" | "RISK" | "SUGGESTION" | "ALTERNATIVE" | "TRADEOFF";
export type AnchorType = "NODE" | "TIMELINE" | "NONE";

export interface WriteupComment {
  id: string;
  anchorType: AnchorType;
  anchorRef: string | null;
  anchorLabel: string | null;
  kind: ReviewKind;
  body: string;
  authorNickname: string;
  mine: boolean;
  reportedByMe: boolean;
  createdAt: string | null;
  reactions?: ReactionSummary | null;
}

export interface WriteupComments {
  comments: WriteupComment[];
  anchors: { type: AnchorType; ref: string; label: string }[];
}

export function getWriteupComments(sessionId: string): Promise<WriteupComments> {
  return apiFetch<WriteupComments>(`/writeups/${sessionId}/comments`);
}

export function postWriteupComment(
  sessionId: string,
  input: { anchorType: AnchorType; anchorRef?: string; kind: ReviewKind; body: string },
): Promise<WriteupComments> {
  return apiFetch<WriteupComments>(`/writeups/${sessionId}/comments`, { method: "POST", body: JSON.stringify(input) });
}

export function reportWriteupComment(commentId: string, reason?: string): Promise<void> {
  return apiFetch<void>(`/writeup-comments/${commentId}/reports`, { method: "POST", body: JSON.stringify({ reason }) });
}

export interface ReportedWriteupComment {
  id: string;
  sessionId: string;
  scenarioId: string | null;
  authorNickname: string;
  anchorLabel: string | null;
  kind: ReviewKind;
  body: string;
  reportCount: number;
  hidden: boolean;
  createdAt: string | null;
}

export function getReportedWriteupComments(): Promise<ReportedWriteupComment[]> {
  return apiFetch<ReportedWriteupComment[]>("/admin/writeup-comments/reported");
}

export function setWriteupCommentHidden(commentId: string, hidden: boolean): Promise<ReportedWriteupComment> {
  return apiFetch<ReportedWriteupComment>(`/admin/writeup-comments/${commentId}/hidden`, { method: "PUT", body: JSON.stringify({ hidden }) });
}

/** docs/COMMUNITY_EXPANSION_PLAN.md C11 (PLAN.md Round E23) — the Community home feeds. */
export interface CommunityHome {
  myDrills: { scenarioId: string; title: string; domain: string; writeups: number; newWriteups: number; newDiscussions: number }[];
  activeDiscussions: {
    scenarioId: string;
    title: string;
    postsThisWeek: number;
    latest: { id: string; kind: string; excerpt: string | null; spoilerLocked: boolean }[];
  }[];
  notableWriteups: { sessionId: string; scenarioId: string; scenarioTitle: string; authorNickname: string | null; reviewCount: number; locked: boolean }[];
}

export function getCommunityHome(since?: string): Promise<CommunityHome> {
  return apiFetch<CommunityHome>(`/community/home${since ? `?since=${encodeURIComponent(since)}` : ""}`);
}

/** docs/COMMUNITY_EXPANSION_PLAN.md C13 (PLAN.md Round E31, ADR-0050) — time-boxed challenges on an official scenario. */
export interface ChallengeEventSummary {
  id: string;
  title: string;
  scenarioId: string;
  scenarioTitle: string;
  domain: string;
  startsAt: string;
  endsAt: string;
  phase: "UPCOMING" | "LIVE" | "ENDED";
  participants: number;
}

export interface ChallengeBoard {
  event: ChallengeEventSummary;
  entries: { rank: number; nickname: string; score: number | null; resolvedSeconds: number | null; mine: boolean; writeupSessionId: string | null }[];
  debriefOpen: boolean;
}

export function listChallengeEvents(): Promise<ChallengeEventSummary[]> {
  return apiFetch<ChallengeEventSummary[]>("/community/events");
}

export function getChallengeBoard(eventId: string): Promise<ChallengeBoard> {
  return apiFetch<ChallengeBoard>(`/community/events/${eventId}`);
}

export function createChallengeEvent(input: { title: string; scenarioId: string; startsAt: string; endsAt: string }): Promise<ChallengeEventSummary> {
  return apiFetch<ChallengeEventSummary>("/admin/events", { method: "POST", body: JSON.stringify(input) });
}

/** docs/COMMUNITY_EXPANSION_PLAN.md C14 (PLAN.md Round E32) — typed reactions; reputation is their per-domain sum. */
export type ReactionKind = "HELPFUL" | "INSIGHT" | "GOOD_TRADEOFF";

export interface ReactionSummary {
  counts: Partial<Record<ReactionKind, number>>;
  mine: ReactionKind[];
}

export function toggleReaction(targetType: "DISCUSSION" | "WRITEUP_COMMENT", targetId: string, kind: ReactionKind): Promise<ReactionSummary> {
  return apiFetch<ReactionSummary>("/community/reactions", { method: "POST", body: JSON.stringify({ targetType, targetId, kind }) });
}

export interface DomainReputation {
  domain: string;
  helpful: number;
  insight: number;
  goodTradeoff: number;
  total: number;
}

export function getReputation(userId: string): Promise<DomainReputation[]> {
  return apiFetch<DomainReputation[]>(`/community/users/${userId}/reputation`);
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
