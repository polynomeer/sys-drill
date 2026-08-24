# SysDrill 시스템 아키텍처 설계서

> 제품 요구사항은 [PRD.md](PRD.md) 참고. 이 문서는 `docs/archive/`의 아키텍처 관련 문서(시스템 설계 기준 문서, 워게임 아키텍처 설계서, 통합 기획서)를 종합한 구현 기준입니다.

## 1. 설계 원칙

1. **모듈러 모놀리스로 시작**한다. 제품 도메인과 학습 UX가 빠르게 바뀔 수 있으므로 서비스 경계보다 실험 속도를 우선한다. 보안·리소스 격리가 필요한 Build Runner와 지연이 큰 AI 평가만 별도 워커로 분리한다.
2. **평가는 Rule + AI 하이브리드**로 구성한다. 결정 가능한 사실(요구사항 누락, 임계값 위반, 상태 일관성)은 규칙 엔진이 판정하고, AI는 트레이드오프 타당성·설명 품질·꼬리질문 생성을 담당한다.
3. **시뮬레이션은 상태 머신**으로 동작한다. 사용자 액션과 시나리오 이벤트가 다음 상태(SimulationState)를 결정하며, 문제 중심이 아니라 `Scenario → Session → SimulationState` 중심으로 모델링한다.
4. **모든 의사결정은 기록**한다 (Decision Log / applied_actions). 점수, 리플레이, 포스트모템, 장기 약점 프로필에 활용한다.
5. **AI 평가·코드 채점·리포트 생성 등 장시간 작업은 비동기 처리**한다. 제출(Submission)과 평가(Evaluation)를 분리해 API 응답 지연과 LLM 실패가 사용자 요청 스레드에 직접 결합되지 않게 한다.
6. **버전 메타데이터를 강제 저장**한다. Scenario, Rubric, PromptTemplate은 모두 버전을 가지며, 평가 결과는 재현 가능해야 한다.
7. **100% digital twin을 약속하지 않는다.** 실제 인프라를 매 세션 띄우지 않고, 교육 목적의 인과관계와 트레이드오프를 명확히 보여주는 추상 시뮬레이션으로 시작한다.

## 2. 논리 아키텍처

```
[ Web / Admin ]
       |
       v
[ API Gateway / BFF ]
       |
       v
+-----------------------------------------------+
|                Core Backend (Modular Monolith) |
|  identity | content | scenario | session       |
|  submission | evaluation | simulation | report |
+-----------------------------------------------+
       |                |                |
       v                v                v
 [ Job Queue ]   [ AI Evaluation   ]  [ Build Runner
   (Redis)         Worker / LLM   ]     Sandbox Worker ]
       |                |                |
       +--------+-------+--------+-------+
                |                |
                v                v
   +------------------------------------------+
   | PostgreSQL | Redis | Object Storage       |
   +------------------------------------------+
```

향후 사용자 수와 팀 기능이 커지면 Session Runtime, Build Runner, AI Evaluation, Billing을 독립 서비스로 분리할 수 있으나, 초기에는 도메인 경계만 명확히 유지하고 배포 단위는 최소화한다.

## 3. 기술 스택

| 영역 | 선택 | 비고 |
|---|---|---|
| Frontend | Next.js, React, Tailwind CSS | 설계 워크스페이스는 React Flow, 코드 입력은 Monaco Editor 검토 |
| Backend | Kotlin + Spring Boot | 모듈러 모놀리스, 도메인 상태·비동기 작업·운영 안정성에 적합 |
| Primary DB | PostgreSQL | 사용자, 시나리오, 세션, 평가, 리포트 등 정합성이 필요한 영속 데이터. JPA/QueryDSL 또는 jOOQ |
| Cache/Queue | Redis | 세션 캐시, rate limit, Job Queue(MVP). 확장 시 SQS 또는 Kafka 검토 |
| Object Storage | S3 호환 | 다이어그램, 코드 아카이브, 대형 평가 원본, PDF 리포트 |
| Vector Store | pgvector (선택) | 리스크/사례 RAG, 유사 시나리오 검색 — MVP 이후 |
| AI | LLM Provider API + structured output | PromptTemplate 버전 관리, JSON Schema 검증 |
| Build Runner | Docker 기반 격리 워커 | 보안 요구 증가 시 gVisor/Firecracker 검토 |
| Observability | OpenTelemetry + Prometheus/Grafana | 로그는 Loki 또는 OpenSearch 계열 |
| Deployment | Container + PaaS/ECS/Fargate | 초기 Kubernetes는 필수 아님 |

