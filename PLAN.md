# SysDrill 작업계획서 (Claude Code 실행 순서)

> 제품 정의: [docs/PRD.md](docs/PRD.md) · 구현 기준: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · 단계별 로드맵: [docs/ROADMAP.md](docs/ROADMAP.md)

이 문서는 Claude Code가 SysDrill MVP([ROADMAP.md](docs/ROADMAP.md) Phase 1 — Core Loop)를 구현할 때 따를 순서를 정의합니다. **각 단계는 이전 단계가 동작하는 상태로 완료된 후 [CLAUDE.md](CLAUDE.md)의 커밋 규칙에 따라 커밋하고 다음 단계로 진행합니다.** 한 단계 안에서도 논리적으로 나뉘면 여러 커밋으로 분리합니다.

단계 순서를 바꾸거나 건너뛰어야 할 이유가 생기면(예: 기술적 제약 발견) 이 문서를 먼저 갱신한 뒤 진행합니다.

---

## 0단계 — 프로젝트 스캐폴딩 ✅ 완료 (2026-08-24)

- [x] 모노레포 구조 결정: `backend/`(Kotlin+Spring Boot), `frontend/`(Next.js)
- [x] `backend/`: Gradle 기반 Spring Boot 프로젝트 초기화 (Spring Boot 4.1.1, Kotlin 2.3.21, Java 21). 모듈 패키지 경계를 [ARCHITECTURE.md](docs/ARCHITECTURE.md) §2 기준으로 미리 만듦 (`identity`, `content`, `scenario`, `session`, `submission`, `evaluation`, `simulation`, `reporting` — 각 `.gitkeep`로 표시, 1단계부터 실제 코드로 채운다)
- [x] `frontend/`: Next.js 프로젝트 초기화 (TypeScript, Tailwind CSS, App Router)
- [x] 로컬 개발 인프라: `docker-compose.yml`로 PostgreSQL + Redis 기동
- [x] 루트 `.gitignore`에 Gradle(`build/`)과 Node(`node_modules/`, `.next/`) 항목 보강 (Gradle 세부 항목은 `backend/.gitignore`가 담당)
- [x] 두 프로젝트 모두 헬스체크 엔드포인트/페이지로 기동 확인 (브라우저로 직접 검증: `frontend`가 `backend`의 `/actuator/health`를 호출해 "up" 표시)

**완료 기준 충족**: `docker compose up -d`로 DB/Redis가 뜨고, `./gradlew bootRun`으로 backend가 DB/Redis에 연결되며, `npm run dev`로 뜬 frontend가 backend 헬스체크를 호출해 표시함을 확인함.

**진행 중 발견한 결정 사항** (다음 단계 작업자가 알아야 할 것):
- 로컬 환경에 이미 다른 프로젝트가 `5432`(Postgres), `8080`(API) 포트를 점유하고 있어 충돌을 피하려고 포트를 변경함: **Postgres → host `5433`**, **Redis → `6379`(그대로)**, **backend `server.port` → `8081`**. 모두 `backend/src/main/resources/application.yml`과 `docker-compose.yml`에서 환경변수로 오버라이드 가능 (`DB_PORT`, `SERVER_PORT` 등).
- `frontend/.env.local.example`의 `NEXT_PUBLIC_API_BASE_URL`도 `8081` 기준으로 맞춰뒀다. 로컬에서 작업 시 `cp frontend/.env.local.example frontend/.env.local` 필요.
- backend actuator에 `management.endpoints.web.cors.allowed-origins=http://localhost:3000`을 설정해 프론트엔드 dev 서버에서 CORS 없이 호출 가능하게 함.

## 1단계 — 도메인 스키마 및 마이그레이션 ✅ 완료 (2026-08-24)

- [x] Flyway 도입 (0단계에서 이미 의존성 추가됨)
- [x] [ARCHITECTURE.md](docs/ARCHITECTURE.md) §4.1 MVP 최소 집합 마이그레이션 작성 (`V1__create_core_schema.sql`): `users`, `content_items`, `scenarios`, `scenario_versions`, `scenario_steps`, `sessions`, `session_phases`, `submissions`, `evaluations`, `evaluation_risk_flags`, `reports`
- [x] JPA 엔티티/Spring Data 리포지토리 작성 (모듈 패키지별로 배치, `.gitkeep` 제거)

**완료 기준 충족**: `./gradlew test`로 11개 테이블 전체에 대해 저장→flush/clear→조회(JSONB 포함)→삭제 라운드트립 테스트 11건이 로컬 Postgres(5433)에서 통과함. `flyway_schema_history`에 `V1` 적용 확인.

**진행 중 발견한 결정 사항**:
- 엔티티 간 FK는 JPA `@ManyToOne` 연관관계 대신 **plain UUID 스칼라 필드**로 참조한다 (예: `Session.userId: UUID`). Aggregate 경계를 넘는 참조는 ID로만 연결한다는 [ARCHITECTURE.md](docs/ARCHITECTURE.md) §4 원칙과 일치하며, lazy-loading/N+1 문제를 초기에 피할 수 있다. 이후 단계에서도 이 패턴을 따른다.
- `scenario_steps`는 `ARCHITECTURE.md` §4.1 표에는 `scenario_id`로 적혀 있지만 §4 ERD 다이어그램(재현성을 위해 버전 고정)에 맞춰 실제로는 `scenario_version_id`로 구현함 — 두 표현이 문서 내에서 불일치했던 부분을 ERD 쪽으로 통일.
- Spring Boot 4에서 테스트 관련 클래스 패키지가 이동함: `@DataJpaTest`는 `org.springframework.boot.data.jpa.test.autoconfigure`, `AutoConfigureTestDatabase`는 `org.springframework.boot.jdbc.test.autoconfigure`, `TestEntityManager`는 `org.springframework.boot.jpa.test.autoconfigure`에 있다 (기존 `org.springframework.boot.test.autoconfigure.orm.jpa.*` 아님).
- JSONB 컬럼은 Hibernate의 `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"`로 매핑하고 Kotlin에서는 raw JSON 문자열(`String?`)로 다룬다. 타입 안전한 구조화 접근은 실제 기능을 구현하는 이후 단계에서 필요에 따라 도입한다.

## 2단계 — Scenario 콘텐츠 시딩 + 세션 상태 머신 ✅ 완료 (2026-08-24)

- [x] [ARCHITECTURE.md](docs/ARCHITECTURE.md) §5 세션 상태 머신 구현 (`IN_PROGRESS → SUBMITTED → EVALUATING → FEEDBACK_READY/EVALUATION_FAILED → COMPLETED`), 상태 조건부 UPDATE(`compareAndSetStatus`)로 동시성 제어
- [x] [PRD.md](docs/PRD.md) §8.1 "선착순 쿠폰" 시나리오 1개를 시딩 데이터로 등록 (`V2__seed_coupon_scenario.sql` — Scenario/ScenarioVersion/ScenarioStep 3단계: INITIAL/FOLLOWUP/INCIDENT)
- [x] API: `POST /sessions`, `GET /sessions/{id}`, `POST /sessions/{id}/submissions`(평가 큐잉 없이 저장만, `client_request_id` 멱등성 지원), `POST /sessions/{id}/advance`

**완료 기준 충족**: 시나리오 1개로 세션을 시작 → 텍스트 답안 제출(`IN_PROGRESS`→`SUBMITTED`) → (평가 워커가 아직 없으므로) `FEEDBACK_READY`를 리포지토리로 강제 설정 → `advance`로 다음 단계(`FOLLOWUP`→`INCIDENT`) 및 마지막 단계에서 `COMPLETED`까지 전이되는 것을 MockMvc API 테스트로 확인. 상태 머신 자체는 순수 단위 테스트로 전이표 전체를 별도 검증. 총 25개 테스트(기존 12개 + 신규 13개) 통과.

**진행 중 발견한 결정 사항**:
- `Session.status`를 Step 1의 `String`에서 `SessionStatus` enum(`@Enumerated(STRING)`)으로 강화했다. 상태 머신을 실제로 구현하는 시점이라 타입 안전성이 바로 가치를 내기 때문.
- 애그리게잇 경계를 넘는 FK는 여전히 plain UUID로 참조하되(1단계 원칙 유지), 서비스 계층(`SessionService`)이 `ScenarioRepository`/`ScenarioStepRepository`를 직접 조회해 다음 단계를 계산한다.
- `sessions.current_phase`는 표시용 캐시(현재 스텝의 `step_type` 문자열)이고, 실제 순서 판단의 근거는 `session_phases.phase_order`(최신 행)다.
- 동시성 제어는 JPQL `@Modifying(clearAutomatically = true)` 업데이트로 구현했다 — bulk update 이후 영속성 컨텍스트를 비워야 이어지는 조회가 stale 캐시가 아닌 최신 DB 값을 읽는다는 점이 Hibernate의 함정이었다.
- Admin API(`POST /admin/scenarios` 등)는 아직 만들지 않았다. 시나리오 등록은 당분간 Flyway 시딩 마이그레이션으로 대체하고, 실제 Admin CRUD는 필요해지는 시점(예: 콘텐츠 제작 도구)에 별도로 추가한다.
- 회원가입/로그인 API는 PLAN.md 어떤 단계에도 아직 명시적으로 배정되어 있지 않다 (PRD.md MVP 범위에는 포함). 테스트에서는 `UserRepository`로 사용자를 직접 생성해 우회했다 — 다음 단계 착수 전에 auth를 별도 단계로 추가할지 판단이 필요하다.

## 3단계 — 비동기 평가 파이프라인 골격 ✅ 완료 (2026-08-24)

- [x] Redis 기반 Job Queue 연동 (`EvaluationQueue`), Evaluation Worker를 별도 모듈(`EvaluationWorker`, 백그라운드 스레드 1개)로 분리 — 물리적으로 별도 프로세스로 배포하는 것은 이후 인프라 단계 과제
- [x] `POST /sessions/{id}/submissions` → Submission 저장 + 트랜잭션 커밋 후(`@TransactionalEventListener(AFTER_COMMIT)`) `EvaluationRequested` 이벤트로 SUBMITTED→EVALUATING 전이 및 큐 적재
- [x] Worker: `submission_id` 기반 idempotency 확인 → Rule Evaluator 스텁(`StubRuleEvaluator`) → Evaluation 저장 → 세션 상태를 `FEEDBACK_READY`로 전이
- [x] 실패 재시도(최대 3회, 설정 가능) + 한도 초과 시 dead-letter 리스트 + `EVALUATION_FAILED` 처리

**완료 기준 충족**: 제출 후 실제 백그라운드 Worker가 비동기로 평가(스텁 결과)를 만들어 저장하고, 세션 상태가 `SUBMITTED → EVALUATING → FEEDBACK_READY`로 전이되는 것을 `GET /sessions/{id}` Polling으로 확인하는 통합 테스트 3건(정상/재시도 실패/중복 전달) 포함 총 28개 테스트가 통과함.

**진행 중 겪은 Spring 7 / Boot 4 함정** (다음 단계에서 비슷한 패턴을 쓸 때 참고):
- `@Transactional`과 `@TransactionalEventListener`를 같은 메서드에 함께 쓸 수 없다 (`RestrictedTransactionalEventListenerFactory`가 기동 시점에 예외를 던짐).
- `AFTER_COMMIT` 콜백 안에서 기본(`REQUIRED`) 전파로 새 트랜잭션을 열면 `"No active transaction for update or delete query"`가 발생한다 — 직전 트랜잭션의 스레드 로컬 동기화 상태가 완전히 정리되기 전이기 때문. `PROPAGATION_REQUIRES_NEW`로 설정한 별도 `TransactionTemplate` 빈(`requiresNewTransactionTemplate`)으로 해결했다.
- `@Modifying(clearAutomatically = true)`는 영속성 컨텍스트를 **flush 없이** 비운다. 그 직전에 호출한 `save()`(아직 flush 안 된 상태)가 조용히 유실된다 — `EvaluationWorker`가 Evaluation을 저장할 때 `save()` 대신 `saveAndFlush()`를 쓰도록 수정해서 해결했다. 이후 단계에서도 "save 후 곧바로 `@Modifying` 쿼리 호출" 패턴을 쓸 때는 이 함정을 기억할 것.
- `submissions.client_request_id`가 전역 UNIQUE였던 것을 `(session_id, client_request_id)` 부분 유니크 인덱스로 좁혔다(`V3` 마이그레이션) — 서로 다른 세션이 같은 idempotency key 문자열을 우연히 재사용해도 충돌하지 않아야 하기 때문.
- **알려진 사소한 문제(미해결)**: 테스트 JVM 종료 시 `EvaluationWorker`의 백그라운드 스레드가 Redis 연결이 이미 종료된 뒤에도 몇 차례 `IllegalStateException`을 로그로 남긴다. 테스트 결과에는 영향 없음(3회 연속 전체 통과 확인). 원인은 Spring 컨텍스트 종료 시 빈 소멸 순서와 인터럽트 처리 타이밍 문제로 추정되며, 우선순위가 낮아 보류.

## 4단계 — Rule 기반 Simulation Engine v1

- [x] [ARCHITECTURE.md](docs/ARCHITECTURE.md) §6 `SystemState`/`DesignTraits` 모델 구현 (`DesignTraits`는 문서의 전체 필드 중 이번 단계 3개 액션이 실제로 쓰는 것만 구현 — 나머지는 필요해질 때 추가)
- [x] "선착순 쿠폰" 시나리오의 워게임 이벤트 템플릿 1개 구현 (트래픽 20배 + Redis latency 증가 → DB write hotspot), `SimulationEngine`에 순수 함수로 구현
- [x] 사용자 액션(`applied_actions`) 처리 및 §6.1 인과/부작용 규칙 최소 3개 구현 (Rate Limit 강화, Cache TTL 조정, DB pool 증가)
- [x] `utilization` 기반 병목 계산 로직 ([ARCHITECTURE.md](docs/ARCHITECTURE.md) §6의 0~60/60~80/80~95/95+/100+ 구간)

**완료 기준 충족**: `POST .../simulation/incident`로 시나리오 워게임을 시작하면 트래픽 20배+Redis 저하로 read/write 양쪽 축이 모두 악화되고(초기 errorRate 0.30, p95 640ms), `STRENGTHEN_RATE_LIMIT`→`INCREASE_CACHE_TTL`→`INCREASE_DB_POOL` 3개 액션을 순서대로 적용하면 각 액션이 서로 다른 축을 회복시키며 최종적으로 안정 상태(errorRate 0.001, p95 80ms)로 돌아오는 것을 API 통합 테스트로 확인. 계산식 자체는 손으로 미리 검증한 값과 대조하는 순수 단위 테스트로 별도 검증. 총 37개 테스트 통과.

**진행 중 발견한 결정 사항**:
- `SystemState`는 저장하지 않고 항상 **파생값**으로 계산한다. Redis에는 재계산에 필요한 최소 입력(`incidentActive`, `DesignTraits`)만 세션별로 저장한다 ([ARCHITECTURE.md](docs/ARCHITECTURE.md) §9가 "실시간 시뮬레이션 상태"를 Redis 역할로 명시한 것과 일치).
- `applied_actions`는 세션 하위 개념이지만 `simulation` 모듈이 소유한다 (평가/제출과 달리 시뮬레이션 액션·효과는 이 모듈의 핵심 책임이므로).
- 인시던트 하나(선착순 쿠폰)에 대해서만 동작하는 전용 상수/공식으로 구현했다. 여러 시나리오가 자체 파라미터로 인시던트를 정의하는 일반화된 엔진은 콘텐츠가 실제로 늘어나는 시점(로드맵 Phase 2)의 과제로 미룬다.
- 테스트에서 `MockMvcResultMatchers.jsonPath(path, Matchers.lessThan(0.6))` 조합이 Hamcrest 3.0에서 `Double`/`BigDecimal` 타입 불일치로 `ClassCastException`을 던졌다 — JsonPath로 값을 직접 읽어 AssertJ로 비교하는 방식으로 우회했다. 이후 단계에서 숫자 비교가 필요한 jsonPath 매처를 쓸 때 참고할 것.

## 5단계 — Rule Evaluator + AI 평가 연동

- [x] Rule Evaluator: 요구사항 누락, scenario-specific invariant 체크 구현 (`RuleEvaluator` — 멱등성/동시성/rate limit/observability 키워드 스캔, LLM 없이 판정)
- [x] PromptTemplate 저장/버전 관리 테이블 및 관리 API (`V5` 마이그레이션 + `POST/GET /admin/prompt-templates`, `POST /admin/prompt-templates/{id}/activate`)
- [x] LLM Provider 연동: Anthropic Claude, `RestClient` 기반 `AnthropicLlmClient`. Context Assemble(`HybridRuleAiEvaluator`) → LLM Critique → 구조화 JSON(`rubricScores`/`strengths`/`missedPoints`/`topRisks`/`followupQuestions`/`recommendedChanges`) 파싱·검증
- [x] [PRD.md](docs/PRD.md) §10 평가 루브릭(100점)을 `Rubric` 객체로 반영, 점수는 모델이 보고한 합계를 신뢰하지 않고 차원별로 재계산+클램프

**완료 기준 충족**: "선착순 쿠폰" 시나리오 제출 → Rule+AI 하이브리드 평가 결과(rubric 점수, 강점/약점, 규칙+LLM 리스크 플래그, 후속 질문, 권장 변경사항, 모델 메타데이터)가 저장되고 신규 `GET /submissions/{id}/feedback`으로 조회 가능함을 통합 테스트로 확인. 신규 25개 포함 총 54개 테스트 통과.

**진행 중 발견한 결정 사항**:
- LLM 자격증명은 `ANTHROPIC_API_KEY`/`ANTHROPIC_BASE_URL`이 아니라 **`LLM_ANTHROPIC_*`로 네임스페이스를 분리**했다. 이 셸에는 Claude Code 자신의 `ANTHROPIC_BASE_URL`이 이미 설정되어 있어, 같은 이름을 쓰면 백엔드가 의도치 않게 그 값을 물려받을 위험이 있었다. 사용자가 `backend/.env.local`에 `LLM_ANTHROPIC_API_KEY`를 넣으면 `./gradlew bootRun`(테스트는 제외)이 이를 읽어 환경변수로 주입한다 (`build.gradle.kts`의 `bootRun` 태스크 커스터마이징 — Spring Config 로더가 아니라 Gradle 레벨에서 처리해 확장자/로더 호환성 문제를 피했다).
- 키가 비어 있으면 `AnthropicLlmClient`가 예외를 던지는 대신 **오프라인 캔드 응답**을 반환한다. 실제 키를 넣기 전까지도 파이프라인 전체(Rule 엔진, 저장, 조회 API)가 정상 동작함을 보장하기 위함이며, 키를 넣는 순간 코드 변경 없이 실제 호출로 전환된다.
- Spring Boot 4의 자동구성된 `ObjectMapper`(Jackson 3, `tools.jackson.databind`)는 Kotlin 데이터 클래스의 기본값 파라미터를 JSON에 없는 필드에 대해 정상적으로 적용하지만, 테스트에서 직접 만든 `JsonMapper.builder().build()`는 Kotlin 모듈이 빠져 있어 같은 상황에서 실패했다 — `LlmEvaluationResultParserTest`를 Spring이 관리하는 실제 빈을 주입받도록 고쳐서 발견/수정했다.
- Step 3에서 겪은 `@Modifying(clearAutomatically = true)`가 flush 안 된 `save()`를 조용히 버리는 함정이 `evaluation_risk_flags`에도 동일하게 재현되어 `saveAllAndFlush`로 수정했다 — 이 패턴을 쓸 때마다 반복해서 주의해야 한다.
- AI 메타데이터는 ARCHITECTURE.md §7.1이 나열한 전체 목록(토큰 수, 비용, idempotency key 등) 대신 `model_provider`/`model_name`/`latency_ms`만 우선 저장했다. 비용/토큰 추적이 실제로 필요해지는 시점에 확장한다.

## 6단계 — 프론트엔드: System Design Workspace

- [x] 온보딩(연차/스택/목표) → Dashboard → 시나리오 목록 → Design Workspace 화면 구현
- [x] Design Workspace: 요구사항 표시, 구조화 섹션 입력, 자동저장, 제출 (구조화 섹션은 8개 필드 대신 가이드 체크리스트가 딸린 단일 textarea로 구현 — 아래 결정 사항 참고)
- [x] 제출 후 평가 대기 상태(Polling) UI

**완료 기준 충족**: 실제 브라우저로 온보딩 → 대시보드 → 시나리오 선택 → 설계 작성/제출 → Polling 대기 → 평가 결과 표시까지 전체 흐름을 직접 조작해 확인함. RuleEvaluator가 "동시성 제어" 키워드 누락을 정확히 잡아내는 것도 실제 데이터로 확인.

**진행 중 발견한 결정 사항 / 이번 단계에서 메운 공백**:
- 이 단계 전까지 없었던 백엔드 API 3종을 먼저 추가했다: `POST/GET /users`(온보딩용 경량 프로필 생성 — 비밀번호 없음, 실제 회원가입/로그인은 아님), `GET /scenarios`/`GET /scenarios/{id}`(시나리오 카탈로그), `SessionResponse.currentStepPrompt`(현재 스텝의 문제 텍스트). 일반 API용 전역 CORS 설정도 이번에 처음 추가했다 (`management.endpoints.web.cors`는 `/actuator/**`에만 적용되고 있었다).
- "구조화 섹션 입력"은 8개의 개별 입력 필드로 구현하지 않고, 8개 항목을 체크리스트로 보여주는 **단일 textarea**로 구현했다. 백엔드의 RuleEvaluator/HybridRuleAiEvaluator가 현재 `rawText` 하나만 소비하고 `structuredJson`은 저장만 할 뿐 아무것도 읽지 않기 때문에, 프론트에서 구조화 폼을 먼저 만드는 것은 아직 쓰이지 않는 데이터를 만드는 것이었다. 백엔드가 섹션별 데이터를 실제로 활용하게 되면 그때 폼을 분리한다.
- "자동저장"은 서버 draft 저장이 아니라 **localStorage에만 저장**된다. 백엔드에 IN_PROGRESS 상태를 유지한 채 답안을 저장하는 엔드포인트가 없고(제출 즉시 SUBMITTED로 전이), 이번 단계 범위에서 새로 만들지 않았다. 제출된 submissionId도 localStorage에 남겨서, 대기(Polling) 중 새로고침해도 이어서 결과를 볼 수 있게 했다.
- 사용자 식별은 여전히 "게스트 프로필"(닉네임만, 로그인 없음) 수준이다. 2단계 이후 계속 열려 있던 실제 회원가입/로그인 공백은 아직 남아 있다 — 여러 기기/브라우저 간 이어가기가 필요해지면 그때 반드시 채워야 한다.

## 7단계 — 프론트엔드: 꼬리설계 + Wargame Live 콘솔

- [x] 꼬리설계 카드 UI (조건 변경 표시 + 재설계 입력) — 기존 Design Workspace에 amber 배너 + phase-aware 안내로 구현 (별도 화면 없이 동일 워크스페이스 재사용)
- [x] Wargame Live: MetricsPanel, ActionPanel, 통합 이벤트/타임라인 로그 ([PRD.md](docs/PRD.md) §7.3 참고 — EventStreamPanel/TimelinePanel은 아래 결정 사항대로 하나로 병합)
- [x] 액션 제출 → Simulation Engine 결과 반영 → 실시간(Polling) 갱신

**완료 기준 충족**: 실제 브라우저로 전체 루프(INITIAL 설계 → 평가 → advance → FOLLOWUP 꼬리설계 → 평가 → advance → INCIDENT/Wargame Live → 인시던트 발생(p95 640ms/error 30%, 4단계 손계산과 정확히 일치) → 3개 액션 적용 → 완전 회복(p95 80ms/error 0.1%) → 회고 제출 → 평가 → advance → COMPLETED)을 직접 조작해 확인함.

**진행 중 발견한 결정 사항**:
- 세션 상태 머신(advance)이 이미 FOLLOWUP/INCIDENT 단계 전이를 처리하고 있었으므로, "꼬리설계 카드"는 별도 화면이 아니라 **기존 Design Workspace가 phase에 따라 다르게 렌더링**하는 방식으로 구현했다. FOLLOWUP이면 조건 변경 배너, INCIDENT면 WargameLive 패널이 추가로 표시된다.
- EventStreamPanel과 TimelinePanel을 **하나의 클라이언트 전용 로그**로 합쳤다. `applied_actions`는 백엔드에 저장되지만 조회 API가 없어서, 인시던트 시작과 액션 적용 이벤트를 프론트에서만 누적한다 (새로고침하면 사라짐). 실제 이력 조회가 필요해지면 `GET /sessions/{id}/applied-actions` 같은 엔드포인트를 추가해야 한다.
- INCIDENT 단계의 "제출"은 새 메커니즘을 만들지 않고 **기존 텍스트 회고 제출을 재사용**했다. 이미 PRD.md §10 루브릭에 "장애 대응 판단"(20점) 항목이 있어서 회고 텍스트를 그대로 Rule+AI 파이프라인에 태우는 것만으로 자연스럽게 맞아떨어졌다.
- 시뮬레이션 상태 조회가 404면(아직 인시던트를 시작 안 한 상태) 자동으로 `POST .../simulation/incident`를 호출해 시작하도록 처리했다 — 별도의 "인시던트 시작" 버튼 없이 워크스페이스 진입 시 자동으로 시작된다.

## 8단계 — 결과 리포트 + 대시보드 ✅ 완료 (2026-08-25)

- [x] Report 생성 (Synthesis 단계): 단계별 점수/리스크 타임라인, 평균 점수 총평, 중복 제거된 개선 가이드 (`ReportService`, 세션이 COMPLETED로 전이할 때 자동 생성)
- [x] SkillProfile 갱신 로직 (반복 약점 태깅): 평가마다 rule 기반 riskKey 발생 횟수를 누적하고 점수 추이(최근 10개)를 기록
- [x] Dashboard: 최근 진행, 약점 TOP 3, 점수 추이 (추천 다음 세션은 시나리오가 1개뿐이라 기존 시나리오 목록으로 대체 — 아래 결정 사항 참고)

**완료 기준 충족**: 실제 브라우저로 3단계(INITIAL/FOLLOWUP/INCIDENT)를 모두 완료(고의로 4개 규칙 개념을 모두 누락시킨 답안 사용) → 리포트 페이지에서 단계별 60/100 점수와 리스크 4개씩 확인 → 대시보드에서 "내 약점 TOP 3"(각 3회), "점수 추이"(3개 막대), "최근 진행"(완료 + 리포트 링크)이 정확히 반영되는 것을 확인.

**진행 중 발견한 결정 사항**:
- "추천 다음 세션"은 별도 추천 로직을 만들지 않았다 — 시나리오가 "선착순 쿠폰" 하나뿐이라 추천할 대상이 없어서, 기존 대시보드의 시나리오 목록(항상 표시됨)이 사실상 이 역할을 겸한다. 시나리오가 여러 개로 늘어나면(로드맵 Phase 2) 그때 실제 추천 로직을 추가한다.
- `EvaluationWorker`에서 `SkillProfileService.recordEvaluation` 호출 시 `save()`가 아니라 `saveAndFlush()`가 필요했다 — 이번에도 `compareAndSetStatus`의 `clearAutomatically=true`가 미플러시 저장을 조용히 버리는 동일한 함정이었다 (3, 5단계에 이어 세 번째 재현). 이 패턴을 쓰는 곳마다 flush 필요 여부를 반드시 확인할 것.
- 참고: 이번 단계 도중 Docker Desktop이 재시작되며 postgres/redis 컨테이너가 멈춰 있었다. `docker compose up -d`로 재기동했고, named volume 덕분에 데이터는 그대로 보존되었다 — 로컬 개발 시 이런 재시작이 있을 수 있다는 점 참고.

## 9단계 — Build Mode (Rate Limiter) ✅ 완료 (2026-08-25)

- [x] Build Runner: Docker 기반 격리 워커, CPU/메모리/timeout 제한, outbound network 차단 (`SandboxExecutor` — `docker run --rm --network none --cpus 0.5 --memory 128m --pids-limit 64`)
- [x] Rate Limiter 챌린지 stage 1~6 정의 (고정 윈도우 → 슬라이딩/토큰버킷 → 동시성 → 분산 스토어 → fail-open/closed → 메트릭; 원본 문서(`docs/archive`) stage 표 기반, "선착순 쿠폰" 시나리오로의 Bridge 연결점 포함)
- [x] 제출(Job Queue 적재) → Sandbox 실행/테스트 → 결과 저장 → 피드백 (`BuildJobPublisher`가 AFTER_COMMIT에 `BuildJobQueue`로 적재 → `BuildRunnerWorker`가 stage별 순차 실행 → `BuildStageResult` 저장 → `GET /build-submissions/{id}`로 조회)

**완료 기준 충족**: `challenges/rate-limiter/` 템플릿 repo(스텁 구현 + `submit.sh`)를 실제로 올바르게 구현해 `POST /build-challenges/rate-limiter/submissions`로 제출 → 6개 stage 전부 실제 `docker run` 샌드박스에서 PASSED, `score=6` 확인. 스텁(미구현) 제출은 6개 전부 FAILED, 구체적 실패 사유("not implemented") 포함 확인. 통합 테스트(`BuildControllerIntegrationTest`) + 단위 테스트(`SandboxExecutorTest`, 격리/타임아웃/net 차단 확인) 포함 총 67개 테스트 통과.

**진행 중 발견한 결정 사항**:
- 샌드박스 실행 언어로 **Python을 채택**했다 (Kotlin/JVM이 아니라) — 챌린지 자체가 언어 불문 설계 문제(Rate Limiter 알고리즘/동시성/장애 대응)이고, Python은 컨테이너 기동이 가볍고 표준 라이브러리만으로 `threading`/소켓 테스트가 가능해 stage 테스트 스크립트를 짧게 유지할 수 있었다. 챌린지가 늘어나면 언어별 이미지를 추가하는 구조(`sysdrill.build.sandbox-image`가 이미 설정값)로 확장한다.
- 5~8단계에서 반복 적용한 "config as data" 원칙을 그대로 따라 **stage별 테스트 스크립트 전체를 DB 컬럼(`build_stages.test_script`)에 저장**했다 (파일 경로가 아니라). 시딩은 `V9` 마이그레이션에서 `$...$` 달러 인용으로 임베드.
- 이번 단계는 **CLI/API만 구현하고 프론트엔드 UI는 만들지 않았다.** PRD.md §7.1 기준 Build Mode의 P0 검증 대상은 "샌드박스 채점 파이프라인이 실제로 동작하는가"이고, 사용자는 로컬 템플릿 repo(`challenges/rate-limiter/`)에서 `git`/`submit.sh`로 제출하는 방식으로 이미 완결된 루프를 수행할 수 있다. Bridge Mode(10단계)에서 Build→Design→Wargame을 한 화면 흐름으로 엮을 때 Build 제출 UI도 함께 만드는 것이 중복 작업을 피하는 길이라 그때로 미뤘다.
- **레이스 컨디션 버그를 발견/수정**했다: `BuildSubmissionService.submit()`이 `@Transactional` 메서드 안에서 `BuildJobQueue.enqueue()`를 직접 호출했는데, 이는 DB 커밋 이전에 Redis에 job이 올라가는 것이라 워커가 아직 안 보이는 트랜잭션의 row를 `findById`로 조회해 못 찾고 조용히 job을 버리는 경우가 있었다 (`BuildControllerIntegrationTest`가 간헐적으로 60초 타임아웃). 3/5/8단계에서 확립한 `EvaluationRequestPublisher`의 `@TransactionalEventListener(phase = AFTER_COMMIT)` 패턴을 그대로 적용해(`BuildSubmissionRequested` 이벤트 + `BuildJobPublisher`) 해결 — 커밋 이후에만 큐에 적재하도록 고쳤다. 격리 실행 3회 연속 통과로 재현 불가 확인.

## 10단계 — Bridge Mode 연결 ✅ 완료 (2026-08-25)

- [x] Build(Rate Limiter) 완료 → 선착순 쿠폰 Design → 꼬리설계 → Wargame으로 이어지는 단일 흐름 구현 (`/bridge` 페이지에서 Build 제출 → `POST /sessions`에 `buildSubmissionId` 전달 → 세션에 영구 링크)
- [x] Bridge 진행률 UI, 통합 평가(구현+설계+운영) 리포트 (`BridgeProgress` 컴포넌트, `ReportService`가 세션의 Build 제출을 조회해 리포트에 `buildSummary`로 포함)

**완료 기준 충족**: 실제 브라우저로 `/bridge`에서 Rate Limiter 스텁을 제출(6 stage 모두 FAILED, score 0/6) → 완료 즉시 "다음: 선착순 쿠폰로 이동" → Design(초기 설계) → 꼬리설계(조건 변경 배너) → Wargame Live(인시던트 발생 → Rate Limit 강화 액션 적용) → 회고 제출 → 세션 COMPLETED → 리포트 페이지에서 "Build — Build your own Rate Limiter: 0/6" 섹션과 3단계 타임라인이 하나의 리포트에 함께 표시되는 것을 확인. 백엔드 통합 테스트(`BridgeModeIntegrationTest` 3개 — 소유자 불일치 거부, 미완료 제출 거부, 전체 흐름) 포함 총 70개 테스트 통과.

**진행 중 발견한 결정 사항**:
- Bridge를 별도 도메인 개념으로 만들지 않고 **`sessions.build_submission_id`(nullable, `build_submissions` FK) 한 컬럼으로만 연결**했다 (`V10` 마이그레이션). `SessionService.startSession`이 이 값을 검증(제출자 일치, `COMPLETED` 상태)한 뒤 세션에 저장하고, `ReportService`가 세션 완료 시 이 링크를 따라가 `reports.build_summary`(jsonb)에 챌린지 제목/점수/stage 수를 스냅샷으로 남긴다 — 별도 Bridge 테이블이나 상태 머신을 만들지 않아도 기존 세션/리포트 파이프라인이 그대로 동작한다.
- 9단계에서 미뤄뒀던 **Build 제출 프론트엔드를 이번 단계에서 만들었다** (`/bridge` 페이지) — 코드를 붙여넣는 단일 textarea로, 로컬 CLI(`challenges/rate-limiter/submit.sh`) 제출과 동일한 API를 호출한다. 정답 구현을 요구하지 않는다: score와 무관하게 제출이 `COMPLETED`이기만 하면 Design으로 넘어갈 수 있다 — Bridge의 핵심은 "구현했다는 사실"과 "설계·운영에서 그 구현의 한계를 경험하는 것"의 연결이지, Build 단계에서 만점을 강제하는 게 아니기 때문이다.
- 연결 대상 시나리오는 하드코딩된 UUID 대신 **`GET /scenarios` 목록에서 `domain === "coupon"`으로 찾는다** (`findBridgeScenario`). 시나리오가 하나뿐인 현재는 사실상 고정이지만, 11단계에서 시나리오가 늘어나도 프론트 코드를 고치지 않고 동작한다.
- `BridgeProgress` 컴포넌트는 세션/리포트에 Build 링크가 있을 때만 표시된다 (`session.buildSubmissionId`/`report.buildSummary`가 null이면 렌더링 안 함) — Bridge를 거치지 않고 대시보드에서 바로 "선착순 쿠폰"을 시작하는 기존 경로는 그대로 영향 없이 동작한다.

