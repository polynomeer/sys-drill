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

## 11단계 — MVP 콘텐츠 완성 및 통합 테스트

- [ ] 나머지 2개 시나리오(알림 이벤트 처리, 대규모 상품 조회) 콘텐츠화 ([PRD.md](docs/PRD.md) §8.2, §8.3)
- [ ] Queue Build 과제 추가
- [ ] E2E 테스트: 회원가입 → 시나리오 3종 각각 Design→꼬리설계→Wargame→Report 완주
- [ ] [PRD.md](docs/PRD.md) §11 "MVP에서 검증할 세 가지" 기준으로 셀프 점검

**완료 기준**: MVP 범위([PRD.md](docs/PRD.md) §11 포함 항목) 전체가 시나리오 3종 기준으로 동작한다.

---

## 진행 방식 메모

- 각 단계 시작 전 해당 단계의 "완료 기준"을 재확인하고, 애매하면 [PRD.md](docs/PRD.md)/[ARCHITECTURE.md](docs/ARCHITECTURE.md)를 먼저 참고한다. 그래도 결정할 수 없는 제품 방향 질문이면 사용자에게 확인한다.
- 이 단계 구분은 Phase 1(MVP) 한정이다. Phase 2 이후는 [docs/ROADMAP.md](docs/ROADMAP.md)를 참고하고, 그 시점에 이 문서를 이어서 갱신한다.
- 테스트: 각 단계마다 최소한의 자동 테스트(단위 또는 통합)를 함께 작성한다. 프론트엔드 단계는 가능하면 브라우저로 직접 동작을 확인한다.