**MVP에서는 Kafka를 도입하지 않는다.** Redis Queue 또는 관리형 큐(SQS)로 시작하고, 이벤트를 여러 독립 소비자가 사용하거나 장기 이벤트 보존이 실제 제품 요구가 될 때 Kafka를 검토한다. (Wargame 시나리오 안에서 "Kafka"를 다루는 것은 시뮬레이션 대상이지, 백엔드 자체의 실제 인프라 요구사항이 아니다.)

## 4. 핵심 도메인 모델

세 핵심 Aggregate는 **Scenario, Session(SubmissionSession), SimulationState**다.
- **Scenario**: 무슨 사건이 어떤 조건에서 발생할 수 있는가를 정의 (버전 관리되는 상태 전이 정의).
- **Session**: 사용자가 무엇을 선택했는가를 기록.
- **SimulationState**: 그 선택의 결과 현재 시스템이 어떤 상태인가를 표현.

```
User 1---N Session N---1 Scenario 1---N ScenarioVersion
                |                        |
                |                        +--- N ScenarioStep
                +--- N SessionPhase
                +--- N Submission N---1 Evaluation
                |                        |--- N RubricScore
                |                        +--- N RiskFlag
                +--- N AppliedAction
                +--- 1 Report
                +--- 1 SkillProfile (User 소유)

ContentItem 1---N Scenario
ContentItem 1---0..1 BuildChallenge --- N BuildStage
User 1---N BuildSubmission N---1 BuildChallenge
```

### 4.1 주요 테이블

| 테이블 | 핵심 컬럼 | 역할 |
|---|---|---|
| users | id, email, experience_years, primary_stack | 사용자 기본 정보 |
| content_items | type, title, difficulty, version | 공통 콘텐츠 카탈로그 |
| scenarios | content_id, domain, base_requirements, scoring_profile | 설계/워게임 시나리오 루트 |
| scenario_versions | version_no, followup_rules, incident_rules | 재현 가능한 시나리오 버전 |
| scenario_steps | scenario_id, step_order, step_type, trigger_condition, content | 초기 문제·꼬리질문·장애 업데이트 단계 |
| sessions | user_id, scenario_version_id, status, current_phase, seed | 학습 세션 Aggregate Root |
| session_phases | session_id, phase_type, order, status | 세션 단계 히스토리 |
| submissions | session_id, phase, raw_text, structured_json, revision_no | 사용자 설계/대응 입력 |
| applied_actions | session_id, action_type, target, parameters, effect | 정규화된 사용자 조치 |
| evaluations | submission_id, rubric_version, score_dimensions, strengths, weaknesses, risk_points | AI+Rule 평가 결과 (1:N — 재평가 이력 보존) |
| evaluation_risk_flags | evaluation_id, risk_key, severity, description | 실무 리스크 목록 |
| reports | session_id, summary, timeline_feedback, improvement_guide | 세션 종합 리포트 (버전 관리) |
| skill_profiles | user_id, strengths, weaknesses, trend | 장기 개인화 프로필 |
| build_challenges | slug, repo_template, languages | Build 과제 루트 |
| build_stages | challenge_id, stage_order, spec, test_spec | 단계별 구현 과제 |
| build_submissions | user_id, challenge_id, commit_ref, status, score | 코드 제출 |
| build_stage_results | submission_id, stage_id, status, score, feedback | 단계별 테스트 결과 |
| prompt_templates | purpose, version, template_body, active | AI 프롬프트 버전 관리 |