## 11단계 — MVP 콘텐츠 완성 및 통합 테스트 ✅ 완료 (2026-08-25)

- [x] 나머지 2개 시나리오(알림 이벤트 처리, 대규모 상품 조회) 콘텐츠화 ([PRD.md](docs/PRD.md) §8.2, §8.3) — `V11`/`V12` 마이그레이션, 각각 고유한 `SimulationEngine` 인시던트 모델(consumer lag / cache 스탬피드)과 `RuleEvaluator` 평가 포인트 세트
- [x] Queue Build 과제 추가 — `challenges/queue/` 템플릿 repo, 4 stage(FIFO, ack/visibility timeout, 재시도+DLQ, 동시성), `V13` 마이그레이션
- [x] E2E 테스트: 시나리오 3종 각각 Design→꼬리설계→Wargame→Report 완주 (`MvpScenarioE2ETest` — notification/product-browsing 2종 신규, coupon은 8단계 `ReportAndSkillProfileIntegrationTest`가 이미 커버)
- [x] [PRD.md](docs/PRD.md) §11 "MVP에서 검증할 세 가지" 기준으로 셀프 점검 (아래)

**완료 기준 충족**: 3개 시나리오 모두 `POST /sessions`부터 `GET /sessions/{id}/report`까지 실제 HTTP 파이프라인으로 완주 확인(백엔드 테스트), Wargame 인시던트 수치를 손으로 미리 계산해 `SimulationEngineTest`로 대조. 실제 브라우저로 "알림 이벤트 처리" 시나리오의 Design(도메인별 가이드 문구) → 꼬리설계 → Wargame Live(도메인별 지표: Queue Lag/Consumer Throughput/Provider Latency, 도메인별 액션 3종)까지 확인 — 인시던트 수치(500rps, p95 2400ms, error 30%)가 손계산과 정확히 일치. Build Mode는 Rate Limiter(6 stage)에 이어 Queue(4 stage)도 올바른 구현은 전부 PASSED, 스텁은 전부 FAILED로 실제 샌드박스에서 검증. 신규 19개 포함 백엔드 총 89개 테스트 통과.

**진행 중 발견한 결정 사항**:
- 4단계에서 "여러 시나리오가 자체 파라미터로 인시던트를 정의하는 일반화된 엔진은 콘텐츠가 실제로 늘어나는 시점의 과제로 미룬다"고 적어뒀던 그 시점이 이번 단계였다. 완전히 데이터 기반인 범용 엔진 대신, **도메인 문자열로 분기하는 3개의 독립된 순수 함수**(`SimulationEngine.Coupon`/`Notification`/`ProductBrowsing`)로 확장했다 — 세 인시던트의 메커니즘(DB read/write hotspot, consumer lag, cache stampede)이 서로 다른 축을 모델링해야 해서 공유 가능한 부분은 이미 공유 중이던 utilization-band 함수(`latencyMultiplier`/`errorRateFor`)뿐이었고, 그 이상의 추상화는 오히려 각 도메인의 손계산 검증을 어렵게 만들었을 것이다.
- `DesignTraits`/`SimulationActionType`/`SimulationSessionStateCodec`을 세 도메인의 필드를 모두 갖도록 확장했다(합집합 방식) — 세션은 자신의 도메인에 해당하는 필드만 실제로 사용하지만, 도메인별로 별도 클래스를 만드는 것보다 Redis 코덱과 세션 상태 모델을 하나로 유지하는 편이 단순했다. `SimulationEngine.applyAction`은 세션의 도메인에 속하지 않는 액션을 명시적으로 거부한다(`IllegalStateException` → 409, `MvpScenarioE2ETest`로 확인).
- **`RuleEvaluator`와 `HybridRuleAiEvaluator`도 도메인 인지형으로 바꿨다** — 이전까지는 세션이 어떤 시나리오든 상관없이 쿠폰 시나리오의 4개 개념(멱등성/동시성/rate limit/관측)만 검사했다. `submission.sessionId`로 세션→시나리오 버전→시나리오 도메인을 조회해 `RuleEvaluator.evaluate(text, domain)`으로 올바른 개념 세트를 선택하도록 고쳤다. 실제 브라우저로 "알림 이벤트 처리" 답안을 제출해 4개의 알림 전용 리스크(idempotent consumer/retry-backoff/DLQ/circuit breaker)가 정확히 잡히는 것을 확인.
- Queue 챌린지는 6단계 Rate Limiter보다 적은 **4개 stage**로 스코프를 좁혔다 — PRD.md §7.1이 명시한 핵심 개념(ack/retry, visibility, at-least-once)이 FIFO/visibility-timeout·재전달/재시도+DLQ/동시성 4개로 충분히 커버되고, Rate Limiter 때 이미 "Build 채점 파이프라인이 실제로 동작하는가"라는 P0 가설은 검증이 끝났기 때문이다.
- `SessionResponse`에 `domain` 필드를 추가해 프론트가 시나리오별 UI(가이드 문구, Wargame 액션 버튼, 지표 패널)를 고를 수 있게 했다. `WargameLive`/`MetricsPanel`/Design 가이드 문구를 전부 `domain` 기반 룩업 테이블로 바꿨다 — 새 시나리오가 추가돼도 페이지 컴포넌트 자체는 다시 작성할 필요가 없다.
- **PRD.md §11 "MVP에서 검증할 세 가지" 셀프 점검**:
  1. MVP 범위(§11 포함 항목) 자체는 코드로 점검 가능하고, 전부 충족한다 — 시스템 설계 3종+꼬리설계, Build 2종(Rate Limiter/Queue), Wargame(3개 도메인 각각 고유 인시던트), Rule+AI 하이브리드 평가, 결과 리포트(Bridge 통합 포함), 약점 프로필/점수 추이. 다만 "회원가입/로그인"은 여전히 6단계에서 남겨둔 공백대로 **닉네임만 있는 게스트 프로필**이다 — 비밀번호 인증이 없다.
  2. §11의 진짜 "검증할 세 가지"(① 설계→조건변경→장애대응 흐름이 기존 학습보다 가치 있다고 느끼는가 ② AI 피드백이 "실무에서 실제로 터질 문제"를 짚어준다고 평가받는가 ③ 반복 학습 동기가 생기는가)는 **코드 자가점검으로 답할 수 있는 질문이 아니다** — 실제 사용자 반응이 필요한 가설이며, 이번 MVP 구현 완료로 "물어볼 수 있는 상태"가 됐을 뿐 아직 검증되지 않았다. 다음 단계는 실제 사용자 온보딩과 피드백 수집이다(로드맵 Phase 2 영역).

---

# Phase 2 — Personalization / 콘텐츠 확장

> [docs/ROADMAP.md](docs/ROADMAP.md) Phase 2 범위를 Claude Code가 실행할 단계로 분해한 것. Phase 1과 동일하게, 각 단계는 이전 단계가 동작하는 상태로 완료된 후 커밋하고 다음 단계로 진행한다.

**로드맵 원칙과의 상충에 대한 기록**: [ROADMAP.md](docs/ROADMAP.md)의 "로드맵 운영 원칙"은 "각 Phase는 이전 Phase의 핵심 검증 질문에 긍정적인 신호가 있어야 다음으로 진행한다"고 명시하지만, Phase 1의 검증 질문(11단계 셀프 점검 참고)은 아직 실사용자 신호가 없는 상태다. 그럼에도 사용자의 명시적 결정으로 Phase 2를 바로 진행한다(2026-08-25) — 이 프로젝트의 목적상 실사용자 확보보다 기능 구현 자체가 우선이라는 판단. 이후 실사용자 피드백이 이 방향과 상충하는 신호를 주면 Phase 2 범위를 재검토한다.

## 12단계 — 시나리오 seed 랜덤화 + adaptive 꼬리설계 ✅ 완료 (2026-08-25)

- [x] 시나리오별 FOLLOWUP(꼬리설계) variant를 여러 개 authored — 시나리오당 3개, 각각 다른 조건 변경 텍스트 + 특정 리스크 개념을 겨냥하는 태그(`targetRiskKey`, nullable) (`V14` 마이그레이션)
- [x] `sessions.seed`(기존에 있었지만 미사용이던 컬럼)를 세션 시작 시 랜덤 값으로 채우고, variant 선택에 사용 — 같은 seed면 같은 변형이 나오는 결정론적 랜덤성("통제된 랜덤성", PRD.md §7.3). `POST /sessions`가 `seed`를 선택적으로 받을 수 있게 해 테스트/재현이 가능하도록 함
- [x] Adaptive 선택 우선순위: 사용자 SkillProfile에서 이 시나리오 도메인과 관련된 최다 약점 riskKey가 있으면 그 riskKey를 겨냥한 variant를 우선 선택, 없으면 seed 기반으로 나머지 variant 중 결정론적 선택 (`SessionService.selectVariant`)
- [x] 이미 등록된 3개 시나리오(coupon/notification/product-browsing) 각각에 variant 데이터 추가

**완료 기준 충족**: `FollowupVariantIntegrationTest` — 같은 seed를 가진 서로 다른 두 신규 사용자(약점 신호 없음)가 같은 variant를 받는 것, 그리고 약점(`MISSING_RATE_LIMIT`)을 미리 기록해둔 사용자는 seed와 무관하게 그 약점을 겨냥한 variant를 받는 것을 확인. 실제 API로도 확인: 4개 개념을 모두 놓친 답안(전형적인 초심자 답안)은 매번 같은 variant로 수렴하고(동점 처리가 항상 먼저 등록된 variant를 고르므로 — 아래 결정 사항 참고), 4개 개념을 모두 언급한 "약점 없는" 답안은 seed에 따라 3개 variant가 골고루 나오는 것을 5개 seed로 직접 확인. 신규 3개 포함 백엔드 총 91개 테스트 통과.

**진행 중 발견한 결정 사항**:
- FOLLOWUP 스텝의 `scenario_steps.content`를 `{"prompt": ...}` 대신 `{"variants": [...]}`로 바꾸되, INITIAL/INCIDENT 스텝은 그대로 `{"prompt": ...}` 단일 형태를 유지한다. 새 테이블(`scenario_step_variants`) 대신 기존 컬럼의 JSON 모양만 확장했다 — `(scenario_version_id, step_order)` 유니크 제약과 기존 스텝 조회 로직을 그대로 쓸 수 있고, 6단계에서 확립한 "config as data" 원칙과도 맞는다. `SessionService.extractPrompt`가 두 모양을 모두 처리한다.
- **동점(tie) 처리를 하다 발견한 특성**: RuleEvaluator가 "그냥 API 서버 하나로 처리합니다" 같은 전형적인 초심자 답안에 대해 해당 도메인의 개념을 거의 전부(coupon 기준 4개 중 3개가 variant 대상) 동시에 flag하기 때문에, adaptive 선택에서 흔히 동점이 나고 `maxByOrNull`은 항상 먼저 나열된 variant를 고른다. 즉 "약점 신호가 아예 없는" 케이스는 생각보다 좁다(잘 쓴 답안이거나, 이미 그 도메인 밖의 약점만 있는 사용자) — seed 기반 다양성은 실제 API 호출로 4개 개념을 모두 언급한 답안으로만 직접 확인했다. 버그는 아니지만 다음 단계(SkillProfile 고도화)에서 동점 처리를 더 정교하게(예: 무작위 동점 해소) 만들지 고려할 만하다.

## 13단계 — SkillProfile 고도화 (장기 추적) ✅ 완료 (2026-08-25)

- [x] 약점 추이를 "최근 10개" 고정 윈도우가 아니라 장기 누적 + 최근 추세를 함께 보여주는 구조로 확장 (`TREND_HISTORY_LIMIT` 10→200, `SkillProfileController`가 최근 3개 vs 그 이전 3개 평균 차이로 `trendDirection`(IMPROVING/DECLINING/STABLE/INSUFFICIENT_DATA)을 계산)
- [x] 도메인별(coupon/notification/product-browsing) 약점 분리 — `RuleEvaluator.domainByRiskKey`(기존 concept 목록에서 역으로 파생, 새 데이터 없음)로 읽기 시점에 그룹화. 저장 형태(`SkillProfile.weaknesses`)는 그대로 flat map — riskKey 이름이 이미 도메인마다 겹치지 않으므로 스키마 변경 불필요
- [x] Dashboard: "다음 추천"을 최다 약점의 도메인과 연결된 시나리오로 계산해 목록 맨 앞으로 정렬 + "추천" 배지 표시 (8단계에서 시나리오가 1개뿐이라 미뤄뒀던 로직)

**완료 기준 충족**: `SkillProfileControllerIntegrationTest`(6개)로 도메인별 그룹화, IMPROVING/DECLINING/STABLE/INSUFFICIENT_DATA 4가지 추세, 200개 저장 확인. 실제 브라우저로 신규 사용자가 "선착순 쿠폰" 약한 답안 제출 → 대시보드에서 "내 약점 TOP 3"(Rate Limit/멱등성 처리/관측 가능성), "선착순 쿠폰"에 추천 배지가 붙어 목록 맨 위로 정렬, "점수 추이"에 데이터 부족 시 방향 화살표가 나타나지 않는 것까지 확인. 신규 6개 포함 백엔드 총 97개 테스트 통과.

**진행 중 발견한 결정 사항**:
- `trend`/`weaknesses`는 저장 로직(`SkillProfileService.recordEvaluation`)을 전혀 바꾸지 않고, 읽는 쪽(`SkillProfileController`)에서만 그룹화·방향 계산을 했다 — 쓰기 경로는 여전히 단순한 flat map/list이고, "도메인별", "최근 추세" 같은 해석은 API 응답을 만들 때만 계산되는 파생값이다(4단계 `SystemState`가 항상 파생값인 것과 같은 원칙).
- 이 개발 환경은 `LLM_ANTHROPIC_API_KEY`가 없어 오프라인 폴백이 항상 고정 점수(60점)를 반환한다. 그래서 IMPROVING/DECLINING 추세는 실제 브라우저로는 재현할 수 없었다 — `SkillProfileService.recordEvaluation`을 직접 호출하는 통합 테스트로만 검증했고, 브라우저 확인은 "데이터 부족 시 화살표가 안 뜬다"는 경계 케이스로 대체했다. 실제 키를 넣은 환경에서 다양한 점수가 쌓이면 이 부분도 자연스럽게 재현 가능하다.
- 다음 Build 과제 마이그레이션은 `V14`가 아니라 **`V15`부터** 시작한다 — `V14`는 이번 단계에서 FOLLOWUP variant 시딩에 이미 썼다(14단계 항목의 번호를 그에 맞게 수정).

## 14단계 — Build 과제 확장: Circuit Breaker ✅ 완료 (2026-08-25)

- [x] `challenges/circuit-breaker/` 템플릿 repo + stage 설계 (failure threshold, half-open, 복구 판단) — 4 stage: CLOSED pass-through, threshold 도달 시 OPEN(fail fast), recovery timeout 후 HALF_OPEN 복구, HALF_OPEN 시도 실패 시 재차단
- [x] `V15` 마이그레이션으로 챌린지/stage 시딩

**완료 기준 충족**: 9/11단계와 동일한 패턴 — 로컬에서 참조 구현/스텁을 실제 sandbox(`docker run`)로 먼저 검증한 뒤 마이그레이션에 반영. `CircuitBreakerControllerIntegrationTest`로 올바른 구현은 4 stage 전부 PASSED(`score=4`), 스텁은 전부 FAILED("not implemented" 피드백 포함) 확인, 격리 실행 2회 연속 통과. 신규 2개 포함 백엔드 총 99개 테스트 통과.

**진행 중 발견한 결정 사항**:
- HALF_OPEN 전이를 `call()` 시점이 아니라 **`state` 프로퍼티를 읽을 때도 계산**하도록 설계했다 — 실제로는 `call()` 내부에서만 상태를 확인해도 동작은 같지만(참조 구현의 `call()`이 시작할 때 `_maybe_transition_to_half_open()`을 다시 부르므로), stage 3 테스트가 `time.sleep()` 후 `cb.state`를 직접 읽어 HALF_OPEN 전이를 확인하기 때문에 `state`가 read-only 관찰이 아니라 지연 전이를 트리거하는 능동적 프로퍼티여야 했다. 이는 ADR-0011("파생값은 항상 읽는 시점에 계산")과 같은 원칙의 또 다른 사례 — OPEN이 HALF_OPEN으로 바뀌는 시점 자체가 "지금이 recovery_timeout을 넘겼는가"에서 파생되는 값이라 어딘가에 캐싱하면 타이머를 별도로 굴려야 했을 것이다.
- Rate Limiter(6 stage)·Queue(4 stage)에 이어 Circuit Breaker도 **4 stage**로 유지했다 — PLAN.md 체크리스트가 명시한 세 핵심 개념(failure threshold/half-open/복구 판단)이 4개 stage(정상 동작을 별도 baseline stage로 분리)로 자연스럽게 나뉘었다.

## 15단계 — Build 과제 확장: Distributed Lock ✅ 완료 (2026-08-25)

- [x] `challenges/distributed-lock/` 템플릿 repo + stage 설계 (mutual exclusion, lease/TTL, fencing token) — 4 stage: 상호 배제, lease 만료, fencing token(오래된 소유자 거부), 동시성
- [x] `V16` 마이그레이션으로 챌린지/stage 시딩

**완료 기준 충족**: 14단계와 동일한 패턴 — 로컬에서 참조 구현/스텁을 실제 sandbox로 먼저 검증한 뒤 마이그레이션에 반영. `DistributedLockControllerIntegrationTest`로 올바른 구현은 4 stage 전부 PASSED(`score=4`), 스텁은 전부 FAILED 확인, 격리 실행 2회 연속 통과. 신규 2개 포함 백엔드 총 101개 테스트 통과.

**진행 중 발견한 결정 사항**:
- Rate Limiter의 `InMemoryStore`/`FaultyStore` 패턴을 그대로 재사용해 `LockStore`를 별도 클래스로 분리했다 — 여러 `DistributedLock` 인스턴스가 같은 `LockStore`를 공유하면 "여러 프로세스가 같은 외부 락 서비스(Redis 등)를 바라보는" 상황을 실제 네트워크 호출 없이 시뮬레이션할 수 있다. 9단계에서 확립한 이 패턴이 매번 재사용 가능한 표준 모양이라는 것을 이번에 다시 확인했다.
- fencing token 검증(stage 3)이 이 챌린지의 핵심이자 가장 지도하기 어려운 부분이었다 — 단순히 "토큰이 증가한다"만 확인하는 게 아니라, **만료된 소유자가 뒤늦게 `release()`를 호출해도 현재 소유자의 락에 전혀 영향을 주지 않아야 한다**는 것까지 어서션했다(`owner_id`와 `token`이 모두 일치해야 release가 성공하도록). 분산 락에서 가장 흔한 실무 버그(락을 소유했다고 "생각하는" 죽은/멈춘 프로세스가 다른 소유자의 락을 실수로 해제하는 것)를 정확히 겨냥한다.

## 16단계 — Build 과제 확장: Retry/Backoff Middleware ✅ 완료 (2026-08-25)

- [x] `challenges/retry-backoff/` 템플릿 repo + stage 설계 (exponential backoff, jitter, retry budget) — 4 stage: 기본 재시도, 재시도 소진, exponential backoff+jitter, 공유 retry budget
- [x] `V17` 마이그레이션으로 챌린지/stage 시딩

**완료 기준 충족**: 14단계와 동일한 패턴 — 로컬에서 참조 구현/스텁을 실제 sandbox로 먼저 검증한 뒤 마이그레이션에 반영. jitter가 들어간 stage 3/4는 무작위성 때문에 로컬에서 10회 연속 재실행으로 별도 확인. `RetryBackoffControllerIntegrationTest`로 올바른 구현은 4 stage 전부 PASSED(`score=4`), 스텁은 전부 FAILED 확인, 격리 실행 3회 연속 통과. 신규 2개 포함 백엔드 총 103개 테스트 통과.

**진행 중 발견한 결정 사항**:
- `RetryPolicy`에 실제 `time.sleep` 대신 **주입 가능한 `sleep_fn`**을 받도록 설계했다(기본값은 `time.sleep`, 테스트는 기록만 하는 no-op을 넘긴다) — exponential backoff는 실수 초 단위로 빠르게 커지므로, 실제로 기다리는 방식으로는 stage 테스트가 샌드박스 10초 타임아웃 안에 끝나지 못했을 것이다. 대기 시간을 실제로 재는 대신 **기록해서 검증**하는 이 패턴은 Circuit Breaker(4단계)의 `time.sleep()` 기반 테스트보다 더 빠르고 안정적이었다 — 다음에 타이밍이 중요한 챌린지를 만들 때는 실제 sleep보다 이 방식을 먼저 고려할 것.
- jitter 검증은 "매번 값이 다르다"만 확인하지 않고 **각 지연이 `[0, base_delay * 2^attempt]`(capped) 범위 안에 있는지**까지 어서션했다 — 순수 무작위성만 확인하면 상한을 벗어나는 구현(예: 지터를 잘못 더해 최대 지연을 넘기는 버그)을 못 잡기 때문.
- retry budget(stage 4)은 **여러 `RetryPolicy` 인스턴스가 같은 `RetryBudget`을 공유**하는 시나리오로 설계했다 — Distributed Lock의 `LockStore` 공유 패턴과 동일한 모양이다. 첫 번째 정책이 예산을 대부분 소진하면, 같은 예산을 쓰는 두 번째(독립된) 정책은 재시도를 거의 못 하고 즉시 실패해야 한다는 것까지 어서션해, "재시도 예산은 개별 요청이 아니라 다운스트림 리소스 전체를 보호한다"는 개념을 정확히 검증한다.

## 17단계 — Build 과제 확장: Event Bus ✅ 완료 (2026-08-25)

- [x] `challenges/event-bus/` 템플릿 repo + stage 설계 (pub/sub, at-least-once delivery, ordering) — 4 stage: pub/sub fan-out, at-least-once(visibility timeout 재전달), ordering, 동시성
- [x] `V18` 마이그레이션으로 챌린지/stage 시딩

**완료 기준 충족**: 14단계와 동일한 패턴 — 로컬에서 참조 구현/스텁을 실제 sandbox로 먼저 검증한 뒤 마이그레이션에 반영. `EventBusControllerIntegrationTest`로 올바른 구현은 4 stage 전부 PASSED(`score=4`), 스텁은 전부 FAILED 확인, 격리 실행 2회 연속 통과. 신규 2개 포함 백엔드 총 105개 테스트 통과. 이로써 로드맵 Phase 2의 Build 과제 확장 4종(Circuit Breaker/Distributed Lock/Retry-Backoff/Event Bus, 14~17단계) 전부 완료.

**진행 중 발견한 결정 사항**:
- Queue(11단계)의 "구독자당 visibility-timeout 큐" 구조를 그대로 재사용하되, **구독자별로 별도 큐를 두어 fan-out을 구현**했다 — `publish()`가 topic이 일치하는 모든 구독자의 큐에 이벤트 사본을 하나씩 넣는다. Queue는 "메시지 하나를 여러 컨슈머 중 하나가 가져간다"(경쟁)였다면, Event Bus는 "메시지 하나를 구독한 모두가 각자 가져간다"(복제) — 같은 at-least-once/visibility-timeout 메커니즘이 두 가지 다른 배달 의미론(competing consumers vs fan-out)에 재사용될 수 있음을 확인했다.
- ordering(stage 3)은 "같은 topic, 같은 구독자" 안에서만 보장하도록 스코프를 좁혔다 — 서로 다른 구독자 간의 순서나 서로 다른 topic 간의 전역 순서는 애초에 pub/sub이 보장하는 개념이 아니므로 테스트 대상에서 제외했다.

## 18단계 — 추가 시나리오: 주문/결제

- [ ] docs/PRD.md §8 표의 "주문/결제"(transaction boundary, outbox/saga, idempotency / 외부 결제 timeout, retry storm, partial failure)를 9~11단계의 다른 시나리오와 같은 상세도로 콘텐츠화 (초기조건/꼬리설계/워게임 3단계 프롬프트, `SimulationEngine` 인시던트 모델, `RuleEvaluator` 평가 포인트)
- [ ] 12단계에서 만든 꼬리설계 variant 구조를 이 시나리오에도 적용

## 18단계 — 추가 시나리오: 주문/결제 ✅ 완료 (2026-08-25)

- [x] docs/PRD.md §8 표의 "주문/결제"(transaction boundary, outbox/saga, idempotency / 외부 결제 timeout, retry storm, partial failure)를 9~11단계의 다른 시나리오와 같은 상세도로 콘텐츠화 (초기조건/꼬리설계/워게임 3단계 프롬프트, `SimulationEngine` 인시던트 모델, `RuleEvaluator` 평가 포인트) — `V19` 마이그레이션
- [x] 12단계에서 만든 꼬리설계 variant 구조를 이 시나리오에도 적용 (3개 variant: 범용/이중결제(멱등성)/트랜잭션 경계)

**완료 기준 충족**: 실제 HTTP 파이프라인으로 Design→꼬리설계(adaptive variant)→Wargame→Report 완주(`Phase2ScenarioE2ETest` 2개), 인시던트 수치를 손으로 미리 계산해(`python3` 스크립트로 사전 검증) `SimulationEngineTest`로 대조. 실제 브라우저로 전체 흐름 확인 — INITIAL(outbox 언급 시 트랜잭션 경계 리스크 미검출), FOLLOWUP(직전 약점=멱등성 → adaptive하게 "이중 결제" variant 선택), Wargame(인시던트 수치 traffic=30rps/p95=480ms/error=30%/backlog=116/pool=312%/PG latency=1000ms 전부 손계산과 정확히 일치) → 3개 액션 순차 적용 → 완전 회복(pool=20%/error=0.1%/p95=60ms, backlog는 18로 잔존 — 격리는 전이를 막을 뿐 backlog 자체를 없애지 않는다는 설계 의도 그대로 확인). 신규 10개 포함 백엔드 총 115개 테스트 통과.

**진행 중 발견한 결정 사항**:
- **ADR-0010("SimulationEngine은 도메인별 독립 함수")과의 긴장을 의식적으로 다뤘다.** PRD.md §8의 "외부 결제 timeout, retry storm"는 표면적으로 notification의 "provider timeout → consumer lag"와 같은 모양처럼 보였지만, 그대로 재사용하지 않고 **완전히 다른 축의 메커니즘**(outbox backlog가 격리되지 않은 공유 커넥션 풀로 번지는 계단식 장애 — bulkhead 패턴)을 설계했다. `queueLag`/`consumerThroughput`/`externalDependencyLatencyMs` 필드는 재사용했지만(이미 있는 필드라 굳이 새로 안 만듦), 실제로 상태를 결정하는 것은 `connectionPoolUsage`(DB 커넥션 풀 압력)이지 처리량 붕괴가 아니다 — 그래서 세 액션(디스패처 증설/멱등성 키/풀 격리) 중 "풀 격리" 하나만 적용해도 사용자 체감 에러율은 크게 개선되지만(0.30→0.02) backlog 자체(116)는 그대로 남는다는, notification에는 없는 새로운 교훈(격리는 전이를 막을 뿐 근본 원인을 고치지 않는다)을 넣을 수 있었다.
- RuleEvaluator concept를 설계하다가 **riskKey 충돌을 직접 만들 뻔했다** — "MISSING_RETRY_BACKOFF"(notification이 이미 사용 중)와 "MISSING_OBSERVABILITY"(coupon이 이미 사용 중)를 그대로 재사용하려다, `domainByRiskKey`가 riskKey 하나당 도메인 하나를 가정한다는 것을 뒤늦게 떠올려 `MISSING_PG_RETRY_BACKOFF`로 도메인 한정 이름을 짓고 observability 개념 자체를 뺐다(notification/product-browsing도 PRD가 명시한 개념만 쓰고 별도 관측 개념을 추가하지 않은 기존 패턴과 일치). 이 충돌을 다시 만들지 않도록 `payment riskKeys do not collide with notification or coupon riskKeys` 회귀 테스트를 추가했다 — 새 도메인을 추가할 때마다 이 검증을 거칠 것.
- 결제 도메인은 트래픽 배수(20배/10배 같은)를 쓰지 않았다 — 인시던트의 트리거가 "트래픽 급증"이 아니라 순수하게 "외부 PG 저하"이기 때문에, 평시와 동일한 주문량(30 rps)에서 PG latency만 20배로 나빠지는 모델로 설계했다. 세 도메인 모두 "트래픽 급증"이었던 것과 달리 이번엔 트래픽이 전혀 늘지 않아도 터지는 장애라는 점이 도메인상 차별점이다.

## 19단계 — 추가 시나리오: 예약 시스템 ✅ 완료 (2026-08-25)

- [x] docs/PRD.md §8 표의 "예약 시스템"(locking, inventory consistency, timeout / 경합, 중복 예약, lock wait) 콘텐츠화 — `V20` 마이그레이션, 3개 FOLLOWUP variant 포함

**완료 기준 충족**: 18단계와 동일한 패턴 — `python3` 스크립트로 인시던트 수치 사전 검증 → `SimulationEngineTest`(4개)로 Kotlin 구현 대조 → `Phase2ScenarioE2ETest`(2개)로 실제 HTTP 파이프라인 확인 → 실제 브라우저로 전체 흐름(INITIAL→FOLLOWUP(adaptive variant: "결제 미완료 이탈" 목표)→Wargame) 확인. 인시던트 수치(traffic=300rps/p95=320ms/error=30%/Lock Wait Queue=880/Lock Capacity=20.0/s/Lock Utilization=4500%)가 손계산과 정확히 일치, 3개 액션 순차 적용 후 완전 회복(Lock Capacity=3400.0/s/Lock Utilization=8.8%/error=0.1%/p95=40ms/Lock Wait Queue=0). 신규 10개 포함 백엔드 총 125개 테스트 통과.

**진행 중 발견한 결정 사항**:
- ADR-0012에 따라 이번에도 **완전히 다른 메커니즘**을 설계했다 — 다운스트림 의존성 저하(payment/notification)도, 캐시 스탬피드(product-browsing)도 아니라 **락 자체의 세분화 수준이 유효 처리 용량을 결정**하는 모델이다. 좌석 전체를 하나의 락으로 묶으면(coarse-grained) 무관한 좌석에 대한 요청까지 서로 줄을 서고, 좌석 단위로 세분화하면(fine-grained) 처리 용량이 20배 뛴다 — 지금까지의 도메인 중 유일하게 "용량 자체가 설계 선택에 따라 수십 배 차이 나는" 축이다.
- "유령 홀드"(결제 미완료 이탈)를 **가용 용량을 깎아먹는 요소**로 모델링했다(payment의 "유실 후 재시도가 유효 부하를 늘린다"와는 반대 방향 — 부하를 늘리는 게 아니라 용량을 줄인다). `holdTimeoutSeconds`가 길수록 더 많은 처리 용량이 이미 이탈한 사용자의 홀드를 위해 영구히 묶여 있는 셈이라, `SHORTEN_HOLD_TIMEOUT` 액션이 직접 용량을 갉아먹는 비율을 줄인다.
- 이 도메인은 트래픽 배수(15배)를 쓰지만, payment처럼 트래픽이 늘지 않는 장애도 아니고 coupon처럼 순수 트래픽 문제도 아닌 **중간 지점**이다 — 트래픽 급증이 "경합"을 유발하는 트리거이긴 하지만, 실제로 사용자를 구하는 것은 트래픽을 줄이는 게 아니라 락 구조 자체를 바꾸는 것(fine-grained locking)이다.

## 19단계와 20단계 사이 참고: `challenges/event-bus` 이후 Build 마이그레이션은 `V21`부터 시작한다 — `V19`(payment)/`V20`(reservation)을 시나리오 시딩에 이미 썼다.

## 20단계 — 추가 시나리오: 배치/정산 ✅ 완료 (2026-08-26)

- [x] docs/PRD.md §8 표의 "배치/정산"(chunking, restartability, reconciliation / partial failure, long transaction, 재처리) 콘텐츠화 — `V21` 마이그레이션, 3개 FOLLOWUP variant 포함

**완료 기준 충족**: 18/19단계와 동일한 패턴 — `python3` 스크립트로 인시던트 수치 사전 검증 → `SimulationEngineTest`(4개)로 Kotlin 구현 대조 → `Phase2ScenarioE2ETest`(2개)로 실제 HTTP 파이프라인 확인 → 실제 브라우저로 전체 흐름(INITIAL→FOLLOWUP(adaptive variant: "중복 반영" 목표)→Wargame) 확인. 인시던트 수치(traffic=20000rps/p95=1150ms/error=60.0%/재처리 대상 레코드=600000/처리 처리량=7272.7rec/s/재처리 부하율=60.0%)가 손계산과 정확히 일치, 3개 액션 순차 적용 후 완전 회복(p95=700ms/error=0.0%/availability=100.0%/재처리 대상 레코드=1000/처리 처리량=9990.0rec/s/재처리 부하율=0.1%). 신규 10개 포함 백엔드 총 135개 테스트 통과, `Phase2ScenarioE2ETest`는 격리 재실행 2회로 플레이키니스 없음을 재확인.

