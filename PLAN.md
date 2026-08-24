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

- [ ] [ARCHITECTURE.md](docs/ARCHITECTURE.md) §6 `SystemState`/`DesignTraits` 모델 구현
- [ ] "선착순 쿠폰" 시나리오의 워게임 이벤트 템플릿 1개 구현 (트래픽 20배 + Redis latency 증가 → DB write hotspot)
- [ ] 사용자 액션(`applied_actions`) 처리 및 §6.1 인과/부작용 규칙 최소 3개 구현 (Rate Limit 강화, Cache TTL 조정, DB pool 증가)
- [ ] `utilization` 기반 병목 계산 로직 ([ARCHITECTURE.md](docs/ARCHITECTURE.md) §6의 0~60/60~80/80~95/95+/100+ 구간)

**완료 기준**: 시나리오를 시작하고 시간에 따라 SystemState가 악화되다가, 올바른 액션을 적용하면 지표가 회복되는 흐름을 통합 테스트로 재현한다.

## 5단계 — Rule Evaluator + AI 평가 연동

- [ ] Rule Evaluator: 요구사항 누락, scenario-specific invariant 체크 구현 (LLM 없이 판정 가능한 것부터)
- [ ] PromptTemplate 저장/버전 관리 테이블 및 관리 API
- [ ] LLM Provider 연동 (Context Assemble → LLM Critique → Follow-up Generation), 구조화 JSON(`top_risks`, `missed_points`, `followup_questions` 등) 스키마 검증
- [ ] [PRD.md](docs/PRD.md) §10 평가 루브릭(100점) 반영

**완료 기준**: "선착순 쿠폰" 시나리오 제출에 대해 Rule+AI 하이브리드 평가 결과가 구조화된 형태로 저장되고 조회 API로 확인 가능하다.

## 6단계 — 프론트엔드: System Design Workspace

- [ ] 온보딩(연차/스택/목표) → Dashboard → 시나리오 목록 → Design Workspace 화면 구현
- [ ] Design Workspace: 요구사항 표시, 구조화 섹션 입력, 자동저장, 제출
- [ ] 제출 후 평가 대기 상태(Polling) UI

**완료 기준**: 브라우저에서 시나리오를 선택해 설계를 제출하고 평가 결과를 볼 수 있다.

## 7단계 — 프론트엔드: 꼬리설계 + Wargame Live 콘솔

- [ ] 꼬리설계 카드 UI (조건 변경 표시 + 재설계 입력)
- [ ] Wargame Live: MetricsPanel, EventStreamPanel, ActionPanel, TimelinePanel ([PRD.md](docs/PRD.md) §7.3, 원본 와이어프레임 참고)
- [ ] 액션 제출 → Simulation Engine 결과 반영 → 실시간(Polling 또는 SSE) 갱신

**완료 기준**: 사용자가 꼬리설계를 거쳐 워게임에 진입하고, 액션을 취하며 지표 변화를 관찰할 수 있다.

## 8단계 — 결과 리포트 + 대시보드

- [ ] Report 생성 (Synthesis 단계): 점수 분해, 강점/약점, 실무 리스크 Top N, 다음 추천
- [ ] SkillProfile 갱신 로직 (반복 약점 태깅)
- [ ] Dashboard: 최근 진행, 약점 TOP 3, 점수 추이, 추천 다음 세션

**완료 기준**: 세션 완료 후 리포트 페이지에서 결과를 확인하고, 대시보드에 약점/추천이 반영된다.

## 9단계 — Build Mode (Rate Limiter)

- [ ] Build Runner: Docker 기반 격리 워커, CPU/메모리/timeout 제한, outbound network 차단
- [ ] Rate Limiter 챌린지 stage 1~6 정의 ([PRD.md](docs/PRD.md) §7.1, 원본 문서 stage 표 참고)
- [ ] 제출(Job Queue 적재) → Sandbox 실행/테스트 → 결과 저장 → 피드백

**완료 기준**: 로컬 템플릿 repo에서 Rate Limiter를 구현해 CLI/git으로 제출하면 stage별 테스트 결과와 리스크 피드백을 받는다.

## 10단계 — Bridge Mode 연결

- [ ] Build(Rate Limiter) 완료 → 선착순 쿠폰 Design → 꼬리설계 → Wargame으로 이어지는 단일 흐름 구현
- [ ] Bridge 진행률 UI, 통합 평가(구현+설계+운영) 리포트

**완료 기준**: 하나의 연속된 세션에서 Build→Design→Wargame 전체 루프를 완주할 수 있다.

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