**ERD 설계 원칙**: Session을 중심 Aggregate로 두고, Scenario 버전을 고정해 재현성을 확보한다. 조회·필터링에 자주 쓰는 타입/상태/점수/시간은 정규 컬럼으로, 자주 바뀌는 scenario rule·simulation effect·AI structured result는 JSONB로 저장한다.

## 5. 세션 상태 머신

| 상태 | 의미 | 주요 전이 |
|---|---|---|
| IN_PROGRESS | 사용자가 현재 Step을 풀고 있음 | SUBMIT → SUBMITTED |
| SUBMITTED | 답안 저장 완료, 평가 요청 준비 | ENQUEUE → EVALUATING |
| EVALUATING | AI Worker가 평가 중 | SUCCESS → FEEDBACK_READY / FAIL → EVALUATION_FAILED |
| FEEDBACK_READY | 피드백 확인, 다음 Step 진행 가능 | ADVANCE → IN_PROGRESS / COMPLETE → COMPLETED |
| EVALUATION_FAILED | 평가 실패, 재처리 필요 | RETRY → EVALUATING |
| COMPLETED | 세션과 최종 리포트 완료 | 종료 |
| ABANDONED | 사용자 중도 이탈 | 재개 정책에 따라 IN_PROGRESS 가능 |

전이 API는 낙관적 동시성 제어 또는 상태 조건부 UPDATE를 사용해 중복 Submit과 잘못된 Advance를 방지한다.
예: `UPDATE session SET status=? WHERE id=? AND status=?` 형태로 현재 상태를 조건에 포함.

## 6. Simulation Engine 설계

MVP는 실제 인프라를 띄우지 않는 **규칙 기반 상태 시뮬레이터**로 시작한다.

```kotlin
data class SystemState(
    val trafficRps: Double,
    val p95LatencyMs: Double,
    val errorRate: Double,
    val availability: Double,
    val dbReadLoad: Double,
    val dbWriteLoad: Double,
    val connectionPoolUsage: Double,
    val cacheHitRatio: Double,
    val cacheLatencyMs: Double,
    val queueLag: Long,
    val consumerThroughput: Double,
    val externalDependencyLatencyMs: Double,
)

data class DesignTraits(
    val hasIdempotency: Boolean,
    val rateLimitPolicy: RateLimitPolicy?,
    val cacheStrategy: CacheStrategy?,
    val asyncBoundaries: List<AsyncBoundary>,
    val retryPolicy: RetryPolicy?,
    val circuitBreaker: CircuitBreakerConfig?,
    val observabilityLevel: ObservabilityLevel,
    val degradationPolicy: DegradationPolicy?,
)

// NextState = f(CurrentState, Incident, DesignTraits, AppliedAction, Time)
```

**병목 계산의 단순화 기준** (원본 기획서 §11.3):
```
0~60%:   안정
60~80%:  latency 증가
80~95%:  p95/p99 급등
95%+:    error 증가
100%+:   timeout/drop 발생
```
utilization = incoming_load / max_capacity. 이 방식은 실제 인프라를 완벽히 재현하지 않지만 교육적으로 유의미한 결과를 제공하는 것이 목적이다.

### 6.1 액션의 인과와 부작용 (예시)

| 액션 | 긍정 효과 | 가능한 부작용 |
|---|---|---|
| Rate Limit 강화 | DB/다운스트림 보호 | 일부 사용자 거절, UX 저하 |
| Cache TTL 증가 | DB 부하·latency 감소 | stale data 위험 |
| Consumer scale-out | queue lag 감소 | 외부 API/DB 병목 전이 |
| Retry 증가 | 일시 장애 복구율 증가 | retry storm, 중복 처리 |
| Feature degradation | 핵심 기능 보호 | 기능 축소, 경험 저하 |
| DB pool 증가 | 대기 요청 일부 감소 | DB 자체 한계 초과 가능 |

## 7. AI 평가 파이프라인