**진행 중 발견한 결정 사항**:
- ADR-0012에 따라 이번에도 **완전히 다른 메커니즘**을 설계했다 — 이전 5개 도메인 전부가 "동시 요청이 하나의 자원을 두고 경합"하는 모양이었다면, 배치/정산은 **하나의 연속된 작업이 중간에 끊겼을 때 무엇을 다시 해야 하는가**가 핵심이다. 체크포인트가 없으면(checkpointingEnabled=false) 실패 시점까지 이미 처리한 레코드 전부를 버리고 처음부터 재시작해야 하고, 있으면 실패한 청크 하나만 버린다.
- 청크 크기(chunkSize)는 reservation의 락 세분화처럼 "작을수록 무조건 좋다"가 아니라 진짜 트레이드오프다 — 작을수록 실패 시 버리는 양은 줄지만, 청크마다 붙는 고정 커밋 오버헤드 비중이 커져 정상 처리량 자체가 낮아진다(청크 10000 기준 처리량 18181.8rec/s → 청크 1000 기준 10000.0rec/s). 그래서 `REDUCE_CHUNK_SIZE` 단독 적용은 checkpointing 없이는 아무 효과가 없다 — 체크포인트가 없으면 어차피 처음부터 재시작이라 청크 크기가 재처리 범위에 영향을 주지 못한다는 점을 `SimulationEngineTest`의 "no single action alone recovers" 테스트로 명시적으로 검증했다.
- **errorRate의 의미 자체가 다르다**: 이전 도메인들은 errorRate가 "요청 실패율"(포화로 인한 타임아웃/거절)이었지만, 배치/정산에서는 **정산 정합성이 깨진 레코드 비율**(재처리로 인한 중복 반영)이다. 데이터 정합성 문제는 시스템이 "포화"돼서가 아니라 멱등성 설계 구멍 때문에 생기는 것이라, 이 도메인만 `latencyMultiplier`/`errorRateFor` 공용 utilization 밴드를 쓰지 않고 직접 계산한다 — `ENABLE_IDEMPOTENT_RECONCILIATION` 단독 적용 시 errorRate는 즉시 0으로 떨어지지만 재처리 대상 레코드/처리량은 그대로 나쁜 상태로 남는 것으로 이를 확인했다(정합성 축과 낭비 작업량 축이 서로 독립적).
- 19단계의 교훈을 그대로 적용해 riskKey 충돌을 사전에 grep으로 확인하고, "다른 4개 도메인과 겹치지 않는다"는 회귀 테스트를 먼저 작성한 뒤 구현했다.

## Phase 2 완료 — MVP 3개 + 신규 3개(payment/reservation/batch-settlement) 총 6개 도메인. 다음은 콘텐츠 확장(추가 도메인)이 아니라 실제 사용자 검증(원래 로드맵 원칙으로의 복귀) 또는 남은 UX/운영 이슈 처리 방향을 사용자와 논의한다.

---

## Phase 3 — Real Runtime (docs/ROADMAP.md)

사용자가 `docs/ROADMAP.md` 운영 원칙("Phase 3는 비용이 크므로 Phase 1~2 검증 신호 확인 후에만 투자한다")을 명시적으로 우회하고 착수를 선택했다(AskUserQuestion, "권장 아님" 프레이밍 인지 후 선택). ROADMAP.md의 Phase 3 범위 전체(실제 컨테이너 인프라, k6/Locust, Toxiproxy, OpenTelemetry, Incident Replay, Postmortem, 고급 Kafka/K8s 시나리오, 면접형 타이머)는 한 스텝에 담기엔 너무 커서, 아래처럼 순서를 나눈다. 21단계만 완료했고, 22단계 이후는 헤더만 미리 스케치한 backlog — 순서/범위는 ROADMAP 운영 원칙에 따라 이전 스텝 결과를 보고 조정한다.

### 21단계 — SimulationEngine 인터페이스 추출 + 쿠폰 도메인 실전 인프라 파일럿 ✅ 완료 (2026-08-26)

- [x] `SimulationEngine`을 인터페이스로 추출, 기존 로직은 `RuleBasedSimulationEngine`으로 이동 (수식/상수 무변경, 6개 도메인 전부 회귀 없음)
- [x] `RealInfraCouponEngine` 신규: 세션 전용 Postgres 스키마(`CouponSchemaProvisioner`) + 세션 전용 `HikariDataSource`(`SessionDataSourceRegistry`, 2~20 커넥션 캡) + 실제 Redis 캐시 + 실제 k6 부하(`CouponLoadRunner`, `docker run grafana/k6`)로 `SystemState`를 실측
- [x] `SimulationService`/`SimulationController`에 `realInfra` 옵트인 쿼리 파라미터 추가 (쿠폰 도메인 전용, 다른 5개 도메인·기존 규칙 기반 쿠폰은 완전히 무변경)
- [x] 프론트엔드: 쿠폰 도메인에서만 "인시던트 시작 방식 선택" 게이트(체크박스) 노출, 나머지 5개 도메인은 기존 즉시 자동 시작 그대로
- [x] 신규 테스트 9개(`CouponSchemaProvisionerTest`, `SessionDataSourceRegistryTest`, `RealInfraCouponEngineTest` — 실제 Docker/k6 사용, 범위·상대 비교 검증)
- [x] ADR-0013(스키마-per-세션 결정), ADR-0014(범위/상대 비교 테스트 규범 예외) 작성

**완료 기준 충족**: 실제 브라우저로 전체 흐름(게이트 체크 → 인시던트 시작 → 3개 액션 적용) 확인 — 인시던트 시 실측 p95=91ms, cacheHitRatio=52.1%, Traffic≈2950rps; rate limit/cache TTL/DB pool 3개 액션 적용 후 실측 p95=4ms, cacheHitRatio=99.0%로 회복. `docker exec`로 세션 전용 Postgres 스키마(`realinfra_<uuid>`)와 `coupon_inventory` 테이블이 실제로 존재/갱신됨을 직접 확인. 신규 9개 포함 백엔드 총 145개 테스트 통과, `realinfra` 패키지 테스트는 격리 재실행 3회로 안정성 재확인.

**진행 중 발견한 결정 사항**:
- **매 액션마다 스키마를 재생성(DROP+CREATE)하면 안 된다** — 실제로 시도했다가 `DROP SCHEMA CASCADE`가 이전(포화 상태였던) 프로브의 아직 처리 중인 요청이 세션 전용 풀을 계속 쓰고 있어 DDL 락 대기로 23초 이상 멎는 것을 실측으로 발견했다. 수정: 스키마 프로비저닝은 `startIncident`의 첫 `computeState`에서 단 한 번만 하고, 이후 `applyAction`은 기존 스키마/데이터를 그대로 재사용한다(오히려 클릭마다 재고가 1000으로 초기화되지 않고 실제 청구 내역이 누적되는 게 더 정직하다).
- **`SimulationService.applyAction`이 상태를 실제 엔진 실행 *이후*에만 Redis에 저장한다**는 기존 설계가 실전 인프라 경로에서는 버그가 된다 — k6가 실제로 요청을 보내는 동안 `RealInfraCouponController`가 Redis에서 읽는 traits는 여전히 액션 적용 *이전* 값이라, DB Pool 증가 액션에서 엔진과 컨트롤러가 서로 다른 풀 크기로 풀을 재구축하며 충돌했다(연결 사용량이 항상 0으로 측정되는 버그로 발견). 수정: `RealInfraCouponEngine.applyAction`이 프로브 실행 *전에* 먼저 `SimulationStateStore`에 새 traits를 저장한다.
- 이 기기에서는 4-커넥션 풀이 단순 쿼리 기준 초당 ~2000요청까지는 거의 무증상이고, 실제 포화(p95가 5ms대에서 80ms+ 대로)는 ~3000rps부터 나타났다 — 규칙 기반 엔진의 트래픽 상수(300/6000)를 그대로 재사용하지 않고 별도 설정값(`incident-rps` 기본 3000)으로 분리한 이유. 기기마다 다를 수 있어 설정으로 뺐다(ADR-0013).
- Hikari의 기본 `connectionTimeout`(30초)이 너무 길어 실전 인프라 세션 전용 풀에는 3초로 단축했다 — 실제 경합이 에러가 아니라 지연으로 먼저 나타나는 것을 관측했고(`errorRate`는 3000rps에서도 0.0%에 가까웠던 반면 p95는 255ms까지 치솟음), 이 때문에 rate-limit 효과 검증 테스트도 errorRate가 아니라 p95LatencyMs 비교로 설계했다(ADR-0014).
- k6의 `--summary-export` JSON은 문서로 짐작하지 않고 실제 실행 결과로 확인했다 — `http_req_duration`/`http_reqs`는 필드가 최상위에 바로 있고(`values` wrapper 없음), `http_req_failed`는 `rate`가 아니라 `value` 필드가 실패율이다.
- `sysdrill.simulation.realinfra.app-base-url`의 기본값이 `${local.server.port}`를 참조하는데, 이 프로퍼티는 웹 서버가 실제로 뜬 뒤에야 채워져 생성자 `@Value` 주입 시점엔 아직 없다 — `CouponLoadRunner`가 `Environment`를 주입받아 실제 프로브 실행 시점(호출 시점)에 지연 해석하도록 수정. 이 덕분에 테스트도 `RANDOM_PORT`로 돌릴 수 있게 되어, 개발 환경에 이미 8081을 쓰는 무관한 서비스가 떠 있어도 충돌하지 않는다(실제로 이 문제를 겪고 고쳤다).

### 22단계 — 실전 인프라 세션 정리 자동화 (만료된 스키마/풀 스윕) ✅ 완료 (2026-08-27)

- [x] `RealInfraSessionTracker` 신규: Redis sorted set으로 세션별 "마지막 활동 시각" 추적 (`SimulationStateStore`의 TTL과 별개 — 그 TTL은 시뮬레이션 *상태*만 만료시킬 뿐, 스키마/풀을 정리하라고 알려주지 않는다)
- [x] `RealInfraCouponEngine.probeAndCache`가 매 실제 프로브(최초 진입 + 액션 적용)마다 `touch()`를 호출 — 실제로 쓰이고 있는 세션은 절대 스윕되지 않는다
- [x] `RealInfraSessionSweepWorker` 신규: `BuildRunnerWorker`/`EvaluationWorker`와 동일한 단일 백그라운드 스레드 패턴(`@EventListener(ApplicationReadyEvent)` + `@PreDestroy`), 다만 큐를 비우는 blocking-poll이 아니라 `sweep-interval-minutes`(기본 30분)마다 깨어나는 sleep 루프. `session-idle-timeout-minutes`(기본 360분, `SimulationStateStore`의 6h TTL과 동일한 "idle" 정의)보다 오래 방치된 세션을 찾아 스키마 drop + 풀 evict + 캐시/통계 정리
- [x] 신규 테스트 4개: `RealInfraSessionTrackerTest`(실제 Redis), `RealInfraSessionSweepWorkerTest`(실제 Postgres/HikariDataSource로 방치된 세션은 정리되고 활성 세션은 보존됨을 확인 — `sweepOnce()`를 `internal`로 열어 30분 타이머를 기다리지 않고 결정론적으로 호출)

**완료 기준 충족**: 신규 4개 포함 백엔드 총 149개 테스트 통과. `RealInfraSessionSweepWorkerTest`가 실제 Postgres 스키마를 프로비저닝한 뒤 하나는 인위적으로 과거 타임스탬프로, 하나는 방금 touch된 상태로 만들고 `sweepOnce()`를 직접 호출 — 방치된 스키마는 실제로 사라지고(`BadSqlGrammarException`으로 확인) 활성 스키마는 데이터가 그대로 남아있음을 실제 DB 조회로 검증했다. `realinfra` 패키지 테스트 재실행 시 `RealInfraCouponEngineTest`(21단계, 실제 k6 타이밍 기반)에서 근소한 차이(576.68ms vs 569.99ms)로 1회 실패가 있었으나 재실행 시 통과 — ADR-0014가 이미 문서화한 실기기 타이밍 편차 범위 내이며 22단계 변경과는 무관함을 확인.

**진행 중 발견한 결정 사항**:
- 정리 로직을 `@Scheduled`가 아니라 `BuildRunnerWorker`/`EvaluationWorker`와 동일한 손수 작성한 단일 스레드 루프로 만들었다 — 이 앱 어디에도 `@EnableScheduling`이 켜져 있지 않고, 기존 두 워커가 이미 확립한 패턴을 그대로 따르는 게 새 인프라(스케줄러 추상화)를 하나 더 들이는 것보다 일관적이다.
- "활동"의 정의를 `computeState`의 캐시 히트 경로(단순 폴링)가 아니라 `probeAndCache`(실제 k6 프로브가 도는 순간)로만 좁혔다 — `SimulationStateStore`도 조회가 아니라 저장 시점에만 TTL을 갱신하는 것과 동일한 기존 관례를 따른 것으로, 새로운 불일치를 만들지 않는다.
- 테스트에서 "방치된 지 오래됨"을 재현하려고 `RealInfraSessionTracker`에 임의 타임스탬프 설정 API를 새로 만들지 않고, 테스트가 같은 Redis sorted set에 직접 backdated score를 써넣는 방식을 택했다 — 프로덕션 코드에 테스트 전용 진입점을 추가하지 않기 위함.

### 23단계 — Toxiproxy 기반 네트워크 fault injection ✅ 완료 (2026-08-30)

- [x] `docker-compose.yml`에 `toxiproxy` 서비스 추가 (admin API 8474 + 세션당 동적 프록시용 포트 20000~20049, 최대 동시 실전 인프라 세션 50개)
- [x] `ToxiproxySessionProxy` 신규: 세션별 Toxiproxy 프록시를 실제 Postgres 앞에 생성하고 `latency` toxic(기본 300ms±50ms)을 주입, 세션 전용 `HikariDataSource`가 이 프록시를 거쳐 연결되도록 `SessionDataSourceRegistry.poolFor`에 jdbcUrl override 파라미터 추가
- [x] `RealInfraCouponEngine`/`RealInfraCouponController` 양쪽 모두 세션의 toxiproxy 라우팅 JDBC URL을 사용 — k6 요청이 실제로 주입된 지연을 통과하도록
- [x] `RealInfraSessionSweepWorker`가 방치된 세션의 Toxiproxy 프록시도 함께 정리
- [x] `SystemState.externalDependencyLatencyMs`에 실제 주입된 지연값을 노출, `WargameLive.tsx` 쿠폰 도메인 지표 패널에 "DB 네트워크 지연 (실전 인프라)" 항목 추가
- [x] **실측으로 재보정**: 21단계에서 정한 `incident-rps=3000`은 300ms 지연 하에서 완전히 무의미해짐(모든 요청이 k6 자체 5초 타임아웃에 걸림) — `incident-rps=30`으로 재보정, k6 스크립트의 VU 배정 공식도 지연 증가를 반영해 상향
- [x] 신규 테스트 3개(`ToxiproxySessionProxyTest`, 실제 Toxiproxy 컨테이너 사용) — 프록시 생성/삭제, 멱등성, 그리고 프록시를 거친 쿼리가 직결 쿼리보다 실제로 느림을 실측 검증
- [x] ADR-0015 작성

**완료 기준 충족**: 실제 브라우저로 전체 흐름(게이트 → 실전 인프라 시작 → 3개 액션 적용) 확인 — 인시던트 시 실측 p95=2426ms, DB 네트워크 지연=300ms(주입값과 정확히 일치); 3개 액션 적용 후에도 DB 네트워크 지연은 여전히 300ms로 불변이고 p95는 부분적으로만 개선(이번 실행에서는 3253ms로 오히려 더 나쁘게 나왔는데, 이는 기기 부하 변동 폭 안에 있는 것으로 ADR-0014가 이미 문서화한 현상 — 오히려 "기존 3개 액션이 네트워크 결함 자체는 못 고친다"는 ADR-0015의 요점을 더 뚜렷하게 보여줌). `docker exec`로 세션 전용 스키마와 실제 청구 데이터(remaining=887/1000)가 남아있음을 확인. Postgres 스키마 프로비저닝을 세션당 1회로 제한한 21단계의 교훈처럼, 이번에도 실측 중 진짜 버그 2개(테스트 정리 누락으로 인한 포트 누수, 실패 시 포트 미반환)를 찾아 고쳤다. 신규 3개 포함 백엔드 총 152개 테스트 통과, `realinfra` 패키지는 격리 재실행 3회로 안정성 재확인.

**진행 중 발견한 결정 사항**:
- **21단계의 calibration은 "지연이 거의 0"이라는 전제 위에 있었다** — Toxiproxy로 진짜 300ms 지연을 주입하자마자 그 전제가 깨졌다. 4-커넥션 풀의 이론적 처리량 상한이 `pool/latency ≈ 4/0.3 ≈ 13 req/s`까지 떨어져, 기존 `incident-rps=3000`은 거의 모든 요청이 k6 자체 5초 클라이언트 타임아웃에 걸리는 값이 됐다 — 실측(RATE 20/30/40/60/100 비교)으로 `incident-rps=30`이 "극적이지만 완전히 죽지는 않는" 딱 맞는 지점임을 다시 찾았다.
- **에러율이 아니라 p95/지연이 진짜 신호라는 21단계 교훈이 여기서도 반복됐다** — 300ms 지연 자체는 `errorRate`를 거의 올리지 않는다(연결이 실패하는 게 아니라 그냥 느려질 뿐이므로). `externalDependencyLatencyMs`를 "실제로 측정된 값"이 아니라 "설정된 주입값을 그대로 노출"하는 방식을 택했다 — 이 값은 애초에 세션마다 고정된 상수이니 매번 다시 측정할 이유가 없다.
- **왜 기존 3개 액션에 새 액션을 추가하지 않았는가(ADR-0015)**: retry/circuit breaker 같은 액션을 추가해 "이번에도 3개를 다 누르면 완전히 회복된다"는 기존 패턴을 유지할 수도 있었지만, 그러면 배울 게 없어진다. 애플리케이션 레벨 레버(rate limit/cache TTL/pool 크기)로는 네트워크 왕복 자체를 빠르게 만들 수 없다는 것 — 일부 실전 인프라 문제는 지금 화면에 있는 도구가 아니라 다른 종류의 해법(타임아웃 튜닝, 재시도, 서킷 브레이커, 혹은 네트워크 자체 수정)이 필요하다는 것을 의도적으로 미해결로 남겼다.
- **스키마 프로비저닝처럼, Toxiproxy 프록시도 세션당 1회만 생성하고 액션 적용 시 재사용한다** — 21단계에서 배운 "재프로비저닝은 DDL 락 hang을 유발한다"는 교훈을 그대로 적용해, `RealInfraCouponEngine.applyAction`에서 `toxiproxy.jdbcUrlFor()`가 idempotent하게 기존 프록시를 반환하도록 설계했다.
- Toxiproxy의 `upstream`은 앱 자신이 쓰는 `DB_HOST:DB_PORT`(호스트 포트 5433)가 아니라 docker-compose 서비스명과 컨테이너 내부 포트(`postgres:5432`)를 가리켜야 한다는 걸 실측으로 확인했다 — Toxiproxy는 앱과 달리 컨테이너 안에서 compose 네트워크로 직접 Postgres에 도달하기 때문.

### 24단계 — OpenTelemetry 기반 실측 metrics/traces 파이프라인 ✅ 완료 (2026-08-30)

- [x] `docker-compose.yml`에 `jaeger`(all-in-one, OTLP 네이티브 수신) 서비스 추가 — UI 16686, OTLP gRPC/HTTP 4317/4318
- [x] `spring-boot-starter-opentelemetry` 추가 — Spring MVC HTTP 요청 처리와 Lettuce Redis 호출이 자동으로 계측됨 (코드 변경 없이)
- [x] `RealInfraCouponController`의 두 JDBC 호출(`/remaining` 조회, `/claim` 갱신)을 `ObservationRegistry` 기반 명시적 span(`coupon.db.select_remaining`, `coupon.db.claim`)으로 감쌈 — JDBC는 프록시 라이브러리 없이는 자동 계측되지 않고, 이 스팬이야말로 실제 Toxiproxy 지연이 드러나는 지점이기 때문
- [x] 신규 테스트 1개(`RealInfraCouponTracingTest`, 실제 HTTP 요청 → 실제 Jaeger 조회 API로 트레이스 검증)
- [x] `management.tracing.sampling.probability=1.0`(파일럿 규모라 전량 샘플링), OTLP 메트릭 export는 비활성화(Jaeger는 트레이스만 수신)

**완료 기준 충족**: 실제 HTTP 요청(`POST .../claim`) → 실제 Jaeger 조회 API로 확인한 트레이스가 `http post .../claim`(314.38ms) → `coupon.db.claim`(**300.46ms**, 설정된 Toxiproxy 지연 300ms와 사실상 일치) → `get`/`del`(Redis, 각각 1ms 미만) 순으로 정확히 중첩되어 나타남을 확인 — 21~23단계에서 숫자로만 보던 "실제 지연"이 이번엔 실제 분산 트레이스의 스팬 구조로 눈에 보이게 됐다. 신규 1개 포함 백엔드 총 153개 테스트 통과, `realinfra` 패키지는 격리 재실행 3회로 안정성 재확인.

**진행 중 발견한 결정 사항**:
- **Spring Boot 3.x 시절 방식(`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` 수동 조합)은 컴파일은 되지만 트레이스를 하나도 만들지 않았다** — 이 프로젝트가 쓰는 Spring Boot 4.1.1은 tracing autoconfiguration을 `spring-boot-actuator-autoconfigure`에서 완전히 빼내 `spring-boot-starter-opentelemetry`라는 전용 스타터로 옮겼다. `--debug` 조건 평가 리포트로 `OtlpTracingAutoConfiguration`/`OpenTelemetryTracingAutoConfiguration`이 아예 평가조차 안 되고 있음을 확인하고서야 원인을 찾았다 — 최신 프레임워크 버전을 쓸 때는 예전 지식(3.x 문서)을 그대로 믿지 말고 실제 조건 평가 결과로 검증해야 한다는 교훈.
- **프로퍼티 네임스페이스도 통째로 바뀌었다**: 익숙한 `management.otlp.tracing.endpoint`는 이 버전에서 메타데이터에는 남아있지만 조용히 무시된다 — 실제로 동작하는 키는 `management.opentelemetry.tracing.export.otlp.endpoint`. 스팬이 `SdkSpan` 레벨에서는 실제로 생성되고 있는데(DEBUG 로그로 확인) Jaeger에는 하나도 안 뜨는 증상으로 이 문제를 좁혀나갔다 — "코드는 도는데 관측되지 않는다"는 실전 인프라 디버깅에서 반복되는 패턴.
- **`spring-boot-starter-opentelemetry`는 트레이스뿐 아니라 OTLP 메트릭 export도 기본 활성화한다** — Jaeger가 메트릭을 안 받아서 매 export 주기마다 404 에러 로그가 쌓였다. `management.otlp.metrics.export.enabled=false`로 껐다(이 키는 실제로 동작 — 메트릭 쪽은 네임스페이스가 안 바뀐 것으로 보인다).
- **DB 스팬의 지속시간을 "실측"이 아니라 "설정값을 그대로 노출"하는 21~23단계의 패턴과 달리, 이번엔 진짜로 매번 다시 측정된다** — `Observation.observe { }`가 실제 JDBC 호출을 감싸므로 매 요청마다 실제 걸린 시간이 스팬에 기록된다. Toxiproxy 지연은 상수지만, 스팬 자체는 요청마다 실측되는 진짜 관측 데이터라는 점에서 21단계의 "설정값 그대로 노출"과 다른 성격이다.
- 새 ADR은 쓰지 않았다 — 이번 발견들은 "여러 대안 중 하나를 의도적으로 골랐다"기보다는 "제대로 동작하는 유일한 방법을 찾았다"에 가까워, ADR의 세 조건(되돌리기 어려움/맥락 없이 놀라움/진짜 대안 존재) 중 세 번째가 성립하지 않는다고 판단했다.

### 25단계 — Incident Replay ✅ 완료 (2026-08-31)

- [x] `GET /sessions/{sessionId}/simulation/timeline` 신규 — `AppliedAction`(이미 매 액션 적용마다 저장되던 테이블) 하나로부터 재구성, 별도 이력 테이블 없음
- [x] `SimulationService.startIncident`가 "인시던트 시작"을 나타내는 sentinel `AppliedAction`(actionType=`INCIDENT_STARTED`) 행을 추가로 저장 — step 0으로 쓰임
- [x] 규칙 기반 도메인: `AppliedAction`에 저장된 액션 순서를 `RuleBasedSimulationEngine`으로 그대로 재적용해 각 스텝의 지표를 재계산(ADR-0011 — 아무것도 새로 저장하지 않음)
- [x] 실전 인프라(쿠폰) 도메인: 재계산이 불가능하므로 각 스텝의 실측 `SystemState`를 `AppliedAction.parameters`(기존에 있었지만 한 번도 안 쓰이던 JSONB 컬럼)에 그대로 캡처 — ADR-0016
- [x] 프론트엔드: `WargameLive.tsx`의 `MetricsPanel`을 export해 재사용, 신규 리플레이 페이지(`/design/{sessionId}/replay`)에 이전/다음/자동 재생 스크러버 UI, 리포트 페이지에 링크 추가
- [x] 신규 테스트 3개(규칙 기반 타임라인 재구성 2개, 실전 인프라 스냅샷 캡처 1개)
- [x] ADR-0016 작성

**완료 기준 충족**: 규칙 기반 쿠폰 세션(인시던트 시작 → 3개 액션)의 `/timeline` 응답이 4단계 모두 4단계 기존 테스트(Step 4)에서 이미 검증된 정확한 수치와 일치(errorRate 0.3→0.02→0.02→0.001, p95 640→240→240→80ms)함을 확인. 실제 브라우저로 전체 흐름 확인: 세션 완료(COMPLETED) → 리포트 페이지의 "인시던트 리플레이" 링크 → 리플레이 페이지에서 이전/다음 버튼으로 4단계를 정확한 수치·색상 코딩과 함께 이동 → "자동 재생" 클릭 시 1.8초 간격으로 마지막 단계까지 자동 진행 후 정확히 멈춤을 확인. 신규 3개 포함 백엔드 총 156개 테스트 통과, 격리 재실행으로 안정성 재확인.

**진행 중 발견한 결정 사항**:
- **새 이력 테이블을 만들지 않았다** — `AppliedAction`이 이미 세션별 액션을 시각순으로 저장하고 있었고(`findBySessionIdOrderByCreatedAtAsc`), `parameters` JSONB 컬럼은 한 번도 쓰인 적이 없었다. 리플레이 기능 전체를 기존 테이블 하나 위에 얹었다 — 마이그레이션 없이, 컬럼 재활용만으로.
- **`engineMode`가 어디에도 영속화돼 있지 않다는 걸 이번에 처음 발견했다** — `SimulationStateStore`(Redis, 6시간 TTL)에만 있어서, 세션이 끝나고 한참 뒤(리포트를 볼 시점)엔 이미 사라졌을 수 있다. sentinel `AppliedAction` 행의 `parameters`에 `engineMode`를 함께 기록해 이 문제를 해결했다 — 모든 행에 기록하지만(첫 행만 있어도 충분하지만 매번 쓰는 게 특별 케이스보다 단순함) 실제로 리플레이 판단에 쓰는 건 첫 행뿐이다.
- **실전 인프라 세션의 스냅샷은 재계산이 아니라 그 순간의 실측값을 그대로 저장한다** — 21단계에서 이미 배운 대로, 실전 인프라 숫자는 나중에 다시 만들어낼 수 없다(인프라가 이미 정리됐거나, 다시 돌려도 타이밍이 달라짐). ADR-0011("파생값은 저장하지 않는다")의 예외를 ADR-0016으로 명시적으로 남겼다 — 예외의 범위를 "재계산이 근본적으로 불가능한 경우"로 좁게 잡아, 다른 곳에 이 예외가 함부로 확대 적용되지 않도록 했다.
- 프론트엔드에서 `MetricsPanel`을 별도 컴포넌트 파일로 옮기지 않고 `WargameLive.tsx`에서 그대로 export만 해서 재사용했다 — 이미 순수 프레젠테이션 함수라 폴링 로직과 결합돼 있지 않았고, 파일 이동은 이번 스텝의 범위를 벗어나는 리팩터라고 판단했다.

### 26단계 — Postmortem 작성 기능 ✅ 완료 (2026-08-31)

- [x] `GET/PUT /sessions/{sessionId}/postmortem` 신규 — 세션 완료 후 사용자가 직접 작성하는 구조화된 포스트모템(신규 `postmortems` 테이블, V22 마이그레이션)
- [x] MTTD(최초 대응까지)/MTTR(마지막 조치까지)/조치 타임라인/전후 지표는 25단계의 `SimulationService.getTimeline`(→ `AppliedAction`)으로부터 매 조회마다 다시 계산 — 저장하는 건 사용자가 직접 쓴 서술뿐(근본 원인, 임시 완화 조치, 근본 해결 조치, 재발 방지 액션 아이템)
- [x] `PUT`은 세션이 `COMPLETED` 상태가 아니면 409(`ConflictException`) — `SessionService`의 기존 상태-가드 관례를 그대로 따름
- [x] 프론트엔드: `/design/{sessionId}/postmortem` 페이지 — 자동 계산 요약(MTTD/MTTR/조치별 효과-부작용 텍스트/지표 전후 비교) + 직접 작성 폼(근본 원인/완화·근본 조치/재발 방지, 한 줄에 하나씩 입력) + 저장. 리포트 페이지에 링크 추가
- [x] 백엔드 신규 테스트 4개(작성 전 초안 조회, 인시던트 미시작 시 빈 초안, 완료 전 저장 거부 409, 완료 후 저장·재조회 시 서술은 영속·지표는 재계산됨을 확인)

**완료 기준 충족**: 실제 쿠폰 세션으로 인시던트 시작 → 3개 조치 적용 → 세션을 INITIAL/FOLLOWUP/INCIDENT 전 단계 제출·평가까지 실제로 완주해 COMPLETED로 전환 → 브라우저에서 `/postmortem` 페이지 접근 시 MTTD 1초/MTTR 3초/Error Rate 30.0%→0.1%/p95 640ms→80ms가 정확히 표시됨을 확인 → 4개 서술 필드를 직접 입력해 저장 → 새로고침 후에도 4개 필드 모두 그대로 남아있고 MTTD/MTTR/지표는 여전히 매 요청마다 신선하게 재계산됨을 API 응답으로 직접 검증. 완료 전 저장 시도가 409로 거부됨을 curl로 확인. 신규 4개 포함 백엔드 총 160개 테스트 통과.

**진행 중 발견한 결정 사항**:
- **"Postmortem 작성 기능"을 INCIDENT phase의 기존 자유서술 제출(`장애 대응 회고`)과 분리된, 완전히 새로운 구조화 문서로 만들었다.** INCIDENT phase 제출은 이미 사실상 가벼운 회고문이지만 범용 루브릭으로 채점되는 `Submission`/`Evaluation` 파이프라인의 일부다. 이번 Postmortem은 그 파이프라인을 전혀 건드리지 않고, `Report`와 같은 층위의 완전히 독립된 문서(자체 엔티티·서비스·컨트롤러)로 만들었다 — 세션이 끝난 뒤 "무슨 일이 있었는지"를 실제 계측 데이터(MTTD/MTTR/조치 타임라인)를 근거로 정리하는 별도 산출물이라는 점에서 역할이 다르다고 판단했다. LLM 채점은 이번 스텝 범위에서 의도적으로 제외했다(조용히 빠뜨린 게 아니라 알고 미룬 것 — 필요해지면 27단계 이후 재검토).
- **MTTD/MTTR은 지속적 모니터링이 아니라 "최초/마지막 조치가 적용된 시각"으로 근사했다.** 정석적인 MTTD는 "감지한 시각"이고 MTTR은 "지표가 SLO 이내로 완전히 돌아온 시각"이지만, 이 앱엔 별도의 "감지 확인" 액션이 없고(사용자가 곧바로 조치를 적용함) 도메인마다 SLO 임계값이 명시적으로 정의돼 있지 않다. 그래서 실용적으로 "첫 조치 적용 = 최초 대응", "마지막 조치 적용 = 대응 종료"로 근사했다 — PRD가 언급하는 지표의 정신은 살리되, 존재하지 않는 정밀도를 지어내지 않았다.
- **MTTD/MTTR/조치 타임라인/전후 지표를 `postmortems` 테이블에 저장하지 않았다.** ADR-0011(파생값은 저장하지 않는다)과 25단계/ADR-0016의 계보를 그대로 따른 것 — `AppliedAction`이 이미 진실의 원천이고, 이번 스텝은 그 위에 사용자 서술 필드만 얹었다. 이 결정 자체는 놀랍지 않다(이미 두 번 확립된 패턴을 그대로 반복)고 판단해 별도 ADR은 쓰지 않았다.
- **`describe(actionType)`가 이미 만들어 두던 "긍정 효과/가능한 부작용" 텍스트(`AppliedAction.effect`)를 포스트모템의 조치 타임라인에 그대로 재사용했다.** 이 텍스트는 PRD의 핵심 가설("어떤 조치가 어떤 부작용을 만드는지 반복 경험")을 이미 담고 있어서, 포스트모템을 위해 새로 쓸 이유가 없었다.

### 27단계 — 고급 Kafka 시나리오 (실제 컨테이너) ✅ 완료 (2026-09-01)

- [x] `docker-compose.yml`에 실제 단일 노드 KRaft Kafka 브로커(`apache/kafka:3.9.0`) 추가 — 호스트 포트 19092(기본 9092 아님, postgres의 5433과 동일한 이유로 다른 로컬 Kafka와의 충돌 회피)
- [x] `RealInfraNotificationEngine` — "notification" 도메인 전용 실전 인프라 `SimulationEngine`(ADR-0013의 스키마-per-세션을 Kafka에 적용, 세션당 실제 토픽)
- [x] `NotificationLoadRunner` — 별도 로드 생성 컨테이너 없이 `kafka-clients` 라이브러리로 인프로세스에서 실제 프로듀서/컨슈머 스레드를 직접 구동(ADR-0017)
- [x] 실측 지표: `queueLag`은 AdminClient로 조회한 진짜 커밋 오프셋 대비 최신 오프셋 차이, `p95LatencyMs`/`errorRate`는 발행~소비 실측 레이턴시(`expiry-ms` 초과 시 실패로 집계), `consumerThroughput`/`trafficRps`는 실측 처리량
- [x] `SimulationService`의 실전 인프라 엔진 선택을 coupon 전용 하드코딩에서 `domain → SimulationEngine` 맵으로 일반화(ADR-0018), 두 번째 실전 인프라 도메인 추가를 위한 구조 변경
- [x] `RealInfraSessionSweepWorker`를 `RealInfraResourceCleaner` 인터페이스 기반으로 일반화(coupon/notification 각각 구현체) — 세 번째 실전 인프라 도메인이 와도 스윕 워커 자체는 수정 불필요
- [x] 프론트엔드: `WargameLive.tsx`의 "실전 인프라로 시작" 게이트를 `REAL_INFRA_DOMAINS` 집합 기반으로 일반화, notification 도메인 전용 설명 문구 추가
- [x] 신규 백엔드 테스트 5개(토픽 생성/삭제 3개, 실측 엔진 동작 2개)
- [x] ADR-0017(인프로세스 클라이언트 vs 외부 로드젠 컨테이너), ADR-0018(도메인→엔진 맵 일반화) 작성