```
Submission
   ↓
Normalize (Markdown/섹션/길이 정규화)
   ↓
Context Assemble (Scenario, Rubric, 이전 Step, 현재 SimulationState, 참고 지식)
   ↓
Evaluation Pipeline
   ├─ Static/Rule Evaluator   (필수 요구사항, invariant, known risk trigger)
   ├─ Consistency / Risk       (hot key, retry storm 등 리스크 라이브러리 매칭)
   ├─ Cost / Capacity          (병목/비용 추정)
   ├─ LLM Critique             (트레이드오프 타당성, 과설계 판단)
   ├─ Follow-up Generation     (꼬리질문/조건 변경)
   └─ Score Aggregation
   ↓
Validation & Persistence (JSON Schema 검증, 점수 범위 검증, 메타데이터 저장)
   ↓
Feedback + Next Scenario Event
```

AI의 출력은 자유 텍스트만 저장하지 않고 `top_risks`, `missed_points`, `followup_questions`, `recommended_changes`, `rubric_adjustments` 같은 구조화 JSON으로 강제한다. 재평가·분석·프롬프트 변경 시 회귀 테스트가 가능해진다.

### 7.1 반드시 저장해야 할 AI 메타데이터
`model_provider`/`model_name`/`model_version`, `prompt_template_id`/`prompt_version`, `rubric_id`/`rubric_version`, `input_token`/`output_token`/`estimated_cost`, `latency_ms`/`retry_count`, `raw_response_ref`와 `parsed_result`, 평가 요청 idempotency key와 correlation id.

## 8. 비동기 처리와 신뢰성

답안 제출 API가 LLM 평가 완료까지 기다리지 않는다.

1. `POST /sessions/{id}/submissions` — Submission을 DB에 저장.
2. 같은 트랜잭션 또는 Outbox Pattern으로 `evaluation_requested` 이벤트 기록.
3. Publisher가 Queue에 Job 전달.
4. Worker는 `submission_id` 기반 idempotency 확인 후 LLM 호출·결과 검증.
5. Evaluation과 상태 변경을 원자적으로 저장.
6. 실패 시 재시도, 한도 초과 시 DLQ 또는 `EVALUATION_FAILED` 상태 전환.

**멱등성 원칙**: 사용자 Submit 요청은 `client_request_id`로 중복 제출 방지. 평가 Job은 `submission_id + evaluation_policy_version`을 고유 키로 사용. Worker는 최소 1회 전달(at-least-once)을 전제로 중복 소비에 안전해야 한다.

## 9. 데이터 저장소 전략

| 저장소 | 용도 |
|---|---|
| PostgreSQL | 사용자, 시나리오 버전, 세션, 제출, 평가, 리포트, 구독 — 정합성이 필요한 영속 데이터 |
| Redis | 세션 단기 캐시, rate limit, Job Queue, 임시 점수/Leaderboard |
| Object Storage | Diagram export, 코드 아카이브, 평가 리포트, 리플레이 산출물 |
| pgvector (선택, Phase 2+) | Reference Architecture, 리스크 사례 RAG |
| Telemetry Store | 제품 이벤트, 워커/러너 관측, 세션 분석 |

## 10. API 설계 초안

| 영역 | Method/Path | 설명 |
|---|---|---|
| Auth | POST /auth/signup, /auth/login | 가입/로그인 |
| User | GET /me | 프로필과 학습 상태 |
| Track | GET /tracks | 학습 트랙 목록 |
| Scenario | GET /scenarios, GET /scenarios/{id} | 목록/상세 및 공개 가능한 초기 조건 |
| Session | POST /sessions | 새 훈련 세션 시작 |
| Session | GET /sessions/{id} | 현재 Step, 상태, 진행률 |
| Submission | POST /sessions/{id}/submissions | 현재 Step 답안 제출 |
| Session | POST /sessions/{id}/advance | 피드백 이후 다음 Step 전이 |
| Session | POST /sessions/{id}/complete | 세션 종료 및 리포트 생성 요청 |
| Evaluation | GET /submissions/{id}/feedback | 답안별 평가 조회 |
| Report | GET /sessions/{id}/report | 세션 종합 리포트 |
| Recommendation | GET /recommendations/next-scenarios | 다음 추천 문제 |
| Admin | POST /admin/scenarios, /admin/rubrics, /admin/prompt-templates | 콘텐츠 관리 |
| Admin | POST /admin/scenarios/{id}/publish | 검증된 시나리오 배포 |