**완료 기준 충족**: 실제 브라우저로 notification 도메인 세션을 INCIDENT phase까지 진행 → "실전 인프라로 시작" 체크 → 인시던트 시작 시 실제 Kafka 토픽·컨슈머 그룹이 생성되고 심각한 실측 장애 상태(Traffic 362rps, p95 7547ms, Error Rate 92.3%, Queue Lag 2166, Consumer Throughput 0.3/s) 확인 → Circuit Breaker/컨슈머 증설/Retry Backoff 조정 3개 조치를 순서대로 적용하며 매번 실측값이 일관되게 개선됨을 확인(p95 7547→5101→2799ms, Error Rate 92.3%→87.3%→72.3%, Queue Lag 2166→1775→138, Consumer Throughput 0.3→14.3→50.3/s) — 규칙 기반 엔진의 "세 조치를 모두 적용해야 회복된다"는 설계 의도와 일치하는 실측 결과. 신규 5개 포함 백엔드 총 165개 테스트 통과.

**진행 중 발견한 결정 사항**:
- **Kafka Docker 이미지의 `listeners` 호스트는 `0.0.0.0`이 아니라 빈 문자열이어야 한다** — `KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,...`로 두면 `advertised.listeners`가 `KAFKA_ADVERTISED_LISTENERS`로 명시하지 않은 리스너(CONTROLLER)에 대해 `listeners`의 호스트값을 그대로 물려받는데, 이때 `0.0.0.0`이 "advertised 불가능한 non-routable 주소"로 검증에 걸려 기동이 즉시 실패한다(`requirement failed: advertised.listeners cannot use the nonroutable meta-address 0.0.0.0`). 이미지 자체의 기본 템플릿처럼 `PLAINTEXT://:9092,CONTROLLER://:9093`(빈 호스트 = "모든 인터페이스에 바인드"라는 관용구이되 advertise 후보로는 취급되지 않음)로 바꾸자 즉시 해결 — 컨테이너 내부에서 `KafkaDockerWrapper setup` 스텝만 따로 실행해 실제 생성되는 `server.properties`를 직접 비교하며 원인을 좁혔다.
- **세션당 하나의 고정 컨슈머 그룹을 유지하는 "백로그 이월" 설계를 시도했다가 되돌렸다.** coupon 파일럿의 "재고는 액션 사이에 리셋하지 않는다"는 철학(ADR-0013)을 그대로 옮겨 하나의 컨슈머 그룹을 세션 내내 재사용해봤는데, 심각한 인시던트로 한 번 수천 건 백로그가 쌓이면 Kafka는 그 그룹에게 항상 "가장 오래된 안 읽은 메시지"부터 넘겨주므로, 이후 모든 프로브가 몇 분 전 메시지의 실측 지연만 측정하게 되어 조치를 아무리 적용해도 p95/errorRate가 계속 악화되기만 하는 상황을 실제로 관찰했다(2번째 액션에서 p95가 7.5초→74초로 폭증). 매 프로브(=매 액션 적용)마다 `latest`부터 읽는 새 컨슈머 그룹을 쓰도록 바꿔, "지금 이 설정이 방금 발행된 부하를 얼마나 잘 따라가는가"만 측정하도록 스코프를 좁혔다 — 토픽 자체는 재생성하지 않아(ADR-0013의 "provisionSchema=false"와 동일 이유) 오버헤드는 없다.
- **DLQ 토픽은 이번 스텝 범위에서 의도적으로 제외했다.** "실패"는 별도 토픽으로 라우팅하지 않고, 발행~소비 지연이 `expiry-ms`를 넘긴 메시지를 카운터로만 집계한다 — 두 번째 토픽과 그 생명주기 관리를 추가하는 대신, 첫 슬라이스는 측정 파이프라인 자체를 검증하는 데 집중했다(21단계가 컨테이너-per-세션 대신 스키마-per-세션을 고른 것과 동일한 스코프 컷 정신).
- **`RealInfraSessionSweepWorker`를 `List<RealInfraResourceCleaner>` 기반으로 리팩터했다.** 기존엔 coupon 전용 컴포넌트 4개를 직접 주입받아 정리했는데, 두 번째 실전 인프라 도메인이 생기자 이 워커가 "어떤 세션이 어떤 도메인인지" 알아야 하는 문제가 생겼다. 각 도메인의 정리 호출이 이미 멱등(없는 걸 지우려 해도 에러 없음)이라는 점을 이용해, 만료된 세션마다 등록된 모든 cleaner를 그냥 다 호출하도록 단순화 — 세 번째 도메인이 와도 이 워커는 전혀 손댈 필요가 없다.

### 28단계 — 면접형 타이머 모드 ✅ 완료 (2026-09-01)

- [x] 세션 시작 시 옵트인하는 `interviewMode`(`sessions.interview_mode`, V23 마이그레이션) — 켜져 있으면 각 단계(`session_phases.started_at` 기준)에 phase 유형별 제한 시간(`sysdrill.session.interview-timer.*`: INITIAL/FOLLOWUP 600초, INCIDENT 480초)이 적용된다
- [x] `SessionResponse.phaseDeadlineAt` — 프론트가 직접 제한 시간을 계산하지 않고 서버가 내려주는 마감 시각 하나로 카운트다운을 그린다
- [x] `Submission.onTime`(V23) — 제출 시점이 마감 이전이었는지 서버가 계산해 기록(면접형이 아니면 `null` — "해당 없음"과 "지각"을 구분)
- [x] 프론트엔드: 대시보드에 "면접형 타이머 모드로 시작" 체크박스, 신규 `PhaseTimer` 컴포넌트(남은 시간 표시, 60초 이하 시 경고색), 시간 초과 시 현재 작성 중인 답안을 그대로 자동 제출(빈 답안이면 "시간 초과로 자동 제출됨" 플레이스홀더)
- [x] 리포트 페이지에 단계별 "시간 내 제출"/"시간 초과" 배지 추가
- [x] 신규 백엔드 테스트 3개(면접형 아닌 세션은 마감 없음, 면접형 세션은 가까운 미래에 마감, 마감 전/후 제출이 각각 on-time/late로 기록됨 — 전용 Spring 컨텍스트에서 INITIAL 제한을 1초로 낮춰 실제로 기다렸다 검증)

**완료 기준 충족**: 백엔드를 `INTERVIEW_TIMER_INITIAL_SECONDS=20`으로 띄운 뒤 실제 브라우저에서 면접형 타이머 체크 → 쿠폰 시나리오 시작 → 첫 단계 타이머(0:15)가 실제로 0에 도달할 때까지 아무 것도 입력하지 않고 기다림 → 자동으로 "(시간 초과로 자동 제출됨 — 작성한 내용 없음)"이 제출되고 평가 결과 화면으로 전환됨을 확인, DB에서 해당 제출의 `on_time=false`를 직접 조회로 확인. 이어서 꼬리설계·장애 대응 단계는 시간 내에 실제로 답안을 작성해 제출 → 리포트 페이지에서 초기 설계에는 "시간 초과"(빨간 배지), 나머지 두 단계에는 "시간 내 제출"(초록 배지)이 정확히 표시됨을 확인. 신규 3개 포함 백엔드 총 168개 테스트 통과.

**진행 중 발견한 결정 사항**:
- **타이머 만료를 서버가 강제하지 않고, 클라이언트가 카운트다운 0에 도달했을 때 기존 제출 엔드포인트를 그대로 호출하는 방식으로 구현했다.** 서버 쪽에서 "시간이 다 된 세션"을 감지해 자동 제출하려면 새 백그라운드 워커(폴링 루프)가 필요한데, 이 앱의 세션/제출 흐름은 원래도 사용자가 직접 제출 버튼을 눌러야 진행되는 구조라 서버가 사용자 대신 타이핑을 멈추게 할 수는 없다 — 실제 면접도 "시간이 되면 감독관이 있는 걸 그대로 받는다"는 점에서 클라이언트가 결정하는 게 더 자연스럽다고 판단했다. `onTime`은 클라이언트의 자기 신고가 아니라 서버가 제출 시각과 `session_phases.started_at`을 비교해 직접 계산하므로, 프론트가 타이머를 조작해도 기록되는 값은 조작할 수 없다.
- **`phaseDeadlineAt`을 서버가 계산해 내려주고, 프론트는 제한 시간 자체를 전혀 모른다.** 클라이언트가 "이 phase 유형의 제한 시간이 몇 초인지"를 알 필요가 없게 만들어, 설정값이 바뀌어도(`application.yml`) 프론트 배포 없이 즉시 반영되고, phase 유형별 서로 다른 제한 시간을 프론트가 별도로 하드코딩할 필요가 없다.
- **`onTime: Boolean?`을 `false`가 아니라 `null`을 "해당 없음"으로 썼다.** 면접형이 아닌 세션의 모든 제출은 `null`이고, 면접형 세션의 제출만 `true`/`false`를 갖는다 — 리포트 페이지에서 "시간 초과"/"시간 내 제출" 배지가 면접형이 아닌 세션엔 아예 나타나지 않는 것도 이 구분 덕분(early `false`였다면 모든 비면접형 제출에 "시간 초과"가 잘못 표시됐을 것).

### 29단계 — 고급 Kubernetes 시나리오 ✅ 완료 (2026-09-01)

로컬/CI 제약(실제 K8s 클러스터 없음)을 실제로 재검토한 결과, 사용자에게 세 가지 선택지(규칙 기반 신규 도메인 / Phase 3 백로그 종료 / kind·minikube로 실제 클러스터)를 직접 물어 "규칙 기반 신규 도메인"으로 확정했다(ADR-0019).

- [x] 7번째 시나리오 "실시간 추천 API"(domain: `autoscaling`) 신규 추가 — INITIAL/FOLLOWUP(3개 variant)/INCIDENT 시드 마이그레이션(V24)
- [x] `RuleBasedSimulationEngine.Autoscaling` — 기존 6개 도메인과 다른 메커니즘(ADR-0010/0012): 유효 Pod 용량을 **두 개의 독립적인 승법 패널티**(리소스 제한 미조정으로 인한 OOM 크래시루프, 무중단 배포 안전장치 부재로 인한 롤아웃 중 용량 감소)가 깎는다 — 그래서 Pod 증설만으로는 거의 개선되지 않는다(새 Pod도 똑같이 불안정)는, 이전 도메인엔 없던 교훈
- [x] `SimulationActionType` 3개 추가(`SCALE_OUT_REPLICAS`/`TUNE_RESOURCE_LIMITS`/`ENABLE_ROLLOUT_SAFEGUARD`), `DesignTraits`에 `podReplicas`/`resourceLimitsTuned`/`rolloutSafeguardEnabled` 추가, 코덱 인코딩에 반영
- [x] `RuleEvaluator`에 `autoscalingConcepts`(오토스케일링/리소스 제한/무중단 배포 3개 리스크 키) 추가
- [x] 프론트엔드: `ACTIONS_BY_DOMAIN`/`INCIDENT_EVENT_BY_DOMAIN`/`MetricsPanel`/`DESIGN_GUIDANCE_BY_DOMAIN`에 autoscaling 항목 추가
- [x] 신규 백엔드 테스트 5개(엔진 4개 — 기준/인시던트/단일 조치로는 미회복/3개 조치 모두 적용 시 회복, 코덱 라운드트립 1개) — 손으로 계산한 수치와 정확히 일치
- [x] ADR-0019 작성 (규칙 기반 vs 실제 클러스터 선택 근거)

**완료 기준 충족**: 손으로 계산한 수치(기준 errorRate 0.001/p95 45ms, 인시던트 errorRate 0.30/p95 360ms/재시작 Pod 2개, 3개 조치 모두 적용 시 errorRate 0.001/p95 45ms로 완전 회복)가 신규 테스트와 정확히 일치. 실제 브라우저·API로 새 세션을 INITIAL→FOLLOWUP→INCIDENT까지 진행 → 인시던트 시작 시 위와 동일한 실측값 확인 → SCALE_OUT_REPLICAS만 적용해도 여전히 errorRate 0.30(설계 의도대로 "Pod만 늘려선 소용없음" 확인) → 3개 조치 모두 적용 시 완전 회복 → 리스크 키워드가 빠진 회고문 제출 시 RuleEvaluator가 3개 리스크(MISSING_AUTOSCALING/MISSING_RESOURCE_LIMITS/MISSING_ROLLOUT_SAFETY)를 정확히 검출해 리포트에 표시됨을 확인. 신규 5개 포함 백엔드 총 173개 테스트 통과.

**진행 중 발견한 결정 사항**:
- **"고급 Kubernetes 시나리오"를 coupon/notification처럼 기존 도메인에 real-infra 경로를 추가하는 방식으로 풀 수 없었다.** 그 두 파일럿은 모두 이미 존재하는 규칙 기반 도메인에 옵트인 real-infra 엔진을 "얹은" 것이었는데, Kubernetes는 애초에 얹을 기존 도메인이 없다 — "이 도메인에도 real-infra를 추가할까"가 아니라 "완전히 새로운 무거운 인프라(실제 클러스터)를 이 개발 환경에 들일까"라는, 이전 두 파일럿과는 질적으로 다른 제품 범위 판단이라 사용자에게 직접 물어 확정했다.
- **utilization 밴드 자체는 재사용하되(coupon 이후 모든 도메인의 공용 `latencyMultiplier`/`errorRateFor`), "무엇이 그 utilization을 만드는가"의 구조를 완전히 새로 설계했다.** 두 개의 독립적인 승법 패널티가 유효 용량을 깎는다는 구조는 payment의 "격리 여부"나 reservation의 "락 세분화"와도 다르다 — Pod 수(분자가 아니라 분모의 배수 항)를 늘리는 조치가 있다는 점에서 이전 도메인들과 표면적으로 비슷해 보일 수 있지만, "용량을 늘리는 조치 하나만으로는 안정성 문제를 대신 해결해주지 않는다"는 것이 이 도메인만의 교훈이다.

---

## Phase 4 — Team/B2B (docs/ROADMAP.md)

Phase 3(21~29단계)가 모두 완료되어, 사용자가 다음 우선순위로 "Phase 4(Team/B2B) 계획부터 새로 수립"을 선택했다(AskUserQuestion). Phase 4로 가는 진짜 전제조건은 실제 인증이다 — ADR-0003이 "멀티 디바이스 기능이나 로컬/데모 환경 밖 노출 전에 재검토하라"고 명시한 지점이 정확히 지금이다(조직/팀 개념 자체가 클라이언트가 보낸 bare `userId`를 신뢰하는 구조로는 성립하지 않는다). 30단계만 완료했고, 31단계 이후는 헤더만 미리 스케치한 backlog — 순서/범위는 이전 스텝 결과를 보고 조정한다.

### 30단계 — 실제 인증(회원가입/로그인) 도입 ✅ 완료 (2026-09-01)

사용자에게 두 가지를 확인했다: 도입 범위는 점진적(가장 민감한 쓰기 엔드포인트 `POST /sessions`만 우선 보호, 나머지는 31단계로 미룸), 기존 게스트 계정은 완전 대체(계정 연결 로직 없이 닉네임 전용 `POST /users`를 이메일+비밀번호 `POST /auth/signup`으로 전면 교체) — 둘 다 "권장" 옵션으로 확정했다(ADR-0020).

- [x] 신규 패키지 `backend/.../auth/` — `JwtService`(HMAC256 서명, 30일 만료), `AuthController`(`POST /auth/signup`, `POST /auth/login` — BCrypt 해시/검증), `AuthInterceptor`(`Authorization: Bearer` 검증 후 request attribute에 userId 저장), `AuthenticatedUserIdArgumentResolver`(`@AuthenticatedUserId UUID` 커스텀 파라미터), `AuthWebConfig`(`WebMvcConfigurer`로 `/sessions` 경로에만 인터셉터 등록)
- [x] 신규 의존성: `org.springframework.security:spring-security-crypto`(`BCryptPasswordEncoder`만 사용, 풀 `spring-boot-starter-security`는 배제 — 필터체인/CSRF 자동 구성이 기존 순수 REST 구조와 충돌), `com.auth0:java-jwt`
- [x] `UserController.create()`(닉네임 전용 게스트 생성) 삭제, `SessionController.start()`가 `@AuthenticatedUserId`로 인증된 userId를 받도록 변경(`StartSessionRequest.userId` 필드 제거)
- [x] `common/web`에 `UnauthorizedException`/`BadRequestException` 계열과 동일한 패턴으로 401 매핑 추가
- [x] 테스트 인프라: `TestJwtIssuer`(정적 홀더 컴포넌트, 컴포넌트 스캔으로 `JwtService` 빈을 캡처)로 `SessionTestSupport.startSession()` 헬퍼 한 곳만 수정 — 기존에 이 헬퍼를 호출하는 모든 테스트 파일은 호출부 변경 없이 그대로 통과(세션당 스키마 프로비저닝과 동일한 "단일 지점 변경" 설계)
- [x] 신규 `AuthControllerIntegrationTest`(회원가입 시 실제 BCrypt 해시 저장/왕복, 중복 이메일 409, 로그인 성공/실패 401 — 4개), `SessionControllerIntegrationTest`에 무인증 401 테스트 1개 추가
- [x] 프론트엔드: `frontend/src/app/onboarding/page.tsx`를 이메일+비밀번호(+닉네임/연차/스택 선택) 가입 폼으로 교체, 신규 `frontend/src/app/login/page.tsx`, `localSession.ts`에 토큰 저장 추가(`userId`는 31단계 전까지 과도기적으로 계속 공존), `api.ts`의 `apiFetch()`가 저장된 토큰을 자동으로 `Authorization` 헤더에 부착
- [x] **버그 수정**: `AuthInterceptor`가 CORS preflight(`OPTIONS`) 요청까지 가로채 401을 반환해 브라우저에서 실제 `POST /sessions`가 `net::ERR_FAILED`로 막히는 문제를 실제 브라우저 검증 중 발견 — curl은 preflight를 보내지 않아 그동안 통과했다. `preHandle`에 `if (request.method == "OPTIONS") return true` 조기 반환을 추가해 해결(기존 `CorsConfig.kt`가 CORS 자체는 이미 처리)

**완료 기준 충족**: 백엔드 전체 178개 테스트 통과(신규 5개 포함, 0 실패). curl로 회원가입(201) → DB에서 `password_hash`가 평문이 아닌 실제 BCrypt 해시(`$2a$10$...`)임을 직접 조회로 확인 → 중복 이메일 가입 시 409 → 잘못된 비밀번호 로그인 시 401 → 토큰 없이 `POST /sessions` 호출 시 401을 각각 확인. 실제 브라우저로 `/onboarding`에서 가입 → 대시보드 진입 및 토큰/닉네임 저장 확인 → "로그아웃" → `/login`에서 재로그인(200) → 쿠폰 시나리오 "시작" 클릭 → `POST /sessions`가 토큰으로 인증되어 201로 성공, 설계 화면으로 정상 진입함을 확인(이 과정에서 위 CORS preflight 버그를 발견하고 수정).

**진행 중 발견한 결정 사항**:
- **인터셉터를 메서드가 아니라 경로 패턴(`/sessions`, `/sessions/**` 아님)에만 등록해 최소 변경으로 점진적 스코프를 구현했다.** `/sessions`에 매핑된 핸들러는 `SessionController.start()`(`POST`) 하나뿐이라, 경로만으로 다른 서브패스(`/sessions/{id}`, `/sessions/{id}/submissions` 등)에 전혀 영향을 주지 않는다 — 엔드포인트별 인증 로직을 따로 만들 필요가 없었다.
- **CORS preflight 버그는 curl 검증만으로는 잡을 수 없었다** — curl은 브라우저의 preflight 메커니즘 자체가 없어 이 세션의 "실제 브라우저로 검증" 원칙(프론트엔드 변경 단계마다 반복)이 실제로 버그를 잡아낸 사례. 인증이 걸린 엔드포인트를 프론트에서 호출하는 모든 향후 단계(31단계 포함)에 동일 패턴(OPTIONS 조기 반환)이 필요함을 기록해둔다.
- **`TestJwtIssuer`를 실제 `/auth/signup` 호출이 아니라 `JwtService`를 직접 주입받아 토큰만 발급하는 방식으로 만들었다.** BCrypt는 의도적으로 느린 해시라, 수십~수백 개 테스트마다 실제 회원가입 흐름을 태우면 테스트 스위트 전체가 크게 느려진다 — 인증 자체를 검증하는 `AuthControllerIntegrationTest`만 실제 BCrypt 경로를 타고, 나머지는 토큰 발급만 재사용한다.

### 31단계 — 기존 엔드포인트 토큰 기반 인증으로 순차 마이그레이션 ✅ 완료 (2026-09-01)

30단계가 예고한 나머지 조각 — `POST /sessions` 하나만 보호하고 남겨뒀던 15개+ 엔드포인트 전부를 토큰 기반으로 마이그레이션했다. Explore 에이전트로 전체 엔드포인트 인벤토리를 조사하고 Plan 에이전트로 설계를 검증받았다(AuthWebConfig 경로 패턴 확장, 소유권 검사 헬퍼 설계, 프론트엔드 userId 저장 제거 여부).

- [x] `AuthWebConfig`가 `/sessions`, `/sessions/**`, `/submissions/**`, `/build-challenges/**`, `/build-submissions/**`, `/skill-profile`에 인터셉터를 등록하되, k6가 직접 때리는 `RealInfraCouponController`(`/sessions/*/simulation/realinfra/coupon/**`)만 `excludePathPatterns`로 명시적으로 제외(ADR-0021)
- [x] 신규 `SessionAccessGuard`(`requireOwner`, `requireOwnerOfSubmission`) — sessionId/submissionId로만 스코프돼 있어 소유권 검사 자체가 없던 `SessionController`(get/submit/advance)/`SimulationController`(4개)/`ReportController`/`PostmortemController`(get/save)/`EvaluationController.getFeedback`에 전부 배선. "내 것이 아님"도 새 403이 아니라 기존 `NotFoundException`(404)으로 통일 — 리소스 존재 여부 자체를 노출하지 않기 위함
- [x] `GET /users/{userId}/sessions` → `GET /sessions`(`SessionController`에 흡수, `UserSessionController.kt` 삭제), `GET /users/{userId}/skill-profile` → `GET /skill-profile` — 둘 다 경로의 `userId`를 없애고 토큰에서 유도
- [x] `POST /build-challenges/{slug}/submissions`의 body `userId` 필드 제거, `@AuthenticatedUserId`로 대체. `GET /build-submissions/{id}`는 `BuildSubmission.userId`가 직접 있어 가드 없이 컨트롤러에서 바로 비교
- [x] 아무도 호출하지 않는 `UserController.kt`(`GET /users/{id}`)와 그 테스트를 삭제(`UserDtos.kt`의 `UserResponse`는 `AuthDtos.kt`가 여전히 쓰므로 유지)
- [x] 프론트엔드: `api.ts`의 `getUserSessions`/`getSkillProfile`/`submitBuildChallenge`에서 `userId` 파라미터 전부 제거. `localSession.ts`의 `getStoredUserId()`/`sysdrill:userId` 저장을 완전히 삭제(모든 실제 사용처가 이제 토큰만으로 충분) — 로그인 게이트로 쓰던 6개 페이지(`dashboard`/`bridge`/`design/[sessionId]`/`replay`/`postmortem`/`report`)를 `getStoredToken()` 체크로 교체
- [x] 테스트: 신규 `backend/.../support/BuildTestSupport.kt`(`MockMvc.submitBuildChallenge`)로 Build 챌린지 테스트 6개 파일의 거의 동일한 로컬 `submit()` 헬퍼를 통일(`SessionTestSupport.startSession`과 같은 "단일 지점 변경" 설계). 소유권 검사가 새로 걸린 기존 통합 테스트 전체(session/simulation/postmortem/evaluation/reporting 패키지)에 `Authorization` 헤더 추가. 신규 "다른 사용자의 세션/피드백은 404" 테스트 3개 추가

**완료 기준 충족**: 백엔드 전체 179개 테스트 통과(0 실패). curl로 토큰 없이 `GET /sessions`/`GET /skill-profile`/`GET /sessions/{id}`/`GET /sessions/{id}/report` 전부 401 확인 → 세션 소유자가 아닌 다른 사용자 토큰으로 조회 시 404 확인(소유권 검사 실제 동작) → 본인 토큰으로는 200 확인 → `POST /sessions/{id}/simulation/realinfra/coupon/claim`을 인증 헤더 없이 호출해도(실제 인프라로 세션을 프로비저닝한 뒤) 여전히 200으로 성공함을 확인(exclude 패턴이 정확히 먹었음을 검증). 실제 브라우저로 가입 → 대시보드(새 `GET /sessions`/`GET /skill-profile` 정상 로딩) → 쿠폰 세션 시작 → 초기 설계 제출/피드백 → 꼬리설계 → 장애 대응(인시던트 시작 → 조치 적용) → 세션 완료 → 리포트까지 전 구간을 새 무-userId 경로로 정상 진행함을 확인.

**진행 중 발견한 결정 사항**:
- **`RealInfraCouponController`를 이번 광범위한 인증 확장에서 의도적으로 제외했다(ADR-0021).** 이 컨트롤러는 `/sessions/**` 바로 아래에 있어 인터셉터 패턴만 보면 자동으로 포함됐을 것이지만, k6 Docker 컨테이너가 직접 때리는 대상이라 사용자 JWT를 보낼 방법이 없다 — 포함시켰다면 21단계의 실전 인프라 쿠폰 시뮬레이션 자체가 깨졌을 것.
- **개발 중 발견한 프로세스 이슈(코드 문제 아님): 로컬 8081 포트를 다른(무관한) 프로젝트의 백엔드가 이미 점유하고 있어, 이번 curl/브라우저 검증은 임시로 8095 포트 + 그에 맞춘 `SYSDRILL_FRONTEND_ORIGIN`으로 진행했다.** 다른 세션의 프로세스를 죽이지 않고 우회한 것 — 실제 배포/평상시 개발에는 영향 없음.
- **소유권 위반을 403이 아니라 기존 404로 통일한 것은 30단계의 "예외 하나 = 상태코드 하나" 패턴을 그대로 따른 선택이다.** 새 예외 타입을 추가하지 않고, "존재하지 않음"과 "내 것이 아님"을 응답만으로 구분할 수 없게 만들어 리소스 존재 여부 자체가 새어나가지 않도록 했다.

### 32단계 — Organization/Team 데이터 모델 + 멤버십(초대, ADMIN/MEMBER 역할) ✅ 완료 (2026-09-01)

Phase 4의 첫 실제 기능. `docs/ROADMAP.md`의 한 줄짜리 항목 외에는 스펙이 전혀 없는 완전한 백지상태임을 Explore 에이전트로 확인했다(PRD.md/ARCHITECTURE.md/archive/FUTURE_EXPLORATIONS.md 전수 검색). 사용자에게 초대 방식을 확인했다: **이메일 지정 초대**(실제 이메일 발송 없음, 관리자가 초대 대상 이메일을 지정하고 그 이메일로 가입/로그인한 사용자만 수락 가능 — 관리자가 링크를 Slack 등으로 직접 전달).

- [x] 신규 패키지 `backend/.../organization/` — `Organization`(집합체 루트) + `OrganizationMembership`/`OrganizationInvitation`(자식, `organization_id`만 `on delete cascade`) 3테이블(`V25__create_organizations.sql`). 멤버십은 이 코드베이스 최초의 다대다 관계이지만 JPA `@ManyToMany` 없이 ADR-0001("집합체 경계를 넘는 참조는 순수 UUID 스칼라")을 그대로 적용
- [x] 초대 상태는 `PENDING`/`ACCEPTED`/`REVOKED` 3개뿐 — `EXPIRED`를 두지 않고 `expires_at < now()`로 매번 계산(ADR-0011 재적용, 별도 스윕 잡 불필요)
- [x] 초대 토큰은 서명 JWT가 아니라 `UUID.randomUUID().toString()`을 DB에 저장해 조회(ADR-0022) — `SessionService.startSession`의 `seed` 생성과 동일한 관례 재사용
- [x] 신규 `OrganizationAccessGuard` — `SessionAccessGuard`와 동일한 형태(주입 가능한 평범한 컴포넌트, "멤버 아님"과 "ADMIN 아님" 둘 다 기존 `NotFoundException`(404)으로 통일). `OrganizationService`가 마지막 남은 ADMIN의 제거/탈퇴를 409로 차단
- [x] 엔드포인트 10개(`POST/GET /organizations`, `GET /organizations/{orgId}`, `POST/GET /organizations/{orgId}/invitations`, `DELETE .../invitations/{id}`, `GET /organizations/invitations/{token}`(미리보기), `POST .../accept`, `DELETE /organizations/{orgId}/members/{userId}`, `POST /organizations/{orgId}/leave`) — `AuthWebConfig`에 `/organizations`, `/organizations/**` 추가
- [x] 프론트엔드: `frontend/src/app/organizations/page.tsx`(내 조직 목록+생성), `[orgId]/page.tsx`(멤버 목록/초대 폼·초대 링크 표시/대기 초대 목록, ADMIN 전용 UI 분기), `invitations/[token]/page.tsx`(미리보기→수락), `dashboard/page.tsx` 헤더에 "조직" 링크 추가
- [x] 신규 `OrganizationControllerIntegrationTest` 14개(생성 시 ADMIN 자동 등록, 비멤버 404, 비-ADMIN 초대 404, 이메일 일치/불일치 수락, 중복 PENDING 초대 409, 이미 멤버인 이메일 수락 409, 마지막 ADMIN 제거·탈퇴 차단 양쪽 경로, 일반 멤버 제거/자진 탈퇴, 만료 초대 409, 초대 취소→REVOKED, 무인증 401)
- [x] ADR 2개 작성 (0022: 초대 토큰이 서명 JWT가 아닌 이유, 0023: 이메일 지정+실 발송 없음+기존 계정 필요 결정)

**완료 기준 충족**: 백엔드 전체 193개 테스트 통과(기존 179개 + 신규 14개, 0 실패). curl로 무인증 401 → 조직 생성 → 비멤버 조회 404 → 초대 생성 → 잘못된 이메일 계정으로 수락 시도 404 → 올바른 계정으로 수락 200 → 멤버 목록 반영 확인 → 멤버(비-ADMIN) 탈퇴 204 → 마지막 ADMIN 탈퇴 시도 409를 각각 확인. 실제 브라우저로 계정 A 가입 → 조직 생성 → 계정 B 이메일로 초대 → 발급된 초대 링크 확인 → 계정 B 가입 → 초대 링크 방문 → 미리보기(조직명/역할/이메일) 확인 → 수락 → 조직 상세 페이지로 자동 이동, 멤버 목록에 B가 MEMBER로 즉시 반영됨을 확인 → 계정 A로 재로그인해 멤버 목록(2명)과 대기 초대 목록(비어있음, 수락으로 소진됨)이 정확히 갱신됐음을 확인.

**진행 중 발견한 결정 사항**:
- **초대 수락 미리보기(`GET /organizations/invitations/{token}`)는 이메일 일치 검사를 하지 않는다.** 수락(`POST .../accept`)만 `OrganizationAccessGuard.requireInvitationRecipient`로 이메일을 검사한다 — 다른 계정으로 로그인한 사용자도 "이 초대가 누구에게, 어느 조직에 발송됐는지"는 볼 수 있어야 "계정을 잘못 열었다"는 걸 스스로 알아챌 수 있기 때문. 신원 확인이 필요한 지점(수락)과 단순 정보 열람(미리보기)을 의도적으로 분리했다.
- **개발 중 발견한 프로세스 이슈(코드 문제 아님): 이번에도 로컬 8081 포트가 다른 무관한 프로세스에 점유돼 있어 검증은 8095 포트로 우회했다.** 31단계에서와 같은 패턴 — 다른 세션/프로세스를 죽이지 않고 우회.
- **테스트 실행 중간에 실패가 났던 원인은 이번 스텝의 코드가 아니라, 이전(31단계) curl 검증 때 남겨둔 Toxiproxy 프록시 2개가 포트 20000/20001을 계속 점유하고 있었기 때문이었다** — `ToxiproxySessionProxyTest`가 새 프록시를 같은 포트에 만들려다 충돌. Toxiproxy admin API로 직접 정리한 뒤 통과 — 실전 인프라 세션 정리 자동화(22단계)가 스키마/HikariDataSource만 다루고 Toxiproxy 프록시 정리는 다루지 않는다는 기존 갭이 실제로 발현된 사례로 기록해둔다(이번 스텝에서 고치지는 않음).

### 33단계 — 팀 대시보드 (멤버별 훈련 로스터) ✅ 완료 (2026-09-01)

`docs/PRD.md`의 "팀 대시보드" 한 줄 언급 외 스펙이 없어, 사용자와 스코프를 논의했다(AskUserQuestion). 후보 3개(멤버별 훈련 로스터 / +조직 집계 지표 / +도메인별 팀 리스크 히트맵) 중 최소 스코프인 **멤버별 훈련 로스터**를 선택 — 32단계의 멤버십 데이터와 기존 개인 약점 프로필(`GET /skill-profile`)을 조합해, ADMIN이 조직 상세 페이지에서 각 멤버의 완료 세션 수·마지막 활동·최근 추세를 한눈에 보는 화면.

- [x] `SkillProfileController`에 있던 `TrendDirection` enum과 `trendDirection()` 계산을 `SkillProfileService`로 옮겨 공용화 — 개인 대시보드와 팀 대시보드가 같은 추세 판정 로직(윈도우 3/임계값 3.0)을 공유
- [x] `SessionRepository.completionStatsByUserIds(userIds)` — 멤버별 COMPLETED 세션 수 + 마지막 완료 시각을 `GROUP BY`로 한 번에 (N+1 방지, JPA 프로젝션 인터페이스 `SessionCompletionStats`)
- [x] `SkillProfileRepository.findByUserIdIn(userIds)` — 동일한 이유로 벌크 조회
- [x] `GET /organizations/{orgId}/dashboard` 신규, **ADMIN 전용** — 32단계의 `OrganizationAccessGuard.requireAdmin`을 그대로 재사용해 비회원/비-ADMIN 모두 404로 통일(기존 초대 관련 엔드포인트와 동일 컨벤션)
- [x] 프론트엔드: `frontend/src/app/organizations/[orgId]/dashboard/page.tsx` 신규(멤버별 테이블 — 닉네임/이메일/역할/완료 세션 수/마지막 활동/추세 뱃지, 개인 대시보드의 뱃지 스타일 재사용), 조직 상세 페이지에 ADMIN에게만 보이는 "팀 대시보드 보기" 링크 추가
- [x] 신규 `OrganizationControllerIntegrationTest` 3개(ADMIN 조회 시 멤버별 수치 정확성 — 훈련 이력 있는 멤버 vs 없는 멤버, 비-ADMIN 멤버 404, 비회원 404)

**완료 기준 충족**: 신규 3개 포함 백엔드 전체 197개 테스트 통과(0 실패). 실제 브라우저로 계정 A(ADMIN) 가입 → 조직 생성 → 계정 B 이메일로 초대·가입·수락 → (LLM 평가까지 포함한 전체 시나리오 완주는 비용·시간이 커서, DB에 B의 COMPLETED 세션 1건 + 상승 추세 SkillProfile을 직접 삽입해 "훈련을 마친 상태"를 재현) → 계정 A의 팀 대시보드에서 B는 완료 세션=1/마지막 활동 시각 표시/추세 "▲ 상승", A 자신은 완료 세션=0/"훈련 이력 없음"/"데이터 부족"으로 정확히 표시됨을 확인 → 계정 B(MEMBER)로 로그인해 대시보드 URL에 직접 접근 시 "이 대시보드는 관리자만 볼 수 있습니다" 오류 화면(404) 확인.

**진행 중 발견한 결정 사항**:
- **팀 대시보드 접근을 ADMIN 전용으로 제한했다.** MEMBER가 다른 멤버의 개인 훈련 현황(완료 세션 수·추세)을 보는 것은 "관리자가 팀 훈련 상태를 파악한다"는 PRD의 Team/Enterprise 티어 취지를 벗어난 프라이버시 노출이 될 수 있어, 32단계에서 이미 확립된 `requireAdmin`(비회원·비-ADMIN 모두 404) 컨벤션을 그대로 재사용했다.
- **집계를 멤버 수만큼 N+1 쿼리로 하지 않고 벌크 쿼리 2개로 묶었다.** 이번 구현에서는 멤버 수가 적어 체감 차이가 없지만, 조직 규모가 커질 걸 감안해 처음부터 `IN`/`GROUP BY` 기반으로 작성 — 별도 트레이드오프가 있는 결정이 아니라 그냥 더 나은 기본형이라 ADR은 남기지 않았다.

### 34단계 — Private/커스텀 시나리오 ✅ 완료 (2026-09-04)

조직이 사내 장애 사례를 익명화해 자체 시나리오로 만드는 기능(ROADMAP.md Phase 4 항목). 이전 세션에서 v1 스코프 확인 질문(AskUserQuestion)이 거부되어 중단됐던 지점을, 자동 모드 지침에 따라 재질문 없이 그때 "권장" 옵션으로 제시했던 범위(설계+꼬리설계만, Wargame 제외, LLM 단독 평가)로 직접 진행했다.

- [x] `Scenario`에 `organizationId: UUID?` 추가(`V26__add_scenario_organization.sql`, `organizations(id) on delete cascade`) — null이면 기존 공개 시나리오(Flyway 시드), 값이 있으면 그 조직 전용 커스텀 시나리오. ADR-0001(순수 UUID 스칼라 참조) 재적용
- [x] 신규 `CustomScenarioService`(scenario 패키지) — `OrganizationAccessGuard.requireAdmin`/`requireMember` 재사용, 기존 `ContentItem`/`Scenario`/`ScenarioVersion`/`ScenarioStep` 테이블을 그대로 재사용해 정확히 2단계(INITIAL, FOLLOWUP)만 생성 — 분기/다중 FOLLOWUP 없음
- [x] `POST/GET /organizations/{orgId}/scenarios`, `GET /organizations/{orgId}/scenarios/{scenarioId}` 신규(`OrganizationController`) — 이미 보호된 `/organizations/**` 경로라 `AuthWebConfig` 변경 불필요
- [x] `GET /scenarios`, `GET /scenarios/{id}`(기존 공개 엔드포인트)는 인증 모델을 바꾸지 않고 `organizationId != null`인 시나리오만 걸러냄(`list()`는 미노출, `get(id)`는 404) — `ScenarioResponses`로 두 컨트롤러의 응답 조립 로직 공유
- [x] `SessionService.startSession`에 조직 스코프 체크 추가 — `scenario.organizationId != null`이면 `organizationAccessGuard.requireMember` 호출, 비멤버는 404 (지금까지 이 체크가 전혀 없었던 보안 공백을 메움)
- [x] `RuleEvaluator.evaluate()`의 조용한 coupon 폴백(`conceptsByDomain[domain] ?: couponConcepts`)을 `?: emptyList()`로 수정 — 커스텀 시나리오의 자유 도메인 문자열이 우연히 coupon 루브릭으로 잘못 채점되는 걸 방지. 기존 7개 도메인은 전부 맵에 있어 무영향
- [x] 프론트엔드: 조직 상세 페이지에 커스텀 시나리오 섹션(목록 + "세션 시작" 버튼 전 멤버, 생성 폼 ADMIN 전용)
- [x] `RuleEvaluatorTest`의 기존 "falls back to coupon" 테스트를 "returns no findings" 로 교체, `OrganizationControllerIntegrationTest`에 6개 케이스 추가(생성/권한/공개 엔드포인트 비노출/세션 시작 스코프/유효성 검증)
- [x] ADR-0024 작성 — ADR-0002의 전면 폐기가 아닌 부분 공존, Wargame을 v1에서 뺀 이유(하드코딩된 도메인 화이트리스트), RuleEvaluator 폴백 수정 이유를 함께 기록

**완료 기준 충족**: 신규 6개 포함 백엔드 전체 203개 테스트 통과(0 실패, 43개 테스트 클래스). curl로 비-ADMIN 생성 시도 404 / ADMIN 생성 201 / 공개 목록·상세에 비노출 / 비멤버 세션 시작 404 / 멤버 세션 시작 201을 확인했고, 이어서 실제로 INITIAL 제출 → 평가(riskFlags 빈 배열로 coupon 폴백이 사라졌음을 직접 확인) → advance → FOLLOWUP 제출 → 평가 → advance까지 전체 2단계 플로우가 COMPLETED로 끝나는 것을 curl로 확인했다. 실제 브라우저로 관리자 계정 로그인 → 조직 상세 페이지에서 커스텀 시나리오 생성 → 같은 조직 멤버 계정으로 "세션 시작" 클릭 → 설계 화면에 커스텀 초기 프롬프트가 정확히 표시됨을 확인, 대시보드의 공개 시나리오 목록엔 이 커스텀 시나리오가 보이지 않음을 확인.

**진행 중 발견한 결정 사항**:
- **전체 테스트 스위트를 처음 돌렸을 때 `RealInfraSessionSweepWorkerTest`의 캡처된 로그가 26GB까지 자라며 멈추지 않는 사고가 있었다.** 원인은 이번 변경과 무관 — Postgres/Redis만 띄운 채(Toxiproxy/Jaeger/Kafka 없이) 실전 인프라 테스트를 돌리면서, 컨텍스트 종료 시점에 `BuildRunnerWorker`/`EvaluationWorker`의 백그라운드 폴링 루프가 끊기지 않은 Redis 연결에 대해 예외를 반복적으로 던지며 로그를 쏟아내는(pre-existing) 셧다운 경합이 전체 스위트의 수십 개 테스트 클래스에 걸쳐 누적된 것. 프로세스를 강제 종료하고 거대 로그 파일을 삭제한 뒤, Docker 스택 전체(toxiproxy/jaeger/kafka 포함)를 띄우고 재실행하니 5분대에 정상 종료됐다 — 디스크 공간 여유(1.1TB)가 있어 실질 피해는 없었지만, 앞으로 실전 인프라 테스트를 포함한 전체 스위트를 돌릴 땐 반드시 `docker compose up -d`로 5개 서비스를 전부 띄운 상태에서 실행해야 한다는 걸 기록해 둔다.
- **커스텀 시나리오는 정확히 2단계(INITIAL, FOLLOWUP) 고정, 분기/다중 FOLLOWUP 미지원으로 스코프를 컷했다.** 기존 공개 시나리오처럼 `triggerCondition` 기반 분기나 여러 FOLLOWUP 변형을 조직 관리자가 직접 저작하게 하려면 그만큼 입력 폼이 복잡해지는데, v1의 목적(사내 장애 하나를 빠르게 시나리오화)에는 필요 이상이라 가장 작은 슬라이스로 시작했다 — 필요해지면 이후 단계에서 확장.

### 35단계 — 플랫폼 RBAC ✅ 완료 (2026-09-05)

Phase 4 나머지 항목(Game Day/RBAC/SSO/Audit Log/온보딩 커리큘럼) 중 사용자가 RBAC를 먼저 선택했다(AskUserQuestion). `AuthWebConfig.kt`가 정확히 지목하던 공백을 메웠다 — `/admin/prompt-templates`(LLM 평가 프롬프트 템플릿 관리)가 지금까지 인증 자체가 전혀 없어 누구나 호출할 수 있었다.

- [x] `User.platformRole`(`USER`/`PLATFORM_ADMIN`) 신규(`V27__add_user_platform_role.sql`) — 조직 스코프인 `OrganizationRole`과 별개의 플랫폼 전역 축
- [x] 신규 `ForbiddenException`(403) — `SessionAccessGuard`/`OrganizationAccessGuard`가 쓰는 기존 404(리소스 존재 은폐) 컨벤션과 의도적으로 분리. `/admin/prompt-templates`는 특정 인스턴스가 아니라 엔드포인트 전체에 걸린 역할 게이트라 숨길 존재가 없음
- [x] 신규 `auth/PlatformAccessGuard.requirePlatformAdmin(userId)` — 기존 가드들과 동일한 "평범한 `@Component`, 컨트롤러가 명시적으로 첫 줄에서 호출" 패턴
- [x] `PromptTemplateController` 3개 엔드포인트 전부 가드 적용, `AuthWebConfig`에 `/admin/prompt-templates`, `/admin/prompt-templates/**` 추가(이번에 처음 인증이 걸림)
- [x] 부트스트랩은 signup 시점 이메일 화이트리스트(`sysdrill.auth.platform-admin-emails`/`SYSDRILL_AUTH_PLATFORM_ADMIN_EMAILS`, 콤마 구분, 기본 빈 문자열) — 승격/강등 API는 만들지 않음(자기 승격 위험)
- [x] `PromptTemplateControllerIntegrationTest`에 헤더 추가 + 3개 케이스(무토큰 401 / 일반 사용자 403 / 관리자 기존 시나리오 통과), 신규 `AuthControllerPlatformAdminBootstrapTest`(화이트리스트 가입 시 승격, 대소문자 무시, 비화이트리스트는 USER 유지)
- [x] ADR-0025 작성

**완료 기준 충족**: 백엔드 전체 208개 테스트 통과 확인(신규 5개 순증 — `PromptTemplateControllerIntegrationTest` 1→3, `AuthControllerPlatformAdminBootstrapTest` 신규 3개; 0 실패, 44개 클래스). 실행 중인 백엔드를 재시작해 curl로도 확인: 무토큰 401 → 일반 사용자 토큰 403 → DB에서 직접 `PLATFORM_ADMIN`으로 승격한 사용자의 토큰(재로그인 불필요, 매 요청마다 DB에서 역할을 다시 조회하므로)으로는 201 확인.

**진행 중 발견한 결정 사항**:
- **전체 스위트를 처음 두 번 돌렸을 때 각각 다른 테스트가 실패했다 — 둘 다 이번 변경과 무관한 환경적 원인으로 확인.** 1차: `EvaluationWorkerIntegrationTest`의 "duplicate delivery..." 케이스가 실패 — 단독 실행해도 재현되는 진짜 결함이지만, 관련 파일(`EvaluationWorker.kt`/`EvaluationQueue.kt`)이 이번 세션 훨씬 이전 커밋(`edf9981`, 31단계)째 전혀 안 바뀌었고 타이밍에 따라 간헐적이라(세 번째 실행에서는 통과) 범위 밖으로 분리해 별도 백그라운드 작업으로 넘겼다(task_96adeffa). 2차: 전체 스위트가 돌아가는 도중에 내가 백엔드 프로세스를 재시작하고 `docker exec`로 DB를 직접 건드리며 curl 검증을 했더니 `CircuitBreakerControllerIntegrationTest`(Docker 기반 Build Mode 테스트)가 실패했다 — 리소스 경합으로 판단, 아무것도 건드리지 않은 세 번째 재실행에서 완전히 통과해 확인. **교훈: 전체 테스트 스위트가 실행 중일 때는 같은 머신에서 Docker/백엔드 프로세스를 건드리지 않는다.**
- **신규 테스트에서 고정 리터럴 이메일(`bootstrap-admin@example.com` 등)을 썼다가 같은 로컬 Postgres에 스위트를 두 번째로 돌릴 때 409로 실패하는 걸 직접 겪었다.** `AuthControllerIntegrationTest`가 이미 `uniqueEmail()` 헬퍼로 이 문제를 피해온 이유를 그대로 재확인 — `@DynamicPropertySource`로 화이트리스트 이메일 자체를 테스트 클래스 로드 시 랜덤 생성해 고정 리터럴을 완전히 제거했다.
- **403을 이 저장소에 처음 도입했다.** 기존 리소스 소유권 가드(Session/Organization)는 "존재 안 함"과 "권한 없음"을 구분하지 않고 전부 404로 통일해왔는데, 이번처럼 특정 리소스 인스턴스가 아니라 엔드포인트 전체에 걸린 역할 게이트에는 그 이유(존재 은폐)가 적용되지 않아 정직하게 403을 썼다 — ADR-0025에 근거를 남겼다.

### 36단계 — Game Day v1 (관전 + 채팅) ✅ 완료 (2026-09-05)

Phase 4 나머지 항목(Game Day/SSO/Audit Log/온보딩 커리큘럼) 중 사용자가 Game Day를 선택했고(AskUserQuestion), 그 안에서도 가장 작은 슬라이스인 "관전 + 채팅"을 골랐다(세션 오너 한 명이 계속 Wargame을 조작, 같은 조직 멤버들은 실시간으로 지켜보며 채팅). `docs/ARCHITECTURE.md`가 "WebSocket은 실시간 멀티플레이 워게임 이전에는 불필요하다"고 명시한 바로 그 지점이지만, 관전 전용이라 여전히 기존 3초 폴링만으로 충분했다.

- [x] `SessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)` 신규 — `GET /sessions/{id}`, `GET .../simulation/state`, `GET .../simulation/timeline`에 적용, `submit`/`advance`/`incident`/`actions`는 기존 `requireOwner` 그대로(관전자는 절대 변형 불가)
- [x] `SessionResponse`에 `isOwner: Boolean` 추가 — 31단계에서 프론트가 로컬 userId를 완전히 지운 터라 서버가 알려줘야 함
- [x] 신규 채팅(`session_chat_messages` 테이블, `SessionChatController` — `POST/GET /sessions/{id}/chat`), Postgres 영속 + 폴링(WebSocket 없음)
- [x] 신규 `GameDaySessionService` — `GET /organizations/{orgId}/game-day-sessions`로 "진행 중인 팀 세션" 목록 제공
- [x] 프론트: `WargameLive.tsx`에 `isOwner` prop(관전자는 시작 게이트·대응 액션 패널·로컬 타임라인 숨김, 지표만 노출) + 채팅 패널 추가, `design/[sessionId]/page.tsx`에 `spectating` 뷰 분기(오너 전용 상태머신 완전 우회), 조직 페이지에 "진행 중인 팀 세션" 섹션 + "관전하기" 링크
- [x] ADR-0026 작성

**완료 기준 충족**: 백엔드 전체 214개 테스트 중 211개 통과 확인(나머지 3개는 전부 이번 변경과 무관 — 아래 진행 중 발견한 결정 사항 참고). curl + 실제 브라우저로 확인: 오너가 쿠폰 시나리오를 INCIDENT 단계까지 진행 → 같은 조직 멤버 계정으로 조직 페이지의 "진행 중인 팀 세션"에서 "관전하기" 클릭 → 실시간 지표가 3초마다 갱신되고 대응 액션 버튼은 안 보이는 관전 화면 확인 → 채팅으로 메시지 남기면 정상 표시 확인 → 오너 계정에선 기존과 동일하게 액션 패널이 그대로 보임(회귀 없음) 확인 → 비멤버 계정으로는 세션 조회·채팅·목록 전부 404 확인.

**진행 중 발견한 결정 사항**:
- **설계를 구현 도중에 한 번 뒤집었다.** 처음엔 관전 권한을 "세션의 시나리오가 조직 소유(`Scenario.organizationId`, 34단계)인가"로 설계했는데, 실제 브라우저 검증 중에 커스텀 시나리오는 34단계/ADR-0024에서 INITIAL+FOLLOWUP으로만 못박혀 있어 **애초에 INCIDENT 단계에 도달할 수 없다**는 걸 발견했다 — 즉 관전이 지켜볼 대상이 구조적으로 존재하지 않는 설계였다. 관전 권한을 "세션 오너와 내가 같은 조직 멤버인가"(어떤 시나리오든 무관)로 바꿔서, 실제로 INCIDENT가 있는 7개 공개 도메인 세션도 관전 가능하게 고쳤다. `GET /organizations/{orgId}/game-day-sessions`도 같은 이유로 "이 조직이 만든 시나리오의 세션"이 아니라 "이 조직 멤버가 진행 중인 세션"으로 바뀌었다. ADR-0026에 이 반전 자체를 기록했다.
- **전체 테스트 스위트를 이번 세션에서만 세 번 다시 설계해야 했다 — 전부 "같은 로컬 인프라를 다른 프로세스와 공유"에서 비롯된 사고였다.** ①처음 돌린 전체 스위트가 사용자가 병렬로 돌리고 있던 다른 세션(35단계 완료 보고 직후 사용자가 "Fix flaky EvaluationWorkerIntegrationTest" 제안을 별도 세션으로 시작함, task_96adeffa)이 같은 로컬 Postgres에 자기만의 `V28` 마이그레이션(`unique active evaluation per submission`)을 적용해버려서 `FlywayValidateException`으로 전체 컨텍스트 로딩이 막혔다 — 내 `V28`(채팅 테이블)을 `V29`로 밀어냈다. ②그 다음엔 완전히 격리하려고 별도 Postgres/Redis 컨테이너(5555/6390 포트)를 띄워 재실행했는데, Redis만 격리하고 Postgres URL만 바꿨을 때 무관한 도메인(Build/Evaluation) 테스트 26개가 무더기로 실패해 원인을 추적한 끝에 Redis 큐도 같이 격리해야 한다는 걸 확인했다. ③Redis까지 격리한 뒤에도 실전 인프라 쿠폰 컨트롤러 테스트 2개가 남았는데, 원인은 `RealInfraCouponController`가 앱의 기본 데이터소스(`DB_PORT`로 격리됨)가 아니라 **Toxiproxy를 경유해 docker-compose Postgres 컨테이너에 고정 접속**하도록 의도적으로 설계돼 있어서(코드 주석에 이미 명시돼 있음 — Toxiproxy가 host 포트가 아니라 compose 내부 네트워크로 직접 접속) 내 격리된 Postgres가 그 경로엔 아예 안 보였던 것 — 진짜 버그가 아니라 이번 임시 격리 인프라가 그 경로까지는 못 미친 것으로 최종 확인했다. **교훈**: 이 저장소의 로컬 개발 인프라(Postgres/Redis/Toxiproxy/Kafka/Jaeger)는 다른 동시 세션과 공유되며, 실전 인프라 기능(Toxiproxy 경유)은 앱의 `DB_PORT`/`REDIS_PORT` 오버라이드로 격리되지 않는다.
- **`EvaluationWorkerIntegrationTest`의 "duplicate delivery..." 실패는 35단계에서 이미 발견해 별도 세션으로 넘긴 사전 존재 결함(task_96adeffa)이라 이번에도 무관하게 재확인만 했다.**

### 37단계 — Google 로그인 (SSO v1) ✅ 완료 (2026-09-05)

Phase 4 마지막 큰 항목. ROADMAP/PRD엔 "SSO"라는 한 줄 외에 스펙이 없어 사용자에게 범위를 확인했고(AskUserQuestion), 조직별 IdP 강제나 SAML이 아니라 **가장 작은 슬라이스인 "Google 로그인 추가"**를 선택했다 — 기존 이메일/비밀번호 로그인과 나란한 대안, 이메일 일치 시 기존 계정에 자동 연결.

- [x] 신규 `auth/GoogleOAuthClient.kt` — ID 토큰 로컬 서명 검증(JWKS 캐싱·키 로테이션 필요) 대신 Google의 `userinfo` 엔드포인트를 호출해 신원 확인. `LlmClient`와 동일한 "인터페이스로 외부 I/O 격리" 패턴
- [x] 신규 `auth/GoogleAuthService.kt` — CSRF `state`는 Redis 1회성 토큰(5분 TTL), 신규 계정은 아무도 모르는 랜덤 UUID를 BCrypt 해싱한 비밀번호로 생성(비밀번호 로그인 암호학적으로 불가능, 스키마 변경 없음), `email_verified=false`는 거부
- [x] 신규 `auth/GoogleAuthController.kt` — `GET /auth/google/login`(302 → Google 동의 화면), `GET /auth/google/callback`(302 → 프론트, 토큰/닉네임은 쿼리스트링이 아니라 URL fragment로 전달)
- [x] 프론트: 로그인 페이지에 "Google로 계속하기" 버튼(일반 네비게이션, CORS 무관), 신규 `/auth/google/complete` 페이지(fragment 파싱 → 로그인 처리 → 대시보드 이동)
- [x] 신규 `FakeGoogleOAuthClient`(`@TestConfiguration` + `@Primary`, 모킹 프레임워크 아닌 순수 Spring DI 치환 — 이 저장소는 Mockito/mockk를 전혀 안 씀) + `GoogleAuthControllerIntegrationTest`(5개) + `GoogleAuthNotConfiguredTest`(1개)
- [x] ADR-0028 작성

**완료 기준 충족**: 백엔드 전체 220개 테스트 중 218개 통과(신규 6개 전부 포함, 나머지 2개는 36단계에서 이미 원인 규명한 것과 동일한 무관 사고 — 아래 참고). 실제 Google 계정 인증 없이는 전체 플로우를 재현할 수 없어(OAuth 앱 등록 필요), 계정 생성/연결/state 검증/이메일 미인증 거부 로직은 `GoogleAuthControllerIntegrationTest`가 커버하고, curl로 `GET /auth/google/login`이 올바른 Google URL로 302 리다이렉트하는지(설정 시)와 설정이 없으면 400을 반환하는지(기본값), 브라우저로 로그인 페이지 버튼과 `/auth/google/complete` 페이지의 fragment 파싱·로그인 처리·리다이렉트를 각각 확인했다.

**진행 중 발견한 결정 사항**:
- **다시 로컬 인프라 공유 사고를 겪었다 — 이번엔 삭제된 다른 세션이 남긴 orphan 마이그레이션.** 사용자가 앞서 시작했던 "flaky EvaluationWorkerIntegrationTest 수정" 별도 세션이 삭제됐는데, 그 세션이 공유 로컬 Postgres에 자기만의 `V28`(`unique active evaluation per submission`) 마이그레이션을 이미 적용해놓은 채로 사라져서, 그 파일이 이 체크아웃엔 없는 채로 DB엔 적용 기록만 남아 있었다 — 공유 Postgres를 쓰는 모든 실행이 한동안 `FlywayValidateException`으로 막혔다. 내용을 모르는 마이그레이션을 함부로 복원하거나 `flyway repair`하지 않고, 36단계에서 이미 검증된 방식대로 완전히 별도의 Postgres/Redis 컨테이너를 띄워 작업을 계속했다. 나중에 확인해보니 그 세션은 실제로 PR로 병합돼 있었다(`fix(backend): 평가 파이프라인 중복 딜리버리 레이스 수정`, EvaluationWorker에 DB 유니크 제약 기반 멱등성 추가) — `git fetch` 시점에 발견해 병합했다. 그 커밋도 ADR 번호로 `0027`을 썼는데 내가 이미 같은 번호로 Google 로그인 ADR을 써둔 상태라 번호가 충돌했다 — 내 쪽을 `0028`로 밀어서 해결.
- **RealInfraCoupon 관련 2개 테스트 실패는 36단계에서 이미 규명한 것과 정확히 같은 원인(Toxiproxy가 앱의 `DB_PORT` 오버라이드와 무관하게 docker-compose Postgres에 고정 접속)임을 재확인만 했다.**

### 38단계 — 조직 감사 로그 (Audit Log) ✅ 완료 (2026-09-05)

ROADMAP.md엔 "SSO/RBAC/Audit Log"라는 한 줄뿐이었다. 이전 7개 Phase 4 단계는 매번 진짜 갈림길(프로토콜 선택, 폴링 vs WebSocket 등)이 있어 AskUserQuestion으로 범위를 물었지만, 이번엔 그런 갈림길이 없다고 판단해 질문 없이 진행했다 — PRD.md 비즈니스 모델 표가 Audit Log를 팀 대시보드·Game Day·커스텀 시나리오와 함께 Team/Enterprise 티어로 묶어놓은 것 자체가 "조직이 자기 팀 안에서 무슨 일이 있었는지 본다"는 스코프를 가리킨다(플랫폼 전역 감사 트레일이 아님). 세션/훈련 활동은 33단계 팀 대시보드가 이미 다루므로, 감사 로그는 관리 행위(초대/제거/생성 같은 상태 변경)만 새로 추가했다.

- [x] `organization_audit_log_entries` 테이블(`organizationId`/`actorUserId` 순수 UUID 스칼라, `action` enum, `detail` jsonb) + `OrganizationAuditLogService.record()`/`.list()`
- [x] 기존 `OrganizationService`(32단계)의 6개 메서드(생성/초대/초대취소/수락/제거/탈퇴)와 `CustomScenarioService.create`(34단계) 끝에 `record(...)` 한 줄씩 추가 — **같은 트랜잭션 안에서 동기적으로 기록**(별도 이벤트 파이프라인 아님, 행위와 로그가 항상 원자적으로 일치)
- [x] `GET /organizations/{orgId}/audit-log` 신규(ADMIN 전용, 최근 200건, 페이지네이션 없음), 대상(제거된 멤버 등)은 원시 UUID로만 저장하고 조회 시점에 닉네임 join(ADR-0011 재적용)
- [x] 프론트: 신규 `organizations/[orgId]/audit-log/page.tsx`(33단계 팀 대시보드 페이지와 동일 구조), 조직 상세 페이지에 "감사 로그 보기" 링크
- [x] ADR-0029 작성

**완료 기준 충족**: 백엔드 220개 테스트 중 218개 통과(신규 1개 포함) — 나머지 2개는 34~37단계에서 이미 규명한 것과 정확히 같은 Toxiproxy 라우팅 이슈(무관). curl로 조직 생성→초대→수락→커스텀 시나리오 생성→멤버 제거까지 실행한 뒤 감사 로그에 5개 항목이 정확한 순서(최신순)·행위자·detail로 기록됨을 확인, 비-ADMIN 토큰은 404 확인. 실제 브라우저로 조직 상세 페이지의 "감사 로그 보기" → 방금 한 행동 5개가 한글 라벨(멤버 제거/커스텀 시나리오 생성/멤버 가입/멤버 초대/조직 생성)과 함께 표로 정확히 보이는 것 확인.

**진행 중 발견한 결정 사항**:
- **선제적 인프라 격리(별도 Postgres/Redis 컨테이너를 미리 띄우고 시작)는 효과가 있었지만, 새로운 종류의 사고를 하나 더 발견했다.** 처음 전체 스위트를 돌렸을 때 8개, 심지어 재시도에서 OOM까지 겪었다 — 원인을 좁혀보니 이번엔 "다른 세션과의 충돌"이 아니라 **내가 오늘 하루 종일 같은 공유 Toxiproxy/Kafka 컨테이너로 전체 스위트를 계속 돌리면서 6시간 넘게 정리 안 된 프록시·토픽·컨슈머 그룹이 쌓인 것**이었다(Postgres 연결 고갈 한 번, 그다음 힙 부족, 그다음엔 포트 20000 already-in-use와 Kafka 토픽 조회 실패까지) — Toxiproxy/Kafka는 `docker-compose.yml`상 세션 하나가 몇 시간이고 붙들고 재사용하는 장수 컨테이너라, 실패한 테스트가 프록시/토픽을 못 지우고 죽으면 다음 실행에 그대로 누적된다. `docker restart`로 두 컨테이너만 재기동하니(Postgres/Redis는 안 건드림 — 상태가 남아 있어야 하는 데이터니까) 바로 34~37단계에서 이미 알려진 2개 실패로 돌아왔다. **교훈**: 격리된 Postgres/Redis를 새로 띄우는 것만으로는 부족하고, 무거운 전체 스위트를 여러 번 돌릴 땐 Toxiproxy/Kafka도 가끔 재기동해서 누적 상태를 비워야 한다.

### 39단계 — 온보딩 커리큘럼 ✅ 완료 (2026-09-06)

ROADMAP.md의 "On-call Readiness, 신규 입사자 온보딩 트랙"이라는 한 줄이 유일한 스펙이었다. 조직당 여러 트랙·멤버별 배정 방식도 가능했지만, 사용자에게 범위를 확인해(AskUserQuestion) **조직당 커리큘럼 1개**를 선택했다 — 조직 관리자가 시나리오(공개+커스텀 섞어서) 순서를 정하면 전 멤버가 같은 트랙을 보고 자기 진행 상황을 확인하는 가장 작은 슬라이스.

- [x] `V31` 마이그레이션 — `organization_curriculum_steps`(`scenario_steps.step_order`와 동일한 "순서 컬럼이 있는 행 테이블" 컨벤션, jsonb 배열 아님)
- [x] 신규 `OrganizationCurriculumStep`/`OrganizationCurriculumStepRepository`, `OrganizationAuditAction`에 `CURRICULUM_UPDATED` 추가
- [x] 신규 `OrganizationCurriculumService` — `setCurriculum`(ADMIN 전용, 전체 교체 `PUT`, 다른 조직의 비공개 시나리오는 거부, 38단계 감사 로그에 기록) / `getCurriculum`(멤버 전용, `OrganizationService.getDashboard`와 동일한 "배치 조회 후 join" 패턴으로 N+1 없이 제목·완료 여부 계산). 완료 판정은 커리큘럼 생성 이전에 이미 끝낸 세션도 포함(소급 적용) — 세션 시작 로직은 전혀 건드리지 않아 순서를 안 지켜도 막지 않는다
- [x] `OrganizationController`에 `PUT`/`GET /organizations/{orgId}/curriculum` 추가
- [x] 프론트: 신규 `organizations/[orgId]/curriculum/page.tsx`(전 멤버 공용 진행 상황 + 시작 버튼, ADMIN 전용 편집 폼 — 후보 추가/위아래 재정렬/제거/저장), 조직 상세 페이지에 전 멤버에게 보이는 "온보딩 커리큘럼 보기" 링크(팀 대시보드/감사 로그 링크와 달리 ADMIN 전용 아님)
- [x] ADR-0030 작성

**완료 기준 충족**: 백엔드 전체 223개 테스트 중 221개 통과(신규 `OrganizationControllerIntegrationTest` 2개 포함, 기존 27개 회귀 없음) — 나머지 2개는 34~38단계에서 이미 규명한 것과 정확히 같은 Toxiproxy 라우팅 이슈(무관). curl로 관리자가 공개 시나리오(선착순 쿠폰) + 커스텀 시나리오로 커리큘럼 저장 → 비관리자 저장 시도 404 → 멤버 조회 시 둘 다 `completed=false` → 멤버가 첫 시나리오로 세션 완료(DB 직접 반영) 후 재조회하면 `completed=true`로 소급 반영 → 다른 조직의 비공개 시나리오 포함 시도 404 → 감사 로그에 `CURRICULUM_UPDATED` 기록까지 확인. 실제 브라우저로 관리자 계정에서 시나리오 추가·위/아래 재정렬·저장 → 멤버 계정으로 "온보딩 커리큘럼 보기"에서 갱신된 순서와 (관리자 본인은 안 풀었으므로 미완료, 멤버 본인이 푼 것만 완료로) 정확히 사용자별로 다른 완료 상태 확인 → "시작" 클릭 시 실제로 새 세션이 시작되어 `/design/{sessionId}` Workspace로 이동하는 것 확인.

**진행 중 발견한 결정 사항**:
- **Hibernate가 같은 flush 안에서 DELETE보다 INSERT를 먼저 실행한다.** `setCurriculum`의 전체 교체 로직이 `deleteByOrganizationId(orgId)` 직후 같은 `(organizationId, stepOrder)` 유니크 키를 쓰는 새 행을 `save()`하다가 `DataIntegrityViolationException`(유니크 제약 위반)을 만났다 — Hibernate의 기본 액션 큐 플러시 순서는 코드 순서와 무관하게 INSERT를 DELETE보다 먼저 실행하기 때문에, 새 INSERT가 아직 물리적으로 지워지지 않은 옛 행과 유니크 키가 충돌한 것. `deleteByOrganizationId` 직후 `stepRepository.flush()`를 호출해 삭제를 먼저 DB에 반영하도록 강제해서 해결했다. **교훈**: 같은 유니크 키를 재사용하는 "지우고 다시 채우기" 패턴은 JpaRepository의 `deleteBy...`와 `save`를 이어 쓸 때 반드시 중간에 `flush()`가 필요하다.
- **전체 테스트 스위트가 처음엔 8시간 넘게 "멈춘 것처럼" 보였는데, 실제로는 512MB 기본 테스트 워커 힙에서 진짜 OOM이 난 뒤 죽지 못하고 GC만 계속 도는 상태였다.** 사용자가 "테스트가 안 끝나는 게 이상하다"고 지적해서 원인을 좁혔다 — 스레드 덤프를 떠보니 GC 스레드 8개가 누적 CPU 수만 초를 쓰고 있었고, 실행 로그를 확인하니 `RealInfraCouponControllerSessionTrackingTest` 실행 중 `OutOfMemoryError: Java heap space`가 실제로 발생했지만 JVM이 곧바로 죽지 않고 힙 부족 상태로 몇 시간이고 스핀했다. `build.gradle.kts`의 `test` 태스크가 `maxHeapSize`를 지정한 적이 없어 Gradle 기본값(512MB)을 쓰고 있었는데, 이 스위트는 `@SpringBootTest` 클래스가 41개나 되고 Spring의 테스트 컨텍스트 캐시가 서로 다른 설정의 컨텍스트를 여러 개 동시에 살려두다 보니(멈춘 워커의 스레드 덤프에서 `HikariPool`이 8개 넘게 동시에 살아있었다) 512MB로는 감당이 안 됐던 것. `test` 태스크에 `maxHeapSize = "3g"`를 추가해 근본적으로 고쳤다(32GB 메모리의 로컬 머신에서 충분히 안전한 값) — 이후 재실행은 6분 51초 만에 정상 종료됐다. 753MB짜리 `.hprof` 덤프 파일도 이 과정에서 남아 있었어서 커밋 전에 삭제했다.