**실시간 UX**: 평가는 수초~수십초 소요될 수 있으므로 "제출 완료"와 "평가 완료"를 분리해 표시한다. MVP는 Polling으로 충분하며, 이후 SSE로 확장한다. WebSocket은 실시간 멀티플레이 워게임 이전에는 불필요하다.

## 11. 보안 및 실행 격리

- Build Mode에서만 사용자 코드를 실행하므로, 이 경로에만 강한 격리를 적용한다.
- Runner는 ephemeral container/VM으로 실행하고 CPU, memory, wall-clock timeout을 제한.
- 기본 outbound network 차단, privileged mode·host mount 금지.
- App Server와 Runner의 네트워크/자격증명/스토리지를 분리.
- 사용자 설계 답안은 기본 비공개, 팀 공유는 명시적 옵션.
- LLM 입력은 System Prompt/Rubric/Hidden Evaluation Rule과 명확히 분리해 Prompt Injection 영향을 제한.
- LLM Raw Output은 사용자에게 그대로 반환하지 않고 JSON Schema + 서버 측 후처리를 거친다.
- Admin API는 일반 사용자 API와 권한·감사 로그를 분리.
- Rate Limit은 사용자/조직 단위로 적용해 비용 공격 방지.

## 12. 관측 가능성

AI 평가가 핵심 비동기 경로이므로 API 지표만으로는 운영 상태를 알 수 없다. `submission → queue → worker → llm → evaluation persistence` 전체를 하나의 correlation id로 추적한다.

| 분류 | 필수 지표 |
|---|---|
| API | request count, p50/p95/p99 latency, 4xx/5xx rate |
| Session | started, completed, abandoned, step drop-off rate |
| Queue | queue depth, oldest message age, enqueue/dequeue rate |
| Evaluation | success/failure rate, retry rate, average/p95 evaluation time |
| LLM 비용 | input/output tokens, cost per scenario, cost per active user |
| 품질 | schema parse failure, invalid score, regeneration rate |

## 13. 스케일링 전략

- AI 평가 Worker가 API 서버보다 먼저 병목이 될 가능성이 크므로 Worker의 독립 확장성을 우선한다.
- Queue depth 또는 oldest message age 기준으로 Worker를 수평 확장.
- 모델 등급을 작업별로 분리 (핵심 평가는 고품질 모델, 태깅/추천/간단한 꼬리질문은 저비용 모델).
- 동일 제출물의 불필요한 재평가는 hash + evaluation policy version으로 방지.
- 평가 트래픽이 독립적으로 커지면 Evaluation Module과 Worker를 별도 서비스로 분리.

## 14. MVP 배포 구성

- Backend API 컨테이너 1개 이상 (모듈러 모놀리스: identity/content/scenario/session/submission/evaluation/simulation/reporting)
- AI Evaluation Worker 컨테이너 1개 이상
- Build Runner Sandbox Worker (격리된 별도 프로세스/컨테이너)
- PostgreSQL 1개, Redis 1개, S3 호환 Object Storage
- 외부 LLM Provider
- Prometheus/Grafana 또는 관리형 모니터링

로컬 개발은 `docker-compose`로 PostgreSQL + Redis를 구성한다 (세부 실행 순서는 [../PLAN.md](../PLAN.md) 0단계 참고).

관련 문서: [PRD.md](PRD.md) · [ROADMAP.md](ROADMAP.md) · [FUTURE_EXPLORATIONS.md](FUTURE_EXPLORATIONS.md)