## Phase 4 완료

30~39단계로 Phase 4(Team/B2B)의 로드맵 항목(조직/팀 관리, 팀 대시보드, Game Day, Private Scenario, SSO, RBAC, Audit Log, 온보딩 커리큘럼)이 전부 구현됐다.

## Phase 5 — Platform (docs/ROADMAP.md)

### Scenario Marketplace ✅ 완료 (2026-09-07)

Phase 5의 첫 항목. ROADMAP.md엔 "Scenario Marketplace(제작자 70%/플랫폼 30%)"라는 한 줄뿐이라 사용자에게 두 갈림길을 확인했다(AskUserQuestion): (1) 결제·수익배분 없이 무료 공개(권장, ADR-0003의 "MVP엔 실제 X 없음" 원칙과 동일 결) vs 결제 스텁 포함, (2) 로그인만 하면 즉시 등록(권장, 가장 작은 슬라이스) vs 관리자 승인 필요. 둘 다 권장안을 선택.

- [x] `V32` 마이그레이션 — `scenarios.creator_user_id`(nullable, `organization_id`와 독립적인 축) 추가
- [x] 신규 `MarketplaceScenarioService`/`MarketplaceController` — `POST /marketplace/scenarios`(누구나 등록, `organizationId=null`+`creatorUserId=본인`으로 34단계 `CustomScenarioService.create()`와 동일한 INITIAL+FOLLOWUP 구조 생성), `GET /marketplace/scenarios`(전체 목록), `GET /marketplace/scenarios/mine`(내 등록 목록)
- [x] **마켓플레이스 시나리오는 별도 저장소가 아니라 기존 공개 시나리오 풀(`organizationId == null`)에 합류** — `ScenarioController`의 `GET /scenarios`/`GET /scenarios/{id}`와 `SessionService.startSession`을 전혀 안 건드려도 마켓플레이스 시나리오가 자동으로 대시보드 목록에 섞여 나오고 아무나 세션을 시작할 수 있다. `ScenarioSummaryResponse`/`ScenarioDetailResponse`에 `creatorNickname`을 추가해 공식/커뮤니티 콘텐츠를 프론트에서 구분 표시 (ADR-0031)
- [x] `/marketplace/scenarios`는 `/organizations`와 같은 방식으로 전체 하위 경로가 인증 필요(단순 탐색 포함) — 마켓플레이스 시나리오 자체는 `/scenarios`에도 공개로 노출되니 발견성 손실은 없음
- [x] 프론트: 신규 `marketplace/page.tsx`(전체 목록+시작 버튼, 내가 등록한 목록, 등록 폼), 대시보드에 "마켓플레이스" 링크 추가
- [x] ADR-0031 작성

**완료 기준 충족**: 백엔드 전체 226개 테스트 전부 통과(신규 `MarketplaceControllerIntegrationTest` 3개 포함, 회귀 없음) — `./scripts/run-tests-isolated.sh`로 확인. curl로 미인증 등록 401 → 등록 성공(조직 없이) 시 `organizationId=null`+`creatorNickname` 확인 → `GET /marketplace/scenarios`와 `GET /scenarios`(공개 목록) 양쪽에 노출 확인 → 무관한 계정이 `POST /sessions`로 조직 멤버십 없이 세션 시작 확인 → `GET /marketplace/scenarios/mine`이 등록자/비등록자에게 다르게 보이는 것 확인. 실제 브라우저로 계정 A가 `/marketplace`에서 시나리오 등록 → "내가 등록한 시나리오"에 즉시 반영 → "시작" 클릭해 System Design Workspace로 정상 진입 → 대시보드의 기존 시나리오 목록(공식 7개 + 마켓플레이스 2개)에도 섞여 나오는 것과 "최근 진행"에 표시되는 것까지 확인.

**진행 중 발견한 결정 사항**:
- **`bootRun`이 아예 안 켜지는 회귀를 발견해 즉석에서 고쳤다.** 검증용 백엔드를 띄우려는데 `resolveMainClassName` 태스크가 "Unable to find a single main class"로 실패했다 — 원인은 이번 작업 이전에 다른 세션이 커밋한 `CleanupStaleKafkaTopics.kt`(Kafka stale 토픽 정리 도구)가 자기 own top-level `fun main()`을 가지고 있어서, Spring Boot의 메인 클래스 자동 탐지가 `BackendApplicationKt`와 그 파일 사이에서 헷갈리게 된 것 — 이 마켓플레이스 기능과는 무관하지만 `bootRun`/`bootJar`를 아예 못 쓰게 막는 회귀라 즉시 고쳤다. `build.gradle.kts`에 `springBoot { mainClass.set("com.sysdrill.backend.BackendApplicationKt") }`를 명시해 자동 탐지 자체를 없앴다. **교훈**: `src/main`에 커맨드라인 도구용 `main()`을 추가하는 커밋은 Spring Boot 프로젝트에서 `bootRun`을 깨뜨릴 수 있으니, 그런 도구를 추가할 땐 `springBoot.mainClass`를 같이 고정해야 한다.

### 실전형 인증 — SysDrill Certified Incident Responder ✅ 완료 (2026-09-07)

Phase 5 남은 두 항목(채용/역량 평가 상품화, 실전형 인증) 중 사용자가 실전형 인증을 선택했다(AskUserQuestion) — 새 외부 사용자 개념 없이 기존 SkillProfile/Session/Evaluation 기록만으로 자격을 판정하는 가장 작은 슬라이스. 승인된 선택지의 미리보기 문구("복수 도메인에서 COMPLETED 세션이 일정 점수 이상이면 GET /certifications로 자격 확인 → 공개 검증 페이지에서 누구나 확인 가능")가 설계를 그대로 결정했다.

- [x] **인증은 발급·저장되지 않고 매 요청마다 재계산되는 라이브 자격 판정** — 새 테이블 없음, ADR-0011("파생값은 저장하지 않고 읽을 때 계산") 원칙 그대로. `CertificationService.status()`가 `ReportService.generate()`와 동일한 "세션의 제출물들 → 활성 평가들의 totalScore → 평균" 계산을, 33단계 팀 대시보드/39단계 커리큘럼과 동일한 "배치 조회 후 join" 패턴으로 사용자 전체 이력에 걸쳐 수행
- [x] **인증 대상 도메인은 공식 Flyway 시드 시나리오(`organizationId == null && creatorUserId == null`)만** — 마켓플레이스(누구나 즉시 등록)나 조직 커스텀 시나리오는 제외
- [x] 판정 기준: 공식 도메인(현재 7개) 전부에서 COMPLETED 세션 평균 점수가 합격선(`sysdrill.certification.passing-score`, 기본 70점, 설정값이라 기준 변경에 마이그레이션 불필요) 이상인 것이 1회 이상 있어야 `certified=true`
- [x] `SubmissionRepository.findBySessionIdIn`/`EvaluationRepository.findBySubmissionIdInAndIsActiveTrue` 배치 조회 메서드 추가
- [x] 신규 `CertificationController` — `GET /certifications/me`(인증 필요), `GET /certifications/{userId}`(공개 검증 페이지, 비인증). `AuthWebConfig`엔 `/certifications/me`만 정확히 등록(와일드카드 없음)해서 `{userId}` 경로는 공개로 남김
- [x] 프론트: 신규 `certifications/page.tsx`(내 인증 현황 + 공개 검증 링크 안내), `certifications/[userId]/page.tsx`(비로그인도 접근 가능한 공개 검증 페이지), 대시보드에 "인증" 링크 추가
- [x] ADR-0032 작성

**완료 기준 충족**: 백엔드 전체 231개 테스트 전부 통과(신규 `CertificationControllerIntegrationTest` 5개 포함, 회귀 없음) — `./scripts/run-tests-isolated.sh`로 확인. curl로 완료 세션 없는 사용자는 전 도메인 미인증 확인 → 공식 도메인 하나를 낮은 점수(50점)로 완료해도 미인증 확인 → 같은 도메인을 합격선 이상(85점)으로 재완료하면 그 도메인만 `passed=true`(최고점 반영)로 바뀌고 나머지는 여전히 미완료, 전체는 `certified=false` 확인 → `GET /certifications/{userId}` 비인증 200, `GET /certifications/me` 미인증 401 확인. 실제 브라우저로 `/certifications`에서 도메인별 표와 공개 검증 링크 확인 → 로그아웃 상태(localStorage 비움)에서도 그 링크에 접속하면 동일한 정보가 보이는 것까지 확인.

**진행 중 발견한 결정 사항**: 없음 — 마켓플레이스 작업 때 이미 `bootRun`/테스트 인프라 문제를 해결해둔 덕분에 이번 단계는 처음부터 마찰 없이 진행됐다.

### 채용/역량 평가 상품화 ✅ 완료 (2026-09-08)

Phase 5 마지막 항목. 사용자에게 가장 큰 갈림길(후보자 계정 필요 여부)을 확인했다(AskUserQuestion) — **계정 필요**(가입/로그인 후 치르는 방식)를 선택, ADR-0020(게스트 플로우 완전 제거)과 같은 결.

- [x] `V33` 마이그레이션 — `organization_assessments`(`OrganizationInvitation`과 같은 이메일 바인딩 토큰 구조를 시나리오 하나로 스코프, 상태 컬럼 없음)
- [x] 신규 `OrganizationAssessmentService` — `create`(ADMIN 전용, 39단계 커리큘럼과 동일한 "공개 또는 자기 조직 시나리오만" 검증), `preview`(공개), `start`(이메일 일치 필요, `SessionService.startSession` 그대로 호출), `getReport`(ADMIN 전용, 세션 COMPLETED+`Report` 있어야 200)
- [x] `reporting/ReportResponses.kt` 신규 추출 — `ReportController`와 평가 리포트 엔드포인트가 동일한 jsonb 파싱 로직 공유
- [x] `OrganizationController`에 `POST/GET .../assessments`, `GET .../assessments/{id}/report`, `GET/POST /organizations/assessments/{token}`(미리보기/시작) 추가
- [x] **평가 상태는 저장하지 않고 `resultSessionId`+`Session.status`에서 매 요청 시 파생**(ADR-0011, ADR-0033), 관리자 리포트 접근은 `SessionAccessGuard`를 안 건드리고 완전히 별도 경로로(후보자는 조직 멤버가 아니라 Game Day의 `requireOwnerOrSpectator`가 원천적으로 안 맞음)
- [x] 프론트: 신규 `organizations/[orgId]/assessments/page.tsx`(ADMIN 전용 — 생성 폼, 목록, 완료 시 리포트 인라인 표시), `organizations/assessments/[token]/page.tsx`(비로그인도 접근 가능한 후보자 미리보기+시작), 조직 상세에 ADMIN 전용 "역량 평가 보기" 링크
- [x] ADR-0033 작성

**완료 기준 충족**: 백엔드 전체 235개 테스트 전부 통과(신규 `OrganizationAssessmentIntegrationTest` 4개 포함, 회귀 없음) — `./scripts/run-tests-isolated.sh`로 확인. curl로 평가 생성 → 미리보기 확인 → 다른 이메일 계정으로 시작 시도 404 → 올바른 이메일로 가입 후 시작 성공(201) → 재시작 시도 409 → 관리자가 미완료 리포트 조회 404 → 세션 완료+리포트 생성 후 재조회 200(요약 확인), 목록 상태가 `IN_PROGRESS`→`COMPLETED`로 파생되는 것 확인 → 다른 조직 관리자의 목록/리포트 조회 404 확인. 실제 브라우저로 관리자가 조직 상세 → "역량 평가"에서 후보자 이메일+시나리오로 평가 생성 → 발급 링크를 별도 탭(로그아웃 상태)에서 열어 미리보기 확인 → 정확한 이메일로 가입 → 다시 링크 접속해 "평가 시작하기" 클릭 → System Design Workspace 진입 확인 → 관리자 화면에서 "진행중" 상태로 갱신된 것 확인.

**진행 중 발견한 결정 사항**:
- **평가 미리보기 페이지를 초대 미리보기 패턴 그대로 복사했다가, 비로그인 후보자가 아예 아무것도 못 보는 버그를 실제 브라우저 검증 중 발견해 바로 고쳤다.** `AuthWebConfig`가 `/organizations/**` 전체를 인증 필수로 등록해서, `previewInvitation`처럼 미리보기도 인증을 요구하도록 그대로 베꼈다 — 그런데 초대 수신자는 이미 계정이 있을 가능성이 높은 기존 사용자라 프런트가 로그인 안 돼 있으면 바로 `/login`으로 보내버리는 반면(초대 수락 페이지의 기존 동작), 채용 후보자는 SysDrill을 처음 보는 사람이라 "뭘 요청받았는지"조차 로그인 전엔 볼 수 없으면 왜 가입해야 하는지 알 도리가 없다 — 승인된 계획 자체가 "비로그인도 접근 가능한 미리보기"였는데 구현이 그 계획을 어겼던 것. `GET /organizations/assessments/{token}` 하나만 `excludePathPatterns`로 빼서 고쳤다(`POST .../start`는 `@AuthenticatedUserId`가 필요해 계속 인증 필요, 단일 `*` 패턴이라 `/start`는 안 건드림). **교훈**: 기존 패턴을 복제할 때는 그 패턴이 전제하는 "누가 이 화면을 보는가"까지 같은지 확인해야 한다 — 겉모양이 같아도 대상 사용자가 다르면 인증 요구사항도 달라질 수 있다.
- Kotlin 블록 주석 안에 `` `/organizations/**` `` 처럼 백틱으로 감싼 Ant 와일드카드 패턴을 그대로 적어 넣으면 `/**`가 중첩 주석 시작으로 파싱돼 "Unclosed comment" 컴파일 에러가 난다(Kotlin은 블록 주석이 중첩 가능) — 이번 단계에서도 `AuthWebConfig.kt` 주석을 고치다 두 번 겪었다. **교훈**: KDoc 주석 안에서 `**`가 포함된 패턴 문자열을 설명할 땐 "wildcarded pattern"처럼 말로 풀어 쓰고, 리터럴 `/**`는 피한다.

## Phase 5 완료

30~39단계(Phase 4)에 이어, Scenario Marketplace·실전형 인증(SysDrill Certified Incident Responder)·채용/역량 평가 상품화로 Phase 5(Platform) 확정 로드맵 항목이 전부 구현됐다.

### Architecture Linter v1 — OpenAPI 스펙 기반 시나리오 자동 생성 ✅ 완료 (2026-09-08)

Phase 5 검증(로드맵 운영 원칙) 없이 사용자가 방향을 먼저 정하기로 했다("Phase 6 로드맵 정할까" → "1번(정적 분석) 방향으로 갈까" → "검증 원칙을 잠시 미루고 착수") — ROADMAP.md Phase 6을 새로 만들고 FUTURE_EXPLORATIONS.md §A를 승격했다(문서 커밋 별도). 착수 전 반드시 좁혀야 한다고 §A 자신이 못박은 두 문제를 사용자에게 확인해(AskUserQuestion) 정면으로 좁혔다: (1) 입력은 전체 리포지토리가 아니라 **OpenAPI 스펙 하나로 한정**, (2) 생성된 시나리오는 마켓플레이스처럼 공개하지 않고 **업로더 본인 전용 비공개**.

- [x] `build.gradle.kts`에 `io.swagger.parser.v3:swagger-parser` 추가, `V34` 마이그레이션 — `scenarios.visibility`(`"PUBLIC"`/`"PRIVATE"`, 기본값 PUBLIC) — `organizationId`/`creatorUserId`에 이은 세 번째 독립 축
- [x] 신규 `ArchitectureRiskScanner` — `RuleEvaluator`와 동일한 모양의 결정론적 규칙 엔진, OpenAPI 문서 하나만으로 판단 가능한 리스크 4종(에러 응답 누락/인증 요구사항 없음/페이지네이션 없는 배열 응답/요청 검증 스키마 없음) 탐지. LLM은 쓰지 않음 — 규칙이 찾은 사실을 문자열 템플릿으로 프롬프트에 꽂는다
- [x] 신규 `ArchitectureAnalysisService` — `CustomScenarioService.create()`/`MarketplaceScenarioService.publish()`와 동일한 ContentItem+Scenario(visibility=PRIVATE)+ScenarioVersion+ScenarioStep(INITIAL,FOLLOWUP) 생성 구조 재사용. **원본 OpenAPI 스펙은 저장하지 않음** — 파싱·스캔·생성을 요청 안에서 마치고 원문은 버린다(ADR-0034)
- [x] 기존 3곳에 `visibility=PUBLIC` 필터 추가(`ScenarioController.list/get`, `MarketplaceScenarioService.listAll`) — PRIVATE 유출 방지. `ScenarioController.get`은 이전엔 organizationId만 체크해서 PRIVATE 시나리오가 UUID만 알면 상세 조회되는 구멍이 있었음(같이 막음)
- [x] `SessionService.startSession`에 "PRIVATE면 creatorUserId만 시작 가능" 체크 추가
- [x] 프론트: 신규 `architecture-analysis/page.tsx`(파일 선택 또는 붙여넣기 → 분석 → 발견된 리스크 표시 → 즉시 시작, 내가 만든 시나리오 목록), 대시보드에 "정적 분석" 링크
- [x] ADR-0034 작성

**완료 기준 충족**: 백엔드 전체 241개 테스트 전부 통과(신규 `ArchitectureAnalysisControllerIntegrationTest` 3개 포함, 회귀 없음) — `./scripts/run-tests-isolated.sh`로 확인. curl로 미인증 401 확인 → 결함 있는 샘플 OpenAPI 스펙(에러응답/인증/페이지네이션/요청검증 전부 빠짐) 분석 → 5개 findings 전부 정확히 탐지 확인 → `GET /scenarios`/`GET /marketplace/scenarios`에 비노출, `GET /scenarios/{id}` 타 사용자 404 확인 → `GET /architecture-analysis/scenarios`가 업로더 전용인 것 확인 → 타 사용자 `POST /sessions` 404, 업로더 본인 201 확인. 실제 브라우저로 `/architecture-analysis`에서 스펙 붙여넣기 → 분석 → 5개 리스크 확인 → "이 시나리오로 시작" 클릭해 findings가 그대로 반영된 문제 설명으로 System Design Workspace 진입 확인 → 대시보드 시나리오 목록엔 안 뜨지만 "최근 진행"에는 정상 표시되는 것 확인(본인 세션 기록이라 당연히 보임 — 비공개인 건 "발견", "최근 진행"은 별개).

**진행 중 발견한 결정 사항**: 없음 — Phase 5 세 단계에서 이미 다진 패턴(ContentItem/Scenario/ScenarioVersion/ScenarioStep 생성, visibility류 독립 축 추가, AuthWebConfig 등록) 그대로 재사용해서 마찰 없이 진행됐다.

### 아키텍처 다이어그램 시각화 (Mermaid DSL) ✅ 완료 (2026-09-09)

사용자가 archify(외부 아키텍처 다이어그램 도구)를 예로 들며 시스템 전체에 다이어그램 렌더링이 전무하다고 지적했다. PRD.md가 MVP 시점에 뺐던 건 "자유형 마우스 드래그 에디터"였고, ARCHITECTURE.md는 React Flow를 "검토" 항목으로만 남겨뒀었다 — 둘 다 정면으로 재판단할 시점(Phase 5/6 이후)이라 사용자에게 AskUserQuestion으로 확인해(1) 텍스트 DSL(Mermaid)을 1차로 하고 React Flow는 이후 "그리면 DSL이 생성되는" 입력 레이어로 단계적으로 확장, (2) Design Workspace와 Architecture Linter 양쪽에 동시 적용을 선택받았다. ADR-0035 참고.

- [x] `frontend/package.json`에 `mermaid` 정확 버전 고정 추가 — 프론트엔드 첫 런타임 의존성
- [x] 신규 공용 컴포넌트 `components/MermaidDiagram.tsx` — 다크모드는 이 프로젝트의 유일한 메커니즘인 `prefers-color-scheme` 미디어쿼리를 직접 감지, 렌더 실패는 인라인 에러로만 처리하고 페이지를 절대 깨뜨리지 않음
- [x] Design Workspace: `answer` 자유 텍스트 안의 ` ```mermaid ``` ` 블록을 그대로 파싱해 실시간 미리보기(신규 `DiagramPreview.tsx`) — 제출되는 `rawText` 자체는 완전히 무변경이라 백엔드/평가 파이프라인 무변경. 템플릿 삽입 버튼 + 인라인 문법 치트시트 제공
- [x] 백엔드 신규 `ArchitectureDiagramGenerator.kt` — `ArchitectureRiskScanner`와 같은 순회로 `flowchart TD` Mermaid 문자열을 결정론적으로 생성, 리스크 심각도(HIGH/MEDIUM)별 노드 색상 스타일링. `ArchitectureAnalysisResponse`에 `diagram` 필드 추가
- [x] ADR-0035 작성, `docs/ARCHITECTURE.md`의 "React Flow 검토" 항목을 실제 결정으로 갱신

**완료 기준 충족**: 백엔드 전체 246개 테스트 전부 통과(기존 `ArchitectureAnalysisControllerIntegrationTest`에 다이어그램 어서션 확장, 회귀 없음) — `./scripts/run-tests-isolated.sh --rerun`으로 확인. 프론트 `npx tsc --noEmit`/`npm run build` 클린 통과. 실제 브라우저로 회원가입 → Architecture Linter에서 결함 스펙 분석 → 위험도별로 빨강/노랑 색칠된 다이어그램 확인(라이트/다크 모드 둘 다) → Design Workspace에서 세션 시작 → 템플릿 삽입 버튼으로 mermaid 블록 자동 삽입 → 실시간 렌더 확인 → 문법을 의도적으로 깨뜨려 인라인 에러(페이지 안 깨짐) 확인 → 다시 유효한 문법으로 되돌려 정상 복구 확인.

**진행 중 발견한 결정 사항**:
- **`mermaid.render(id, code)`는 같은 id를 가진 엘리먼트가 이미 문서에 있으면 실패한다** — 성공 시 반환된 SVG를 `innerHTML`로 그대로 삽입해두기 때문에, 같은 컴포넌트 인스턴스가 같은 id로 다시 렌더를 시도하면(예: 성공 → 성공 재시도) 그 자체와 충돌해 실패한다. **교훈**: 렌더 호출마다 매번 새 고유 id를 발급해야 한다(카운터 ref로 해결).
- **에러 상태에서 렌더 대상 `<div>`를 완전히 언마운트하면 영원히 복구 불가능한 상태에 빠진다** — `error && return <ErrorUI/>` 패턴으로 짜면 `containerRef.current`가 에러 상태 동안 `null`이 되고, 이후 코드를 고쳐서 렌더가 성공해도 `if (containerRef.current)` 체크가 항상 거짓이라 `setError(null)`이 절대 호출되지 않아 무한히 옛날 에러 메시지만 보여준다. 실제 브라우저 검증 중(정상→깨짐→정상 문법 되돌리기) 이 버그를 직접 발견했다. **교훈**: 렌더 대상 엘리먼트는 항상 마운트 상태를 유지하고(CSS `hidden`으로만 숨김), 에러 UI는 그 옆에 조건부로 얹는다 — React에서 "ref가 필요한 DOM 노드"를 상태에 따라 통째로 언마운트하는 패턴은 그 노드에 나중에 다시 쓰기 위한 명령형 접근(imperative access)이 필요한 경우 항상 이런 종류의 교착을 만들 수 있다.
- **`mermaid.render()`는 실패 시 자기 자신의 내부 스크래치 컨테이너(`#d<id>`)를 `document.body`에 직접 붙여둔 채 정리하지 않는다** — React 트리 밖에 있어서 컴포넌트가 인지하지 못하고, 페이지 하단에 mermaid 자체의 기본 에러 SVG가 별도로 떠 있는 걸 브라우저 검증 중 발견했다. `finally` 블록에서 매번 `document.getElementById('d'+renderId)?.remove()`로 정리해서 해결.

---

## 상용화 준비 ✅ 완료 (2026-09-09, 결제/IaC 제외)

[docs/COMMERCIALIZATION.md](docs/COMMERCIALIZATION.md)의 "코드로 구현 가능한 것" 전체를 auto mode로 구현했다. 결제/과금(가격 정책 미정)과 프로덕션 IaC(클라우드 프로바이더 미정)는 사람의 결정이 코드보다 먼저 필요해 이번엔 손대지 않았다 — 컨테이너 이미지(Dockerfile)까지만 만들었다.

- [x] `EmailSender` 인터페이스 + `SmtpEmailSender`/`LoggingEmailSender`(`MailConfig`가 `spring.mail.host` 설정 여부로 자동 선택) — 조직 초대·채용 평가 초대가 실제 발송을 시도하도록 연결
- [x] 비밀번호 재설정(`PasswordResetService`, 토큰 테이블, `/reset-password` 프론트), 이메일 인증(`EmailVerificationService`, `/verify-email` 프론트) — 둘 다 `OrganizationInvitation`과 같은 opaque-token 모양(ADR-0022) 재사용
- [x] Rate limiting(`RateLimiter`+`RateLimitInterceptor`, IP 기준), 로그인 실패 잠금(`LoginAttemptService`, 이메일 기준 5회/15분)
- [x] LLM 일일 사용량 쿼터(`LlmUsageGuard`, 사용자당 기본 50회/일, `SessionService.submit()`에서 체크)
- [x] `.github/workflows/ci.yml`(백엔드는 `docker compose up`으로 전체 인프라를 띄운 뒤 직접 테스트 — CI 러너는 매번 새 VM이라 `run-tests-isolated.sh`의 격리 장치가 필요 없음), `.github/dependabot.yml`
- [x] `backend/Dockerfile`, `frontend/Dockerfile`(Next.js standalone) — `docker build` 성공 확인
- [x] 프론트 Sentry 연동(`instrumentation.ts`/`instrumentation-client.ts`, DSN 미설정 시 비활성)
- [x] `npm audit`로 발견한 실제 취약점 수정(Next.js critical RCE 포함, 16.3.2 → 16.3.4)
- [x] `/terms`, `/privacy` 페이지(플레이스홀더 + 법률 검토 필요 배너) + 가입 필수 동의 체크박스(`User.termsAcceptedAt`)
- [x] 관리자 대시보드(`GET /admin/dashboard/stats` + `/admin` 프론트, 기존 `PlatformAccessGuard` 재사용)

**완료 기준 충족**: 백엔드 전체 257개 테스트 전부 통과(신규 약 15개 포함, 회귀 없음) — `./scripts/run-tests-isolated.sh --rerun`으로 확인. 프론트 `tsc`/`npm run lint`/`npm run build` 클린. 두 Dockerfile 모두 `docker build` 성공. 실제 브라우저로: 약관 동의 없이 가입 시도 시 차단 확인 → 동의 후 가입 성공, 로그에 인증 메일 발송 확인 → 인증 링크로 이메일 인증 완료 확인 → 비밀번호 재설정 요청 → 로그에서 링크 확인 → 재설정 → 새 비밀번호로 로그인 성공 확인 → `/terms`·`/privacy` 페이지 확인 → 일반 사용자로 `/admin` 접근 시 403, DB에서 role을 PLATFORM_ADMIN으로 바꾼 뒤 재접근 시 실제 통계 확인.

**진행 중 발견한 결정 사항**:
- **`sentry-spring-boot-starter-jakarta`는 이 프로젝트의 Spring Boot 4.1.1과 호환되지 않는다** — `RestClientCustomizer` 클래스가 없어 앱이 아예 기동 실패했다(`NoClassDefFoundError`). 최신 버전(8.16.0, 이 글 작성 시점 Maven Central 최신)까지도 Spring Boot 4를 지원하지 않는 것으로 보인다. 백엔드 Sentry 연동은 되돌렸고, 프론트엔드(`@sentry/nextjs`, Spring 무관)만 유지했다. **교훈**: 최신 메이저 버전의 프레임워크(Spring Boot 4는 매우 최근 릴리스)를 쓸 때는 서드파티 스타터의 버전 호환성을 먼저 확인해야 한다 — 실제로 앱을 띄워보기 전까지는 컴파일이 되더라도 런타임에서만 드러나는 비호환일 수 있다.
- **`src/test/resources/application.yml`을 새로 만들면 안 된다** — Gradle 테스트 클래스패스에 `src/main/resources/application.yml`과 동시에 존재하면 Spring Boot의 설정 병합이 예상과 다르게 동작해(datasource 등 필수 설정이 유실되어) 전체 테스트 컨텍스트가 깨진다(`DataSourceBeanCreationException`). 테스트 전용 값 오버라이드가 필요하면 `@DynamicPropertySource`를 개별 테스트 클래스에서 쓰거나, 프로덕션 기본값 자체를 테스트 스위트 전체 트래픽을 감당할 만큼 넉넉하게 잡는 편이 안전하다.
- **Redis 기반 rate limiter의 키에 테스트 격리 축이 없으면, 같은 Redis를 공유하는 전체 테스트 스위트 사이에서 상태가 새어나간다** — IP 기준 키(`sysdrill:ratelimit:/auth/login:<ip>`)는 모든 MockMvc 테스트가 동일한 "클라이언트 IP"를 쓰기 때문에, 한 테스트 클래스의 트래픽이 다른 클래스의 카운터에 영향을 준다. 사용자 ID 기준 키(LLM 쿼터)는 테스트마다 새 UUID 사용자를 만들어 자연스럽게 격리되지만, IP 기준은 그렇지 않다. **교훈**: 이런 카운터를 검증하는 테스트는 "정확히 N번째에 걸린다"가 아니라 "충분히 많이 시도하면 결국 걸린다"는 식으로, 공유 상태의 사전 오염에 강건하게 짜야 한다.
- **`@Bean` 메서드 이름이 겹치면 `@Primary`가 있어도 `BeanDefinitionOverrideException`이 난다** — 타입이 같아도 이름이 다르면 `@Primary`로 정상적으로 오버라이드되지만, 이름까지 같으면 Spring이 이를 허용하지 않는다(`FakeGoogleOAuthConfig`가 문제 없었던 건 진짜 구현체가 `@Component` 스캔으로 다른 이름의 빈이 됐기 때문). 테스트용 Fake 빈은 실제 `@Bean` 팩토리 메서드와 겹치지 않는 이름을 써야 한다.
- **`mermaid.render()`류의 "실패 시 자기 콜백이 두 번 불릴 수 있는" 문제와 비슷하게, `useEffect`가 React Strict Mode에서 두 번 실행되면 토큰을 소비하는 API 호출(비밀번호 재설정 확인, 이메일 인증)도 두 번 나갈 수 있다** — 첫 호출이 성공(204)해도 두 번째 호출이 실패(400, 토큰 이미 사용됨)하면서 그 실패가 나중에 도착해 성공 상태를 덮어쓸 수 있다. 실제 브라우저 검증 중 `/verify-email` 페이지에서 이 버그를 직접 발견해, `useRef` 기반 "이미 실행됨" 가드로 고쳤다. **교훈**: 부작용이 있고 재실행되면 안 되는 `useEffect`(특히 일회용 토큰을 소비하는 API 호출)는 항상 이 가드가 필요하다고 가정해야 한다.

---

## UI/UX 고도화 — Phase A (기초 공사) ✅ 완료 (2026-09-09)

사용자가 "절차나 인프라같은 상용제품화 보다는 이 프로젝트의 완성도를 높이는 방향으로 가자"며 방향을 틀었다. 실제로 앱을 띄워 확인한 근거를 바탕으로 [docs/UX_STRATEGY.md](docs/UX_STRATEGY.md)를 작성했고(4단계 전략), 그중 레버리지가 가장 큰 Phase A를 auto mode로 끝까지 구현했다.

- [x] 버그 4개 수정: `globals.css`의 하드코딩된 `Arial` 폰트 제거(Geist Sans 실제 적용), `layout.tsx` metadata를 "SysDrill"로 교체, `app/icon.svg` 신규(단순 SVG 모노그램), 헤더 wrap 버그는 아래 앱 셸로 구조적 해결
- [x] `frontend/src/components/ui/`에 원자 컴포넌트 7종 신설 — `Button`(primary/secondary/ghost/danger × md/sm), `Card`, `Badge`(neutral/success/warning/danger/accent), `Alert`(warning/danger), `Input`/`Textarea`, `LoadingState`, `EmptyState` — 이미 26개 페이지에서 반복되던 Tailwind 문자열을 그대로 뽑아낸 것이라 디자인 결정은 최소
- [x] `globals.css`에 `--accent`/`--accent-foreground` CSS 변수 도입(라이트: indigo `#4f46e5`, 다크: `#818cf8`) — 개발자 도구 톤의 절제된 단일 액센트
- [x] `components/AppHeader.tsx` 신규(로고, 데스크톱 가로 내비게이션, 모바일 햄버거 메뉴, 로그인 상태에 따라 분기) + `layout.tsx`에 통합, 26개 페이지 전부에서 자체 내비게이션/뒤로가기 블록 제거
- [x] 26개 페이지 전부 마이그레이션 — 카드/버튼/배지/로딩/빈 상태를 새 컴포넌트로 교체(로직·상태 관리는 무변경, JSX 스타일링만 치환)

**완료 기준 충족**: `npx tsc --noEmit`/`npm run lint`(0 errors)/`npm run build` 전부 클린(18개 라우트 정상 생성). 실제 브라우저로 로그인 → 대시보드(1440px에서 헤더 한 줄 유지, `getComputedStyle`로 폰트가 Geist인 것 확인) → `resize_window`로 375px 모바일 확인(햄버거 버튼으로 전환, 메뉴 항목이 세로 한 줄씩 정상 표시 — 이전엔 글자 단위로 쌓이던 버그) → 마켓플레이스/조직/인증 페이지 스크린샷으로 카드·버튼·배지 일관성 확인 → 조직 생성 → 멤버 초대까지 핵심 플로우 실제 실행해 회귀 없음 확인.

**진행 중 발견한 결정 사항**:
- **`app.icon.svg`를 만들어도 같은 디렉터리에 `favicon.ico`가 남아 있으면 브라우저는 여전히 `favicon.ico`를 쓴다** — Next.js의 파일 기반 메타데이터 컨벤션은 `favicon.ico`를 `icon.svg`보다 우선한다. create-next-app이 만든 기본 `src/app/favicon.ico`가 이번 세션 이전부터 계속 남아있었던 것— `getComputedStyle`/build 로그만으로는 안 드러나고 실제로 `document.querySelector('link[rel="icon"]')`을 찍어봐야 드러나는 종류의 버그였다. 파일을 삭제해 해결. **교훈**: Next.js 파일 컨벤션은 "새 파일을 추가"하는 것만으로 부족할 수 있다 — 같은 디렉터리의 기존 기본 파일이 우선순위상 여전히 이기고 있지 않은지 실제 렌더링된 `<link>` 태그로 확인해야 한다.
- **`organizations/[orgId]/page.tsx`의 초대 성공 메시지("이메일은 자동 발송되지 않습니다")가 상용화 라운드(이메일 발송 연동) 이후로 사실과 어긋나 있었다** — 마이그레이션 도중 우연히 발견해 "초대 이메일을 발송했습니다"로 정정. 스타일링 리팩터링 스코프 밖이지만 완성도(이 라운드의 목표) 안에 들고 리스크가 낮아 바로 고쳤다.
- 버튼 variant는 반복 사용 맥락에 따라 규칙을 세워 일관 적용했다: `primary`(액센트)는 화면/섹션당 가장 중요한 CTA 1개, `secondary`(테두리)는 목록 행 반복 액션, `ghost`(밑줄 텍스트)는 저강조 내비게이션형 액션, `danger`(빨강 밑줄)는 파괴적 액션 — 기존 코드에 이미 존재하던 색 구분(`text-red-600 underline` 등)을 grep으로 먼저 확인한 뒤 그대로 컴포넌트 variant에 대응시켰다.

Phase B(반응형 레이아웃), Phase C(핵심 루프 화면 리디자인), Phase D(로딩 스켈레톤/토스트/Monaco Editor)는 착수하지 않았다.

---

## UI/UX 리뉴얼 — `SysDrill_UIUX_Design_Plan.docx` 기반 (2026-09-09~)

사용자가 외부 디자인 기획서를 제시하고 화면 기획을 다시 하고 UI/UX를 리뉴얼해달라고 요청했다. AskUserQuestion으로 "IA까지 전면 리뉴얼"·"P0 전체(캔버스/모니터링 기능 포함)"를 확인했고, 두 Explore 에이전트로 기존 결정(ADR-0035, 3단계 세션 phase 모델, 단일 Build 챌린지)과의 충돌을 먼저 파악한 뒤 계획을 세웠다. 전체 계획은 승인된 plan 파일 참고. Phase A(라이트 테마)와 무관한 별도 트랙 — [docs/UX_STRATEGY.md](docs/UX_STRATEGY.md) 참고.

### Round 1 — 디자인 토큰 + 컴포넌트 + IA (Header/Home/Drills) ✅ 완료 (2026-09-09)

- [x] `globals.css` 다크 네이비 리브랜드 — `:root`를 단일 다크 팔레트로 교체(라이트/다크 분기 제거), `--accent`를 `#2f80ff`로, 상태 색상 3종(`--success`/`--warning`/`--danger`) 신설. 폰트는 `next/font/google`의 `Noto_Sans_KR`로 교체(Pretendard는 별도 패키지가 필요해 대체), `Geist_Mono`는 유지
- [x] `frontend/src/components/ui/` 7종(Button/Card/Badge/Alert/Input·Textarea/LoadingState/EmptyState) 전부 새 토큰으로 리브랜드, 신규 `MetricCard.tsx`(값/단위/변화율/상태/sparkline, 순수 SVG) 추가
- [x] 26개 페이지 전체에 남아있던 raw Tailwind 색상 클래스(`zinc-*`/`red-*`/`emerald-*` 등, `dark:` 짝이 있는 것과 없는 것 모두) 전부 새 토큰(`text-foreground-muted`/`border-border`/`bg-surface*`/`text-danger`/`text-success`)으로 sed 스윕 — 컴포넌트화되지 않고 남아있던 자리까지 다크 테마가 깨지지 않게 함
- [x] `AppHeader.tsx` — nav를 Home/Drills/Learning/Community로 교체, 헤더 검색(엔터 시 `/marketplace?q=...`로 이동), 알림 벨(정적 아이콘), 아바타 드롭다운(닉네임/로그아웃 — 텍스트 링크에서 교체)
- [x] `app/learning`, `app/community` 신규 — P2라 콘텐츠는 없지만 헤더 IA에 죽은 링크를 안 만들기 위한 최소 placeholder
- [x] Home(`app/dashboard/page.tsx`) 재설계 — Hero("Train. Break. Fix. Repeat."), System Design/Build/Incident 모드 카드, 실제 데이터 기반 통계 칩(시나리오/도메인 개수 — "100+" 같은 과장 없이 실제 개수만), Bridge Mode CTA, 약점 TOP3/점수 추이/최근 진행 3열 그리드. **계획 당시 "약점 TOP3는 백엔드가 없어 스코프 아웃"이라고 썼는데, 실제로는 `SkillProfile.weaknessesByDomain`이 이미 있어 그대로 유지했다** — 계획 문서의 그 판단은 틀렸던 것으로 정정
- [x] Drills(`app/marketplace/page.tsx`) 재설계 — 전체/System Design/Build/Incident 탭(System Design·Incident는 동일 시나리오 목록을 보여줌 — 가짜 필터 대신 정직한 표현), 난이도/카테고리 필터(실제 필드 기반), 검색(헤더 프리필 지원), Drill 타입 배지. `useSearchParams` 사용으로 인한 Next.js 빌드 요구사항 때문에 `Suspense` 경계 추가

**완료 기준 충족**: `npx tsc --noEmit`/`npm run lint`(0 errors)/`npm run build` 전부 클린(20개 라우트, `/learning`·`/community` 포함). 실제 브라우저로 신규 계정 가입 → Home(실시간 API로 시나리오/도메인 개수 확인) → Drills 탭 전환(Build 탭이 단일 rate-limiter 카드로 정확히 좁혀지는 것 확인) → 헤더 검색 프리필 확인 → 모바일(375px) 햄버거 메뉴에 새 IA 라벨 확인 → 시나리오 시작 클릭해 `/design/{id}`로 정상 진입까지 회귀 없음 확인.

**진행 중 발견한 결정 사항**:
- **로컬 백엔드가 사용자 본인의 다른 개발 세션에 의해 이미 실행 중이었다**(포트 8083, CORS는 `sysdrill.frontend-origin` 환경변수로 `http://localhost:3002`만 허용하도록 설정돼 있었음 — 기본값 3000이 아니었다). 처음엔 기존 관행대로 포트 3000에서 검증하려다 CORS 403으로 막혔고, 원인을 `CorsConfig.kt`/`application.yml`까지 추적해 사용자의 실제 프런트 포트가 3002라는 걸 확인한 뒤 `.claude/launch.json`에 `frontend-verify`(포트 3002) 설정을 별도로 추가해 검증했다. 기존 `frontend`(포트 3000) 설정은 건드리지 않음. **교훈**: 같은 저장소에 사용자가 이미 자기 방식대로 띄워둔 dev 서버가 있을 수 있다 — 포트/CORS가 예상과 다르면 설정 파일을 추적해서 실제 값을 확인하는 게, 기본값을 가정하고 계속 재시도하는 것보다 빠르다.
- Home 화면의 통계 칩에 문서 목업의 "100+ 시나리오" 같은 과장된 숫자를 그대로 쓰지 않고 실제 `scenarios.length`/도메인 개수로 대체했다 — 실제 데이터보다 부풀린 마케팅 카피를 UI에 박아넣지 않는다는 원칙.

### Round 2 — System Design Drill 다이어그램 캔버스 ✅ 완료 (2026-09-09)

- [x] ADR-0036 작성 — ADR-0035("다이어그램은 Mermaid 텍스트")를 뒤집는 게 아니라 0035 자신이 남겨둔 화해 경로("React Flow를 같은 Mermaid 텍스트로 직렬화되는 입력 방식으로 추가")를 그대로 실행한 것임을 기록. 캔버스가 답안 텍스트 이외의 새 필드/스키마를 만들지 않는다는 게 핵심 제약
- [x] `frontend/package.json`에 `@xyflow/react@12.11.6` 정확 버전 고정 추가(React 19 지원 확인 후 설치) — 프론트엔드 두 번째 런타임 의존성(`mermaid`에 이은)
- [x] 신규 `frontend/src/app/design/[sessionId]/DiagramCanvas.tsx` — 노드 팔레트 7종(Client/API Gateway/Service/DB/Cache/Queue/CDN, 각 Mermaid 도형 문법에 매핑), 클릭 배치·드래그 이동·핸들 드래그 연결·Delete 삭제, 라벨은 노드 안 인라인 `<input>`으로만 편집(별도 설정 폼 없음). 모든 변경이 `flowchart TD` Mermaid 텍스트로 재직렬화되어 부모의 답안 텍스트 속 `<!-- sysdrill-canvas:start/end -->` 마커 구간에 갱신됨
- [x] `frontend/src/lib/localSession.ts`에 `saveCanvasDraft`/`loadCanvasDraft` 추가 — 캔버스의 노드/좌표/엣지 그래프는 기존 답안 draft와 같은 티어(브라우저 localStorage만, 백엔드 무관)로 세션별 영속화. 답안 텍스트에서 Mermaid를 역파싱하는 대신(ADR-0036이 명시적으로 비용이 크다고 판단한 부분) 그래프 자체를 별도로 저장해 새로고침 후에도 캔버스가 복원되게 함
- [x] `design/[sessionId]/page.tsx`에 "캔버스"/"텍스트(Mermaid)" 토글 추가(기본값 캔버스) — 텍스트 모드는 기존 `DiagramPreview`+textarea 흐름 그대로 유지. 좌측 단계 네비게이션은 새 `StepNav` 컴포넌트로 구현하되 백엔드 phase 모델(INITIAL/FOLLOWUP/INCIDENT)은 무변경 — "요구사항 분석"/"초기 설계"는 같은 INITIAL 단계의 두 프레젠테이션 라벨일 뿐
- [x] Round 1에서 빠뜨렸던 `frontend/src/components/`(app/ 밖) 잔여 라이트 테마 색상 3개 파일(`BridgeProgress.tsx`, `PhaseTimer.tsx`, `MermaidDiagram.tsx`) 마저 다크 토큰으로 교체, `MermaidDiagram`의 `prefers-color-scheme` 기반 테마 분기를 제거하고 `theme: "dark"` 고정(앱 전체가 다크 단일 테마이므로)

**완료 기준 충족**: `npx tsc --noEmit`/`npm run lint`(0 errors)/`npm run build` 전부 클린. 실제 브라우저(포트 3002, 사용자 본인 백엔드 대상)로 새 System Design 세션 진입 → 캔버스에 Client/DB 노드 배치 → 답안 텍스트에 Mermaid 블록이 실시간 반영되는지 확인 → 노드 라벨 편집이 즉시 반영되는지 확인 → "텍스트" 모드 전환 시 동일 콘텐츠가 `DiagramPreview`로 정상 렌더되는지 확인 → 페이지 새로고침 후 캔버스 그래프가 복원되는지 확인 → 답안 제출 → 평가 진행 → 피드백 화면까지 기존과 동일하게 동작(백엔드 무변경 확인) → StepNav가 각 단계에서 올바르게 강조되는지 확인.

**진행 중 발견한 결정 사항**:
- **React Compiler(`react-hooks/purity`, `react-hooks/globals`)가 컴포넌트 함수 안에서 모듈 스코프 변수(`let placementCounter`)를 재할당하거나 `Date.now()`를 호출하는 것을 렌더 순수성 위반으로 막았다** — 실제로는 이벤트 핸들러(`onClick`) 안에서만 호출되는 코드라 런타임 버그는 아니었지만, 린터가 정적으로 "렌더 중 호출 가능성"을 배제하지 못해 에러로 잡았다. `placementCounter`는 `useRef`로, id 생성은 이미 이 코드베이스에 있던 `crypto.randomUUID()` 패턴(`design/[sessionId]/page.tsx`의 `handleSubmit`)으로 교체해 해결. **교훈**: 이 프로젝트의 린트 설정은 "이벤트 핸들러 안에서만 실행됨"이라는 논리적 보장을 신뢰하지 않는다 — 모듈 스코프 mutable 변수와 `Date.now()`/`Math.random()` 등은 컴포넌트 함수 몸체 어디에 있든 피해야 한다.
- **캔버스는 답안 텍스트 속 손으로 쓴 Mermaid 블록을 다시 캔버스로 역파싱하지 못한다**(ADR-0036에 명시) — "텍스트" 모드에서 수동 편집 후 "캔버스" 모드로 돌아가면 캔버스는 자신의 마지막 localStorage 그래프를 보여주고, 이후 캔버스를 조작하면 그 수동 편집을 덮어쓴다. 의도적으로 받아들인 단방향(캔버스→텍스트) 동기화의 한계 — 양방향 동기화는 Mermaid 파서가 필요해 ADR-0035가 원래 피하려던 비용을 다시 불러온다.

### Round 3 — Incident Drill 메트릭/로그/액션 패널 ✅ 완료 (2026-09-09)

- [x] `frontend/package.json`에 `recharts@3.10.1` 정확 버전 고정 추가(React 19 지원 확인 후 설치) — 세 번째 런타임 의존성
- [x] 백엔드 `SystemState.kt`에 `cpuUtilization`/`memoryUtilization` **computed property**(constructor 필드 아님) 추가 — 7개 rule-based + 2개 real-infra 엔진이 이미 만들어내는 부하 신호(`dbReadLoad`/`dbWriteLoad`/`connectionPoolUsage`/`queueLag`/`errorRate`) 중 최댓값으로 CPU를 유도하고, `p95LatencyMs` 기반 backpressure를 섞어 Memory를 유도 — 9곳의 기존 `SystemState(...)` 생성 호출부, 어느 것도 건드리지 않음(ADR-0011: 파생값은 equals/hashCode/copy에 참여하지 않음)
- [x] 신규 `frontend/src/components/ui/Gauge.tsx` — CPU/Memory 원형 게이지(순수 SVG `<circle>` stroke-dasharray). 문서는 CPU/Memory/Disk 3개를 보여주지만, 백엔드가 디스크 관련 지표를 전혀 모델링하지 않아 근거 없는 세 번째 숫자를 만들지 않고 2개만 구현(과장된 통계를 만들지 않는다는 이 라운드 전체의 원칙)
- [x] 신규 `frontend/src/app/design/[sessionId]/LogViewer.tsx` — 시간/레벨/서비스/메시지 + 검색/레벨 필터/자동스크롤. 백엔드 `GET .../simulation/timeline`(기존에 있었지만 미사용이던 엔드포인트)으로 초기 로그 히스토리를 시딩하고, 이후 실시간 상태 변화(WARN/ERROR 진입) 시점에만 이어붙임(매 폴링 틱마다 로그를 스팸하지 않음)
- [x] `WargameLive.tsx` 전면 재작성 — RPS/에러율 실시간 recharts 라인 차트(최근 40틱 링버퍼), 대응 액션을 문서의 개념 카테고리(스케일/캐시/트래픽/설정, 아이콘+배지)로 재분류(새 백엔드 액션 타입 없음), 전체를 Card/Button/Badge 다크 컴포넌트로 재스타일링. `MetricsPanel`(스냅샷 전용, replay 화면과 공유)과 `MetricsHistoryCharts`(실시간 전용, 링버퍼 필요)를 분리 — replay는 시간축 자체가 스크러버라 롤링 히스토리가 필요 없음

**완료 기준 충족**: `npx tsc --noEmit`/`npm run lint`(0 errors)/`npm run build` 프론트 전부 클린. 백엔드 `./gradlew compileKotlin` 클린, 시뮬레이션 관련 테스트 전부 통과, `./scripts/run-tests-isolated.sh` 전체 스위트 그린. 실제 브라우저(신규 격리 백엔드 인스턴스 대상)로 알림 도메인 인시던트 시작 → CPU/Memory 게이지·RPS/에러율 차트·로그가 실제 데이터로 렌더되는지 확인 → 대응 액션(컨슈머 증설) 적용 시 지표·로그가 실시간으로 갱신되는지 확인 → 모바일(375px)에서 지표 그리드가 넘치지 않는지 확인(아래 버그 참고) → 인시던트 회고 제출 → 평가 → 피드백까지 회귀 없음 확인 → 리플레이 화면에서 게이지 포함 스크러버가 정상 동작하는지 확인.

**진행 중 발견한 버그 3건과 수정**:
- **CPU 게이지가 "NaN%"로 나온 진짜 원인은 Jackson 직렬화가 아니라 별도의 응답 DTO였다.** `SimulationController`는 `SystemState`(도메인 모델)를 직접 반환하지 않고 `SystemStateResponse`(`SimulationDtos.kt`)로 명시적으로 매핑해서 반환하는데, 이 DTO에 새 필드 2개를 추가하는 걸 빠뜨렸다. 처음엔 "이 프로젝트가 Jackson 3(`tools.jackson.*`)를 쓰는데 Kotlin 모듈이 body의 computed property는 직렬화하지 않는 것 아닐까"라는 잘못된 가설을 세우고 `SystemState`를 constructor 파라미터+기본값 형태로 바꿨다가, curl로 직접 응답을 찍어봐도 여전히 필드가 없는 걸 확인하고서야 `javap`로 컴파일된 클래스를 뒤져 `SystemStateResponse`라는 별도 클래스의 존재를 발견했다. 진짜 원인을 고친 뒤에는 `SystemState`를 원래의(더 명확한) computed property 형태로 되돌렸다. **교훈**: "필드가 API 응답에 안 나온다"는 증상을 프레임워크 직렬화 세부사항 탓으로 성급하게 결론짓지 말고, 먼저 도메인 모델과 API 응답 사이에 별도 매핑 계층이 있는지부터 grep해야 한다 — 이 프로젝트는 실제로 `XxxResponse` DTO + `.from()` 팩토리 패턴을 여러 곳에 쓰고 있었다.
- **`GET .../simulation/timeline`이 500으로 죽는 진짜 버그를 라이브 검증 중 발견했다** — `awaitingStartChoice`(real-infra 옵트인 게이트)는 순수 클라이언트 상태라 페이지를 새로고침할 때마다 다시 뜨는데, 거기서 "인시던트 시작"을 다시 누르면 `SimulationService.startIncident`가 무조건 새 `INCIDENT_STARTED` 행을 추가하고 트레이트를 리셋했다. `getTimeline`의 rule-based 리플레이 경로는 "index 0만 INCIDENT_STARTED일 것"이라 가정하고 나머지를 전부 `SimulationActionType.valueOf()`로 파싱했는데, 두 번째 `INCIDENT_STARTED` 행을 만나면 그 enum에 없는 값이라 예외가 났다. `startIncident`에 멱등성 가드(이미 활성 인시던트가 있으면 그냥 현재 상태를 반환)를 추가하고, `getTimeline`도 위치가 아니라 값으로 `INCIDENT_STARTED`를 걸러내도록 방어적으로 고쳤다. **교훈**: 프런트 상태가 "매번 다시 물어보는" 게이트를 그리면, 백엔드도 "같은 시작 액션이 여러 번 올 수 있다"고 가정해야 한다 — 이건 Round 3 UI 변경과 무관한, 세션이 있었지만(다른 팀원이 만든 것이 아니라 이번 라이브 검증 과정에서 우연히 밟은) 이번에 처음 발견된 기존 버그였다.
- **Incident 지표 카드가 모바일(375px)에서 오른쪽으로 넘쳤다** — 게이지 2개 + 지표 그리드를 `flex flex-wrap`으로 나란히 두면, flex 자식은 기본적으로 `min-width: auto`라 내용물의 intrinsic width보다 좁아지지 않는다. `flex-col sm:flex-row` + 지표 그리드에 `min-w-0`을 추가해 모바일에서는 세로로 쌓이게 고쳤다.

### Round 4 — Build Drill 코드 에디터 ✅ 완료 (2026-09-09)

문서 10장 우선순위표의 P0(Round 1~3)가 전부 끝난 뒤, 사용자에게 "Round 4"의 구체적 범위를 물어(AskUserQuestion — Build Drill 코드 에디터 vs 결과/역량 프로필 화면 vs 다른 작업) P1 항목 중 "Build Drill 코드 에디터"로 확인받았다.

- [x] `frontend/package.json`에 `@uiw/react-codemirror`/`@codemirror/lang-python`/`@codemirror/theme-one-dark` 정확 버전 고정 추가 — CodeMirror 6 기반, Monaco보다 가벼워 "Rate Limiter 챌린지 하나의 Python 텍스트 입력"이라는 실제 스코프에 맞는 선택
- [x] `app/bridge/page.tsx`의 평범한 `<textarea>`를 syntax highlighting + 줄 번호가 있는 `CodeMirror` 컴포넌트로 교체 — `sourceCode`/`handleSourceChange`(localStorage draft 저장)/제출 로직은 전부 무변경, 에디터 컴포넌트만 교체

문서 §5.4가 그리는 Build Drill의 전체 그림(좌측 가이드/과제/테스트/힌트 탭, 여러 챌린지, Cache/Circuit Breaker/Idempotency/Message Consumer 등)은 이번 스코프가 아니다 — 사용자가 고른 범위는 "코드 에디터"였고, 실제 데이터 모델도 여전히 단일 rate-limiter 챌린지뿐이라(Round 1에서 이미 확인한 제약) 새 챌린지나 탭 구조를 지어내지 않았다.

**완료 기준 충족**: `npx tsc --noEmit`/`npm run lint`(0 errors)/`npm run build` 전부 클린. 실제 브라우저로 Python 문법 강조·줄 번호 렌더 확인 → 에디터에 직접 타이핑 → localStorage draft에 반영되는지 확인 → 새로고침 후 draft가 에디터에 복원되는지 확인 → 모바일(375px)에서 긴 코드 줄이 페이지 자체가 아니라 에디터 내부에서만 가로 스크롤되는지(`document.body.scrollWidth === window.innerWidth`) 확인.

**진행 중 발견한 결정 사항**: 없음 — 기존 컴포넌트(Card/Button 등)와 `handleSourceChange`/draft 로직을 그대로 재사용해서 마찰 없이 진행됐다.

### Round 5 — 결과/AI 피드백/역량 프로필 ✅ 완료 (2026-09-09)

Round 4에서 확인한 남은 P1 항목("결과/AI 피드백/역량 프로필")을 사용자에게 다시 확인받고 진행했다. 조사 결과 두 화면 모두 **새 백엔드 작업 없이** 이미 존재하는 엔드포인트만으로 채울 수 있음을 확인 — `report/[sessionId]`는 `timelineFeedback[].submissionId`로 기존 `getFeedback()`을 호출하면 되고, `/profile`은 대시보드·인증 페이지가 이미 각각 쓰던 `getSkillProfile()`/`getMyCertification()`/`getUserSessions()`를 한 화면에 모으면 된다.

- [x] `design/[sessionId]/page.tsx`의 로컬 `FeedbackView`/`FeedbackList`를 공용 `frontend/src/components/FeedbackDetail.tsx`로 추출(동작 무변경) — 리포트 화면과 세션 진행 중 화면이 동일한 피드백 렌더링을 공유
- [x] `report/[sessionId]/page.tsx` 재설계 — 마지막 단계 점수를 헤드라인 원형 게이지(Round 3 `Gauge` 재사용, 점수 구간별 상태색)로 표시, 각 단계마다 `getFeedback(submissionId)`를 병렬 호출해 `FeedbackDetail`로 전체 피드백(루브릭 점수/잘한 점/놓친 점/실무 리스크/꼬리질문/권장 변경사항) 노출, `getSkillProfile()`+`listScenarios()`로 "다음 추천 Drill" CTA 추가(대시보드와 동일한 추천 로직)
- [x] 신규 `frontend/src/app/profile/page.tsx` — 진행률(인증 도메인 통과 수 + 진행바, 인증 페이지 패턴 재사용), 역량 프로필(`recharts` `RadarChart`로 도메인별 최고 점수 시각화 — 세 번째 recharts 차트 유형), 점수 추이(대시보드 막대 그래프 재사용), 보완 영역(대시보드의 "약점 TOP3"를 6개까지 확장, `riskLabel` 재사용), 배지(인증 페이지 패턴), 추천 학습 경로(리포트와 동일 로직), 기록(전체 세션 목록)
- [x] `AppHeader.tsx`의 아바타 드롭다운(데스크톱)과 모바일 메뉴 양쪽에 "프로필" 링크(`/profile`) 추가

**완료 기준 충족**: `npx tsc --noEmit`/`npm run lint`(0 errors)/`npm run build` 전부 클린(21개 라우트, `/profile` 포함). 실제 브라우저로 인시던트 세션을 실제로 완료 처리(`다음 단계로` 클릭까지)한 뒤 리포트 페이지에서 헤드라인 게이지·3단계 전체 피드백·추천 Drill CTA(클릭 시 실제 세션 시작까지) 확인 → `/profile`에서 진행률·레이더 차트·점수 추이·보완 영역·기록이 전부 실데이터로 렌더되는지 확인 → 모바일(375px)에서 두 화면 모두 오버플로 없는지 확인.

**진행 중 발견한 버그 1건과 수정**: 레이더 차트가 모바일(375px)에서 긴 도메인 라벨("대규모 상품 조회", "주문/결제" 등)이 잘려 보였다(페이지 자체 오버플로는 아니고 차트 SVG 안에서 라벨이 잘림). `RadarChart`에 `outerRadius="60%"` + `margin`을 넉넉히 줘서 해결. **교훈**: recharts의 극좌표 차트(Radar/Pie)는 기본 `outerRadius`가 라벨 공간을 고려하지 않으므로, 한국어처럼 라벨이 긴 데이터를 쓸 땐 처음부터 outerRadius를 줄여 여백을 확보하는 게 안전하다.

### Round 6 — P2: Bridge Mode 점검 + Learning/Community 정적 콘텐츠 ✅ 완료 (2026-09-09)

P2 진행을 요청받고, 착수 전에 두 항목의 성격이 P0/P1과 근본적으로 다르다는 걸 먼저 설명했다: Bridge Mode "재통합"은 문서가 전제하는(세 Drill이 원래 따로 있었다는) 상황 자체가 이 앱에는 없고(ADR-0009 — Bridge Mode는 처음부터 Session의 nullable FK로 설계된 통합 흐름), Learning/Community는 콘텐츠 모델이 전혀 없어 P0~P1처럼 "기존 엔드포인트 재사용"이 불가능하다. 사용자에게 Learning/Community 범위를 확인(AskUserQuestion) — 정적 콘텐츠로 최소 구현.

- [x] **Bridge Mode 점검**: Home CTA(Round 1)·`/bridge` 코드 에디터(Round 4)·`BridgeProgress` 스테퍼(Design/Report 화면, 기존)가 이미 Build→Design→Wargame→Report 전 구간에 일관되게 붙어 있음을 확인. 실제로 손볼 통합 공백이 없어 코드 변경 없음
- [x] `frontend/src/lib/designGuidance.ts` 신규 — `design/[sessionId]/page.tsx`에 로컬 상수로 있던 도메인별 설계 가이드·인시던트 회고 가이드를 공용 모듈로 추출(동작 무변경), `DOMAIN_TITLES` 매핑 추가
- [x] `frontend/src/lib/riskLabels.ts`에 `riskDescription()`/`RISK_KEYS` 추가 — RuleEvaluator의 13개 riskKey 각각에 대한 설명(AI 피드백의 "놓친 점"과 동일 어휘)
- [x] `app/learning/page.tsx` — "곧 제공됩니다" placeholder를 실제 콘텐츠로 교체: 도메인별 설계 가이드 7종 + 장애 대응 회고 가이드(모두 `designGuidance.ts` 재사용, 세션 중 보여주는 것과 동일 문구) + 개념 레퍼런스 13종(`riskLabels.ts` 재사용)
- [x] `app/community/page.tsx` — placeholder를 GitHub Issues 링크로 교체. Discussions는 이 저장소에서 꺼져 있음을 `gh repo view`로 직접 확인한 뒤, 실제로 접근 가능한 Issues로 안내(존재하지 않거나 비활성 상태인 채널을 안내하지 않는다는 원칙)

**완료 기준 충족**: `npx tsc --noEmit`/`npm run lint`(0 errors)/`npm run build` 전부 클린. 실제 브라우저로 `/learning`에서 7개 도메인 가이드+회고 가이드+13개 개념 레퍼런스가 실제 텍스트로 렌더되는지 확인 → `/community`의 Issues 링크가 정확한 URL(`github.com/polynomeer/sys-drill/issues`)로 새 탭 여는지 확인 → 두 페이지 모바일(375px) 오버플로 없음 확인 → `design/[sessionId]/page.tsx`가 공용 모듈로 리팩터링된 후에도 세션 중 가이드 문구가 그대로 나오는지(회귀 없음) 확인.

**진행 중 발견한 결정 사항**: `react/no-unescaped-entities` 린트 에러 — JSX 텍스트 안에 ASCII 큰따옴표(`"놓친 점"`)를 그대로 쓰면 걸린다. `&quot;` 같은 HTML 엔티티 대신 한국어 조판에 자연스러운 유니코드 곡선따옴표(" ")로 바꿔 해결 — 엔티티보다 가독성이 낫고 린트 규칙도 통과한다.

---

## Drills 고도화 — Architecture Canvas ↔ Simulation 연동 (docs/DRILLS_SIMULATION_VISION.md 기반, 2026-09-09~)

`docs/archive`에 추가된 두 신규 문서를 종합해 [docs/DRILLS_SIMULATION_VISION.md](docs/DRILLS_SIMULATION_VISION.md)를 작성했다. 사용자가 "Architecture Canvas를 Simulation Engine과 연결된 실행 가능한 모델로 전환"하기로 결정했고, 이를 [ADR-0037](docs/adr/0037-architecture-canvas-becomes-the-simulation-topology-source-of-truth.md)로 기록(ADR-0036 supersede)했다.

### Slice 1 — 기존 DesignTraits 매핑 ✅ 완료 (2026-09-09)

두 가지 구현 규모(① 기존 `DesignTraits`에 캔버스 노드 config를 매핑 vs ② 완전 자유형 `SystemTopology` 신규 엔터티) 중 사용자에게 확인받아 ①로 진행했다. 조사 중 `DesignTraits.kt`가 이미 도메인별 노드급 운영 변수(`dbPoolSize`/`readReplicaCount`/`cacheTtlSeconds`/`consumerCount`/`podReplicas`/`chunkSize`/`holdTimeoutSeconds`/`dispatcherWorkers`)를 갖고 있다는 걸 발견 — 문제는 세션 시작 시 항상 하드코딩된 기본값으로만 초기화되고(`SimulationService.kt` `!realInfra -> DesignTraits()`) 설계 단계에서 사용자가 무엇을 그리든 시뮬레이션에 전혀 반영되지 않았다는 것.

- [x] `SimulationDtos.kt`에 `StartIncidentRequest(val traits: DesignTraits = DesignTraits())` 추가 — Kotlin data class 기본값 덕분에 캔버스가 일부 필드만 보내도 나머지는 자동으로 기본값 유지
- [x] `SimulationController.kt`의 `POST .../simulation/incident`가 선택적 요청 바디를 받도록 확장
- [x] `SimulationService.kt`의 `startIncident`에 `initialTraits` 파라미터 추가, `!realInfra` 분기에서만 사용(real-infra 분기의 인프라 프로비저닝 최소값은 그대로 보호)
- [x] `frontend/src/lib/api.ts`의 `startIncident`가 3번째 인자로 `traits`를 받아 POST 바디로 전송
- [x] `DiagramCanvas.tsx`에 `NODE_TRAIT_CONFIG`(7개 도메인 × 관련 노드 종류 1개씩, `DesignTraits.kt` 필드와 1:1 대응) 추가, 노드에 숫자 입력 렌더링, `onTraitsChange` 콜백으로 부모에 전파. boolean 토글류 traits는 캔버스에 노출하지 않음(INCIDENT 대응 액션의 결과값이라는 의미가 강해 설계 단계 초기값으로는 어색함)
- [x] `design/[sessionId]/page.tsx`/`WargameLive.tsx`에 `canvasTraits`/`initialTraits` 배선 — `RuleBasedSimulationEngine` 자체는 전혀 바꾸지 않음(입력이 어디서 오는지만 바뀜)

**완료 기준 충족**: `npx tsc --noEmit`/`npm run lint`(0 errors)/`npm run build` 전부 클린. 백엔드 `./gradlew compileKotlin`/`compileTestKotlin` 클린, `./scripts/run-tests-isolated.sh --tests "com.sysdrill.backend.simulation.*"` 통과. 실 브라우저(격리 백엔드, port 8083)로 coupon 세션 두 개를 비교: 캔버스에서 DB 노드의 `dbPoolSize`를 300(기본 50)으로 설정한 세션은 인시던트 시작 응답이 `dbWriteLoad: 0.3 / connectionPoolUsage: 0.3 / availability: 0.98`인 반면, 기본값 그대로인 세션은 `dbWriteLoad: 1.8 / connectionPoolUsage: 1.0 / availability: 0.7`로 — 캔버스 설정이 실제로 시뮬레이션 결과를 바꾸는 걸 확인. 모바일(375px)에서 노드 안 숫자 입력이 오버플로 없이 렌더되는 것도 확인.

**진행 중 발견한 결정 사항**: 새 ADR은 쓰지 않았다 — "작은 슬라이스부터"라는 시퀀싱 선택은 되돌리기 쉬운 구현 판단이라 CLAUDE.md의 ADR 3조건을 모두 만족하지 않고, ADR-0037 자체가 이미 이 질문을 작업계획에 넘긴다고 명시했다.

**진행 중 발견한 버그와 수정**: 사용자가 실제 브라우저에서 `DiagramCanvas.tsx`에 노드를 추가할 때 "Cannot update a component (DesignWorkspacePage) while rendering a different component (DiagramCanvas)" 콘솔 에러를 보고했다. 원인은 `commit`(부모의 `setAnswer`/`setCanvasTraits`까지 이어지는 부수효과)이 `setNodes`의 함수형 업데이터 안에서 호출되고 있었다는 것 — React Flow가 새로 추가된 노드를 측정하며 발생시키는 "dimensions" 자동 변경이 `onNodesChange`를 예상보다 이른 타이밍(다른 컴포넌트의 렌더 도중)에 동기 호출해, 업데이터 안에서의 부수효과 호출이 안전하지 않았다. `nodes`/`edges` state 업데이트는 순수하게 유지하고, `commit`은 `useEffect`(의존성 `[nodes, edges]`)로 옮겨 렌더 이후에만 실행되도록 고쳤다. 이 리팩터링 도중 한 번 더 자기 자신을 물었다: `commit`을 effect의 의존성 배열에 그대로 넣었더니 `onMermaidChange`/`onTraitsChange`가 부모(`page.tsx`)에서 매 렌더마다 새로 만들어지는 일반 함수라 `commit`(`useCallback`)의 참조가 매번 바뀌어 effect가 무한히 재실행되는 "Maximum update depth exceeded" 루프가 발생했다 — `commit`을 ref로 참조해(`commitRef`, 별도의 무의존성 `useEffect`로 매 렌더 후 갱신) effect의 실제 의존성에서 빼는 것으로 해결했다(ref를 렌더 중에 직접 쓰는 것도 이 프로젝트의 React Compiler 순수성 규칙에 걸려 별도 effect가 필요했다). **검증 중 발견한 한계**: 이 세션에서 쓰는 Claude Browser 자동화 창은 사용자 화면에 실제로 표시되지 않는 상태(`document.visibilityState === "hidden"`, 컨테이너 실측 0×0)로 떠 있어, React Flow의 노드 크기 측정(ResizeObserver)이 진짜로 수렴 불가능한 0×0 컨테이너에서 영원히 진동하는 루프를 만들어낸다 — 이 세션 이전(HEAD) 커밋의 손대지 않은 원본 코드로 되돌려도 동일하게 재현되는 것으로 확인해, 이번 수정과 무관한 테스트 환경의 한계임을 검증했다. 실제 사용자가 보고한 증상(반복 없는 단발성 경고)과 일치하며, 이 수정 자체는 tsc/lint/build 전부 클린 상태로 완료됐지만 실제 화면에서의 최종 확인은 사용자에게 요청해야 한다.

**다음 슬라이스 후보**(착수 전 결정 필요): `SystemTopology` 신규 엔터티 기반 완전 자유형 노드별 상태 — [docs/DRILLS_SIMULATION_VISION.md](docs/DRILLS_SIMULATION_VISION.md) §5.3, §6 참고.

### Phase 3-C — Postmortem 집계 (전체/도메인별 MTTD·MTTR) ✅ 완료 (2026-09-10)

`docs/DRILLS_SIMULATION_VISION.md` §6의 Phase 3 확장 세 항목(3-A/3-B/3-C) 중 조사 결과 3-C가 가장 작은 범위임을 확인하고(세션별 MTTD/MTTR 계산·Postmortem 작성 UI는 이미 존재 — 없는 건 세션을 가로지르는 집계뿐) 사용자가 이를 선택했다.

- [x] `PostmortemService.kt` — MTTD/MTTR 계산을 `mttdMttr()`로 추출해 기존 `get()`과 새 `getSummary()` 양쪽에서 재사용. `getSummary(userId)`는 `sessionRepository.findByUserIdOrderByStartedAtDesc(userId)`로 사용자의 전체 세션을 가져와 인시던트가 실제로 시작된 것만 골라 전체/도메인별 평균 + 추이를 계산 — 전부 read-time 재계산(ADR-0011 계보), 새로 영속화하는 것 없음
- [x] 추이는 `identity/SkillProfileService.kt`의 `trendDirection()`을 재사용 — 점수(클수록 좋음) 전제인 함수를 초 단위 값을 음수로 뒤집어 넘겨서 "짧아질수록 개선"으로 재해석
- [x] `PostmortemDtos.kt`에 `PostmortemSummaryResponse`/`PostmortemDomainSummary` 추가, `PostmortemController.kt`에 `identity/SkillProfileController.kt`와 동일한 평평한 스타일로 `GET /postmortem-summary`(`PostmortemSummaryController`) 추가
- [x] `frontend/src/lib/api.ts`에 `getPostmortemSummary()` + 타입 추가, `profile/page.tsx`에 "장애 대응 통계" 카드(평균 MTTD/MTTR + 도메인별 목록) 추가 — 점수 추이용 `TREND_DIRECTION_LABELS`를 그대로 재사용하지 않고 별도 `TIME_TREND_LABELS`를 만듦("▲ 상승"이 시간 지표에서는 의미가 거꾸로라)

**완료 기준 충족**: `npx tsc --noEmit`/`npm run lint`(0 errors)/`npm run build` 전부 클린. 백엔드 `./gradlew compileKotlin`/`compileTestKotlin` 클린, `PostmortemControllerIntegrationTest`에 추가한 신규 테스트 2건(도메인 2개 걸친 집계, 인시던트 없는 사용자의 all-zero 케이스) 포함 전체 스위트(`./scripts/run-tests-isolated.sh`) 통과. 실 브라우저(격리 백엔드 port 8083, `next start` 프로덕션 프리뷰 — `next dev`는 사용자의 기존 dev 서버와 프로젝트 락 파일이 충돌해 사용 불가)로 coupon 세션 하나를 인시던트까지 진행한 뒤 `/profile`에서 "장애 대응 통계" 카드가 실데이터로 렌더되는지 확인, 콘솔 에러 없음 확인, 모바일(375px) 오버플로 없음 확인.

**진행 중 발견한 버그와 수정**: 새 `GET /postmortem-summary`가 항상 409(`IllegalStateException: @AuthenticatedUserId used on a path AuthInterceptor isn't registered for`)로 실패했다 — `AuthWebConfig.kt`의 `addInterceptors`가 인증이 필요한 경로를 화이트리스트 방식으로 등록하는데, 새 평평한 경로(`/skill-profile`과 같은 패턴)를 거기 추가하는 걸 빠뜨렸다. `/postmortem-summary`를 `/skill-profile` 옆에 추가해 해결. **교훈**: 이 프로젝트에서 새 최상위 인증 필요 엔드포인트를 추가할 때는 컨트롤러/서비스뿐 아니라 `AuthWebConfig.kt`의 `addPathPatterns` 목록도 함께 확인해야 한다 — 컴파일도 통과하고 401(무인증)도 아닌 409로 실패해서 처음엔 내 집계 로직 자체의 버그로 오인했다.

### Phase 3-A — 로그 심각도 계산을 백엔드로 이전 ✅ 완료 (2026-09-10)

`docs/DRILLS_SIMULATION_VISION.md` §6의 3-A는 원래 "OTel 도입과 자연스럽게 묶임"이라고 썼지만, 실제로 OTel/Jaeger는 coupon/notification 실전 인프라 파일럿 2개 도메인에만 의미가 있다는 걸 조사로 확인했다(나머지 5개는 순수 규칙 기반이라 추적할 실제 실행이 없음). 사용자에게 두 범위(① 로그 심각도만 백엔드 이전 vs ② coupon/notification만 OTel 실제 확장)를 확인받아 ①로 진행했다.

- [x] `SystemState.kt`에 `level: String` computed property 추가 — `WargameLive.tsx`의 `deriveLevel()`이 클라이언트에서 하던 것과 정확히 같은 `cpuUtilization` 기반 0.6/0.95 밴딩(`frontend/src/lib/metrics.ts`의 `utilizationStatus()`와 동일 임계값, 두 런타임 간 공유 소스가 없어 어느 한쪽이 바뀌면 다른 쪽도 맞춰야 한다는 주석 남김)
- [x] `SimulationDtos.kt`의 `SystemStateResponse`에 `level` 필드 추가 — `TimelineStepResponse`가 이미 `SystemStateResponse.from(...)`을 재사용하므로 타임라인 스텝에도 자동으로 흘러들어감
- [x] `WargameLive.tsx`의 `deriveLevel()` 함수 삭제, 6곳의 호출부를 전부 `state.level`로 교체(같은 파일의 `utilizationStatus()`는 Gauge 색상용으로 남겨둠 — 로그 심각도와 무관한 별개 용도)
- [x] `frontend/src/lib/api.ts`의 `SystemState` 인터페이스에 `level: "INFO"|"WARN"|"ERROR"` 추가

**완료 기준 충족**: `npx tsc --noEmit`/`npm run lint`(0 errors)/`npm run build` 전부 클린. 백엔드 `./gradlew compileKotlin`/`compileTestKotlin` 클린 + 전체 스위트(`./scripts/run-tests-isolated.sh`) 통과(`SystemStateResponse`를 직접 생성자 호출하는 테스트가 없어 필드 추가로 깨진 곳 없음). curl로 인시던트 시작/타임라인 응답에 `level` 필드가 실제로 오는지 확인(`cpuUtilization: 0.98` → `"ERROR"`) → 실 브라우저(격리 백엔드 port 8083)로 세션을 인시던트까지 진행 후 로그 패널의 첫 줄이 백엔드가 보낸 `ERROR`로 렌더되는지 확인 → Rate Limit 강화 액션 적용 후 CPU가 90%로 내려가며 새 로그 줄이 `WARN`으로 바뀌는지 확인(0.6~0.95 밴드 경계 동작 확인) → 콘솔 에러 없음, 모바일(375px) 오버플로 없음 확인.

**진행 중 발견한 결정 사항**: 새 ADR은 쓰지 않았다 — computed property 추가 + DTO 필드 추가 + 클라이언트 호출부 교체는 전부 되돌리기 쉬운 리팩터라 CLAUDE.md의 ADR 3조건을 만족하지 않는다.

### Phase 3-B — Traffic Lab: coupon 실전 인프라 부하 설정 노출 ✅ 완료 (2026-09-10)

`docs/DRILLS_SIMULATION_VISION.md` §6의 마지막 항목. 원안은 Traffic Lab(RPS 패턴/read-write ratio/hot-key) + Chaos Lab(다중 장애 타입)을 전부 새로 만들어야 하는 가장 큰 범위였지만, 조사 결과 `CouponLoadRunner.run(sessionId, rps, durationSeconds)`가 이미 RPS/지속시간을 파라미터로 받는 제네릭 함수이고 아무도 그 값을 바꿔 호출하지 않는다는 걸 확인했다. 사용자에게 Traffic vs Chaos 중 선택받아 Traffic Lab(coupon만, RPS/지속시간을 사용자가 직접 설정)으로 진행했다. `NotificationLoadRunner`는 rate/duration 파라미터가 아예 없어 이번 슬라이스에서 제외.

- [x] `SimulationSessionState.kt`에 `loadRpsOverride`/`loadDurationOverride` 필드 추가, 코덱 끝에 추가(레거시 24-part 인코딩과의 하위 호환을 위해 `parts.getOrNull(24/25)` 사용 — Redis TTL 6시간 동안 남아있을 수 있는 구 데이터 대비)
- [x] `StartIncidentRequest`에 `targetRps`/`loadDurationSeconds` 추가 → `SimulationController`/`SimulationService.startIncident` 경유 → `SimulationSessionState`에 실림(ADR-0037 슬라이스 1의 `initialTraits`와 같은 자리)
- [x] `RealInfraCouponEngine.kt`에 `max-configurable-rps`(100)/`max-configurable-duration-seconds`(10) 상한 추가 — `probeAndCache`가 HTTP 요청 스레드 안에서 동기적으로 k6를 돌리므로 무제한 허용 시 요청 자체가 멈춘 것처럼 보임
- [x] `WargameLive.tsx`의 인시던트 시작 게이트에 `realInfraChoice && domain === "coupon"`일 때만 "목표 RPS"/"부하 지속시간(초)" 입력 2개 노출, `startIncident` 4번째 인자로 전달

**완료 기준 충족**: `npx tsc --noEmit`/`npm run lint`(0 errors)/`npm run build` 전부 클린. 백엔드 전체 스위트(`./scripts/run-tests-isolated.sh`) 통과, `RealInfraCouponEngineTest`에 상대 비교 테스트 추가(낮은 커스텀 RPS가 기본 incident-rps보다 achieved trafficRps를 뚜렷이 낮춤). curl로 직접 검증: 기본(오버라이드 없음) 세션 `trafficRps: 10.56`(용량 한계에 도달) vs `targetRps=3` 세션 `trafficRps: 3.03`(요청값과 거의 정확히 일치) — 오버라이드가 실제 k6 부하를 진짜로 제어하는 것을 확인. 실 브라우저로 coupon 도메인에서 입력 2개가 나타나고 notification 도메인에서는 안 나타나는 것 확인, 모바일(375px) 오버플로 없음 확인.

**진행 중 발견하고 고친 버그**: 실 브라우저 검증 중 이번 슬라이스와 무관한 기존 동시성 버그를 발견했다 — `WargameLive.tsx`가 3초마다 `GET /state`를 폴링하는데, 폴링이 진행 중인 k6 프로브(3초+Docker 오버헤드)와 겹치면 두 번째 `computeState` 호출이 `measurementStore.find()`에서 아직 null을 보고 스키마를 또 프로비저닝하려다 `DuplicateKeyException`으로 500 에러가 났다(에러율 100%로 관측). `RealInfraCouponEngine.probeAndCache`의 `synchronized(lock)` 안에 double-checked 캐시 재확인을 추가(`provisionSchema=true`, 즉 `computeState` 경로에만 적용 — `applyAction`의 `provisionSchema=false` 경로는 액션마다 항상 새로 프로빙해야 하므로 건드리지 않음)해서 해결 — 전체 realinfra 스위트(25개) 재검증 통과, 수정 전/후 로그에서 `DuplicateKeyException` 0건 확인.

**아직 못 고친 것**: 위 수정을 검증하던 중 **별개의, 더 깊은** 사전 존재 버그를 하나 더 발견했다 — 스키마 중복 생성 에러는 사라졌지만, 같은 재현 시나리오에서 k6 요청 13개가 전부 `relation "coupon_inventory" does not exist`로 실패했다(에러율 여전히 100%). `CouponSchemaProvisioner.provision()`은 동기 순차 DDL이라 이 타이밍 문제의 원인이 바로 보이지 않음 — Phase 3-B 범위를 벗어나는 별도 조사가 필요해 `spawn_task`로 분리했다(`task_41f7d0b0`). Traffic Lab 핵심 기능(RPS/지속시간 오버라이드) 자체는 폴링 없는 순차 curl 호출로 이미 명확히 검증됐으므로 이 잔여 버그와 무관하게 정상 동작함.

### Phase 3-B 후속 버그 — real-infra coupon 스키마 provision이 SimulationService의 트랜잭션에 편입되어 DDL 커밋이 지연되던 문제 ✅ 완료 (2026-09-10)

Phase 3-B 라이브 검증 중 커밋 `adc32da`(computeState 캐시 미스 재프로빙 경합 수정)를 확인한 직후, 완전히 새로운 세션으로 인시던트를 한 번만 시작(동시 클릭 없음)했는데도 메트릭 패널이 "에러율 100%"에 고정되는 걸 발견했다. 백엔드 로그에는 그 단일 k6 run에서 `org.postgresql.util.PSQLException: relation "coupon_inventory" does not exist`가 13/13 요청 전부에서 발생 — `CouponSchemaProvisioner.provision()`이 `DROP/CREATE SCHEMA` → `CREATE TABLE` → `INSERT`를 k6 실행보다 먼저, 같은 스레드에서 동기 실행하는데도 재현됐다는 점에서 기존에 고친 경합(두 요청이 겹치는 경우)과는 무관한, 단일 순차 호출로도 100% 재현되는 별개의 문제였다.

원인: `SimulationService.startIncident`/`applyAction`은 `@Transactional`(`SimulationService.kt:76`, `:160`)이고, 그 안에서 동기적으로 `RealInfraCouponEngine.computeState()`/`applyAction()` → `probeAndCache()`를 호출한다. `CouponSchemaProvisioner`의 `JdbcTemplate`은 커스텀 빈이 아닌, 앱의 유일한 primary `DataSource`(JPA와 공유)에 물린 Spring Boot 기본 빈이다 — Spring의 `JpaTransactionManager`는 바로 이 plain JDBC 접근이 같은 트랜잭션에 합류하도록 그 DataSource의 커넥션을 `TransactionSynchronizationManager`에 노출하는 표준 동작을 한다. 그 결과 `provision()`의 DDL이 `startIncident`가 연 트랜잭션의 커넥션 위에서 실행되어 **커밋되지 않은 채로** 남고, 바로 이어서 같은 트랜잭션 안에서 동기 실행되는 `loadRunner.run(...)`(k6 컨테이너, `probeAndCache` 내부에서 `process.waitFor`로 블로킹)이 보내는 요청은 전부 완전히 별개의, 트랜잭션과 무관한 `SessionDataSourceRegistry`의 per-session Hikari 풀(Toxiproxy 경유)을 타므로 그 시점엔 아직 커밋되지 않은 스키마/테이블을 볼 수 없었다 — 경합이 아니라 순수한 트랜잭션 경계 순서 문제였기 때문에 동시성 없이도 매번 재현됐다.

- [x] `CouponSchemaProvisioner.provision()`에 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 추가 — 호출자(`startIncident`/`applyAction`)의 열린 트랜잭션과 무관하게 이 DDL만 독립된 새 트랜잭션에서 실행·커밋되도록 해, `probeAndCache`가 k6를 실행하기 전에 스키마/테이블이 다른 모든 커넥션에서 이미 보이는 상태를 보장한다.
- [x] `RealInfraCouponEngineTest.kt`에 회귀 테스트 추가 — 기존 테스트들과 달리 `engine.computeState(...)`를 직접 부르지 않고, 이 앱에 이미 있던 `requiresNewTransactionTemplate`과 짝을 이루는 기본 `transactionTemplate`(REQUIRED 전파, `TransactionSupportConfig.kt`)으로 감싸 `startIncident`/`applyAction`과 동일한 "열린 트랜잭션 안에서 호출" 조건을 재현.

**완료 기준 충족**: `./gradlew compileKotlin`/`compileTestKotlin` 클린. `./scripts/run-tests-isolated.sh --tests "com.sysdrill.backend.simulation.realinfra.*"` 26/26 통과, 전체 스위트(`./scripts/run-tests-isolated.sh`) 통과. 신규 테스트는 수정 전 코드로는 `errorRate`가 사실상 1.0으로 나와 실패하고(재현 확인), 수정 후에는 통과함을 확인했다.

**진행 중 발견한 결정 사항**: 새 ADR은 쓰지 않았다 — `provision()`에 `REQUIRES_NEW` 애노테이션 하나를 붙이는 것은 되돌리기 비용이 거의 0에 가까운(annotation 한 줄 제거) 변경이라 CLAUDE.md의 ADR 3조건 중 "되돌리기 비용이 실제로 크다"를 만족하지 않는다 — 대신 왜 이게 필요한지는 `provision()` 바로 위 KDoc에 근거와 함께 남겼다(재사용 가능한 독립 기록이 필요할 만큼 무겁지 않은 결정).

**진행 중 발견한 버그와 수정(테스트 작성 중)**: 처음 작성한 회귀 테스트는 기본 `incidentRps`(30)로 `engine.computeState`를 호출해 `errorRate < 0.5`를 검증했는데, 수정 후에도 0.78로 실패했다 — 원인은 버그가 아니라 이 파일럿의 의도된 동작이었다: `INITIAL_DB_POOL_SIZE`(4)+Toxiproxy 지연 조합의 자연 처리량 한계가 ~13 req/s인데(`incident-rps` 설정 옆 calibration 주석) 기본 incident-rps(30)는 그 한계를 일부러 넘어서게 설계된 값이라 실제 커넥션 경합으로 인한 에러율 자체가 정상적으로 높다. `loadRpsOverride=3`(한계 대비 충분히 낮음)으로 바꿔 이 자연 포화를 배제하자, 그래도 완전히 새로 생성된 세션의 풀이 Toxiproxy를 통과하는 첫 물리 커넥션 몇 개를 아직 만드는 중이라 일부 요청이 Hikari의 3초 `connectionTimeout`에 걸리는 정상적인 워밍업 노이즈(관측: 약 10~17%)가 있었다 — 처음 정한 `< 0.1` 문턱값은 이 노이즈에도 실패해, 최종적으로 "거의 모든 요청이 실패"(버그: ~1.0)와 "워밍업 중 소수 실패"(정상: ~0.1~0.2)를 확실히 구분하는 `< 0.5`로 조정했다.

### Slice 2 — SystemTopology 영속화 ✅ 완료 (2026-09-10)

Slice 1(DesignTraits 매핑) 완료 시 남겨둔 "다음 슬라이스 후보"(`SystemTopology` 신규 엔터티)에 착수하기 전에, ADR-0037이 작업계획 단계로 미룬 질문(엔진이 노드별 토폴로지를 직접 읽을지)을 사용자에게 다시 확인했다(AskUserQuestion) — "영속화만"을 선택, `RuleBasedSimulationEngine`은 Slice 1의 `DesignTraits` 입력 경로를 그대로 유지하고 손대지 않는다.

문제: `DiagramCanvas.tsx`가 그리는 노드/엣지 그래프(위치·kind·per-node trait 값)는 지금도 `frontend/src/lib/localSession.ts`의 `saveCanvasDraft`/`loadCanvasDraft`로 **브라우저 탭 로컬에만** 저장돼, 기기를 바꾸거나 브라우저 데이터가 지워지면 그린 설계가 통째로 사라진다.

**저장 형태 선택**: `Postmortem` 엔터티(세션당 1행 + jsonb)와 동일한 패턴을 그대로 따랐다 — 노드를 관계형으로 쪼개지 않고, 프론트가 이미 로컬 드래프트에 쓰는 `{nodes, edges}` JSON 블롭 하나를 그대로 저장하는 완전 불투명(opaque) 저장소로 뒀다. ADR-0037 본문이 "세션당, 노드당(per session, per node)"이라고 적어 관계형 스키마를 암시하는 것처럼 읽힐 수 있지만, 지금 이 슬라이스에서 노드별로 쿼리/집계할 백엔드 소비자가 없다(엔진도 여전히 flat `DesignTraits`만 읽음) — 관계형 스키마는 아직 근거 없는 선제 설계라 판단해 미뤘다. 새 ADR은 쓰지 않았다: 이 저장 형태 선택은 나중에 노드별로 읽어야 하는 엔진 슬라이스가 오면 마이그레이션으로 되돌릴 수 있는 구현 판단이라 CLAUDE.md 3조건(특히 "되돌리기 비용이 크다")을 만족하지 않는다 — Slice 1의 결정 사항 기록과 같은 이유.

- [x] `V38__create_system_topologies.sql` — `session_id`(unique, FK)+`graph`(jsonb) 1행/세션, `Postmortem`용 `V22`와 동일 모양
- [x] `SystemTopology.kt`/`SystemTopologyRepository.kt`/`SystemTopologyDtos.kt`/`SystemTopologyService.kt`/`SystemTopologyController.kt`(`simulation` 패키지) — `PostmortemService.get`/`save` 패턴 그대로(저장 전 GET은 `saved=false`+빈 그래프 상수 반환, PUT은 upsert). `Postmortem.save`와 달리 세션 상태(`COMPLETED`) 제약 없음 — 설계 단계 내내 계속 저장되는 드래프트라서. `AuthWebConfig.kt`의 기존 `/sessions/**` 와일드카드가 새 경로를 이미 커버해 별도 등록 불필요(Phase 3-C `/postmortem-summary`처럼 flat path가 아님)
- [x] `frontend/src/lib/api.ts`에 `getSystemTopology`/`saveSystemTopology` 추가(`getPostmortem`/`savePostmortem`과 동일 모양)
- [x] `DiagramCanvas.tsx` — 마운트 시 `useEffect`로 백엔드 토폴로지를 조회해 `saved === true`일 때만 로컬 state를 덮어씀(백엔드에 저장된 게 없으면 로컬 드래프트를 그대로 유지 — 첫 방문/오프라인 내성 보존). 기존 `commit` 콜백(로컬 드래프트 저장 지점) 안에 `saveSystemTopology` fire-and-forget 호출을 한 줄 추가 — 실패해도 `console.error`만 하고 로컬 저장/부모 콜백 타이밍에 영향 없음

**완료 기준 충족**: 백엔드 `./gradlew compileKotlin`/`compileTestKotlin` 클린, `SystemTopologyControllerIntegrationTest`(신규 3개 — 미저장 세션은 빈 그래프, 저장 후 두 번째 PUT은 같은 행에 upsert, 소유자 아닌 사용자는 404) 통과, `./scripts/run-tests-isolated.sh --tests "com.sysdrill.backend.simulation.*"` 전체(회귀 포함) 통과. 프론트 `npx tsc --noEmit`/`npm run lint`(0 errors, 기존 `react-hooks/set-state-in-effect` 베이스라인 경고만)/`npm run build` 전부 클린. 실 브라우저(격리 백엔드 8084 — 기존에 쓰던 8083이 이번엔 무관한 다른 프로젝트가 점유하고 있어 포트를 바꿈): coupon 세션에서 DB 노드를 추가하고 `dbPoolSize`를 300으로 설정 → 네트워크 탭에서 `PUT /sessions/{id}/topology` 200 확인, 저장된 `graph`에 `dbPoolSize: 300`이 그대로 들어있는 것 확인 → 로컬스토리지 드래프트를 명시적으로 지운 뒤 페이지를 새로고침해도(`localStorage.removeItem` 후 reload) 노드와 `dbPoolSize=300`이 그대로 복원되는 것 확인 — 로컬 캐시가 아니라 실제로 백엔드에서 복원됨을 검증. 콘솔 에러 없음.

**알려진 단순화(계획적으로 처리 안 함)**: 백엔드-로컬 동기화에 last-write-wins 같은 시각 비교가 없다 — 마운트 시 백엔드에 저장된 값이 있으면 무조건 그걸로 로컬을 덮어쓴다. 두 기기에서 동시에 같은 세션을 편집하는 케이스는 이번 슬라이스 범위 밖이다.

**하지 않은 것**: 엔진이 토폴로지를 직접 읽도록 바꾸지 않음(`RuleBasedSimulationEngine` 무변경, Slice 1 경로 유지) — ADR-0037이 "작업계획 단계에서 결정"으로 미룬 훨씬 큰 후속 슬라이스로 남겨둔다. Evaluator(`HybridRuleAiEvaluator`)에 토폴로지를 새 입력으로 추가하지 않음 — `rawText`(Mermaid 텍스트, ADR-0036)는 이미 캔버스 모양을 반영 중이라, 구조화된 per-node config를 평가 컨텍스트에 넣는 건 별도 결정이 필요하다.

이걸로 `docs/DRILLS_SIMULATION_VISION.md` §8의 1번 항목("Architecture Canvas를 실행 가능한 시뮬레이션 모델로 만들 것인가")이 제안했던 두 슬라이스(DesignTraits 매핑 → SystemTopology 영속화)가 모두 끝났다. 남은 후속 결정은 §8 2/3번(Phase 3 이후 후보 우선순위)과, 이번 슬라이스가 일부러 미룬 "엔진이 노드별 토폴로지를 직접 읽는" 슬라이스다.

### Slice 3 — 엔진이 노드별 토폴로지를 직접 읽음 ✅ 완료 (2026-09-10)

Slice 2가 미뤄둔 마지막 결정("작업계획 단계에서 결정"으로 ADR-0037이 남겨둔 엔진 결합 여부)에 착수. Slice 2까지도 실제로는 "엔진이 토폴로지를 읽는다"는 이름뿐이었다 — `POST .../simulation/incident`에 실제로 들어가는 `DesignTraits`는 여전히 **프론트엔드**(`DiagramCanvas.tsx`의 `collectTraits()`)가 "같은 kind 노드가 여러 개면 마지막 값이 이긴다"는 규칙으로 평탄화해 요청 바디로 보낸 걸 백엔드가 검증 없이 받아쓰는 구조였다. 이번 슬라이스는 이 경계를 뒤집어, `SimulationService.startIncident`가 **세션에 저장된 `SystemTopology`를 서버 스스로 DB에서 읽어** `DesignTraits`를 계산하고, 저장된 토폴로지가 있으면 클라이언트가 보낸 값보다 우선하게 했다. `RuleBasedSimulationEngine.kt`의 7개 도메인 순수 함수 자체는 전혀 건드리지 않았다 — 입력이 어디서 오는지만 바뀌었다.

**집계 정책**: 같은 kind 노드가 여러 개일 때 필드별로 다르게 처리한다 — `dbPoolSize`/`consumerCount`/`readReplicaCount`/`dispatcherWorkers`/`podReplicas`(병렬 용량 단위 개수)는 **SUM**(노드를 더 그리면 총 용량이 늘어남), `cacheTtlSeconds`/`holdTimeoutSeconds`/`chunkSize`(단일 컴포넌트 설정값)는 **LAST**(기존 Slice 1/프론트 `collectTraits()`와 동일한 "마지막 노드가 이긴다" 동작 유지 — 여러 노드에 걸쳐 더할 개념이 아님).

- [x] `SystemTopologyService.kt`에 `deriveDesignTraits(sessionId, domain): DesignTraits?` 추가 — 저장된 토폴로지가 없으면 `null`(폴백 트리거), 있으면 `graph` JSON을 파싱(`@JsonIgnoreProperties(ignoreUnknown = true)` 최소 DTO 3개 — `id`/`type`/`position` 등은 무시)해 위 집계 정책 테이블(`TOPOLOGY_FIELDS` — `DiagramCanvas.tsx`의 `NODE_TRAIT_CONFIG`를 그대로 옮긴 것, 필드명이 서로 어긋나지 않게 유지해야 한다는 Slice 1과 같은 결합)로 `DesignTraits()` 기본값 위에 필드만 골라 덮어씀
- [x] `SimulationService.kt`의 `startIncident`가 `SystemTopologyService`를 새 의존성으로 받아, `!realInfra` 분기를 `systemTopologyService.deriveDesignTraits(sessionId, domain) ?: initialTraits`로 변경(real-infra 분기는 무변경 — 이미 자체 프로비저닝 최소값 로직이 있음)
- [x] 프론트엔드는 무변경 — `collectTraits`/`onTraitsChange`/`initialTraits` 전달 경로는 캔버스를 아예 쓰지 않은 세션(텍스트 전용 설계, API 직접 호출 테스트)을 위한 폴백으로 그대로 남겨뒀다

**완료 기준 충족**: `./gradlew compileKotlin`/`compileTestKotlin` 클린. `SimulationControllerIntegrationTest.kt`에 신규 테스트 추가 — product-browsing 세션에 DB kind 노드 2개(`readReplicaCount` 40+59, 합 99)를 저장한 뒤, **일부러 속이는** 클라이언트 요청 바디(`{"traits":{"readReplicaCount":0}}`)로 인시던트를 시작해도 토폴로지 기반 값이 이겨 `dbReadLoad ≈ 0.4`(`RuleBasedSimulationEngine.kt` 순수 수식으로 손계산한 정확값, 기존 `SimulationEngineTest`의 기본값 40.0과 같은 공식 — `BASE_DB_READ_CAPACITY_RPS(2000) * (1+99) = 200000`, `80000/200000`)로 나오는지 확인 — SUM 집계와 우선순위 둘 다 한 번에 증명. `./scripts/run-tests-isolated.sh --tests "com.sysdrill.backend.simulation.*"` 전체(회귀 포함) 통과.

**실 검증(curl E2E, 순수 백엔드 변경이라 브라우저 대신 사용)**: 격리 백엔드(포트 8084)에서 (1) product-browsing 세션에 위와 같은 토폴로지를 저장하고 `{"readReplicaCount":0}`을 보내는 인시던트 시작 요청이 실제로 `dbReadLoad: 0.3999999999999999`를 반환 — 토폴로지가 클라이언트 값을 실제로 덮어씀을 실제 HTTP 응답으로 확인. (2) 토폴로지를 저장하지 않은 별도 세션에서 `{"readReplicaCount":5}`를 보내면 `dbReadLoad: 6.666...`(폴백 경로가 클라이언트 값을 그대로 씀, `80000/12000`)로 정확히 갈리는 것 확인 — 두 경로가 실제로 다르게 동작함을 증명.

**하지 않은 것**: 엣지(의존성 그래프) 기반 계산 안 함 — kind별 집계만, "이 service가 실제로 연결된 db만 카운트" 같은 그래프 순회는 §5.2 "Dependency Graph" 모듈 자체가 아직 없는 훨씬 큰 후속 결정으로 남김. 프론트엔드 `collectTraits`/`NODE_TRAIT_CONFIG` 리팩터링 안 함(폴백 경로로 그대로 유지). real-infra 엔진 무변경. 새 ADR 안 씀 — 트레이트 우선순위 출처를 바꾸는 것은 Slice 1/2와 같은 급의 되돌리기 쉬운 구현 판단(`when` 분기 한 줄)이라 CLAUDE.md 3조건 미충족.

이걸로 `docs/DRILLS_SIMULATION_VISION.md` §5.3/§8이 조건부로 남겨뒀던 "Architecture Canvas가 시뮬레이션의 실제 입력이 되는" 전환이 완료됐다 — Slice 1(매핑) → Slice 2(영속화) → Slice 3(엔진이 직접 읽음)까지 세 슬라이스로 점진적으로 도달. 남은 후속 결정은 §8 2/3번(Phase 3 이후 후보 우선순위)과, 이번에도 의도적으로 미룬 엣지 인식(Dependency Graph) 슬라이스뿐이다.

---

## 진행 방식 메모

- 각 단계 시작 전 해당 단계의 "완료 기준"을 재확인하고, 애매하면 [PRD.md](docs/PRD.md)/[ARCHITECTURE.md](docs/ARCHITECTURE.md)를 먼저 참고한다. 그래도 결정할 수 없는 제품 방향 질문이면 사용자에게 확인한다.
- Phase 1(0~11단계)과 Phase 2(12단계~)는 같은 이 문서 안에서 이어진다. Phase 3 이후는 [docs/ROADMAP.md](docs/ROADMAP.md)를 참고하고, 그 시점에 이 문서를 이어서 갱신한다.
- 테스트: 각 단계마다 최소한의 자동 테스트(단위 또는 통합)를 함께 작성한다. 프론트엔드 단계는 가능하면 브라우저로 직접 동작을 확인한다.
- 하드/되돌리기 어렵고/맥락 없이 놀랍고/진짜 트레이드오프인 결정은 [CLAUDE.md](CLAUDE.md)의 ADR 절 기준에 따라 `docs/adr/`에도 별도로 기록한다.
- **전체 백엔드 테스트는 `./scripts/run-tests-isolated.sh`로 돌린다** — 다른 세션과 충돌하지 않는 격리 Postgres/Redis를 매번 새로 띄우고, `RealInfraCouponController`류 실전 인프라 테스트가 요구하는 Toxiproxy 라우팅(컴포즈 네트워크 안 고정 서비스명 접속, application.yml 주석 참고)까지 맞춰준다. 30~39단계에서는 이 스크립트가 없어 `RealInfraCouponControllerSessionTrackingTest`/`RealInfraCouponTracingTest` 2개를 "격리 환경에서는 원래 실패하는 무관한 이슈"로 계속 넘겨왔는데, 39단계 이후 커버리지 점검 중에 이게 진짜 버그가 아니라 격리 방식 자체의 허점이었음을 확인하고 이 스크립트로 근본 해결했다 — 이제 이 스크립트로 돌리면 223개 전부(그 2개 포함) 통과해야 정상이다.
