# 테스트 전략

백엔드 테스트 클래스 58개, `@Test` 295개 (2026-09-21 기준). 실행 방법은 [§3](#3-실행), 왜 이렇게 나눴는지는 [§1](#1-세-층)부터.

## 1. 세 층

| 층 | 클래스 | 무엇을 검증하나 | 외부 의존 | 단언 방식 |
|---|---|---|---|---|
| **순수 단위** | 6 | `SimulationEngine` 도메인별 수식, `RuleEvaluator`, `Rubric`, `SessionStateMachine`, 상태 코덱, LLM 응답 파서 | 없음 | 손으로 계산한 **정확한 값** (`isCloseTo`) |
| **통합** (`@SpringBootTest` + MockMvc, `@DataJpaTest` 1) | 48 | 컨트롤러 → 서비스 → 리포지토리 → **실제 Postgres/Redis** 전 구간. 상태 전이, 권한(403), 멱등성, 큐→워커 파이프라인 | docker compose의 Postgres·Redis | HTTP 응답 + DB 상태 |
| **실제 인프라** (`RANDOM_PORT`) | 4 + provisioner 테스트 | 실제 k6 부하 → 실제 Toxiproxy 지연 → 실제 Postgres 스키마 / 실제 Kafka 토픽. 트레이스가 Jaeger에 도달하는지까지 | Docker 데몬, k6 이미지, Toxiproxy, Kafka, Jaeger | **범위와 상대 비교**만 ([ADR-0014](adr/0014-real-infra-tests-use-range-assertions.md)) |

세 층을 가르는 기준은 "무엇을 mock하느냐"가 아니라 **"결과가 결정론적이냐"** 입니다.

- 규칙 기반 엔진은 순수 함수라서 `traffic=20000rps / p95=1150ms / error=60.0%`처럼 정확한 값을 박아둡니다. 새 도메인을 추가할 때는 Python 스크립트로 수치를 먼저 계산하고 Kotlin 구현이 그와 일치하는지 대조하는 게 관행입니다.
- 실제 인프라의 p95는 머신 CPU, Docker 자원 할당, 그 순간 공유 Postgres를 두드리는 다른 프로세스에 따라 달라집니다. 그래서 `errorRate ∈ [0,1]`, `rate-limit 적용 p95 ≤ 미적용 p95` 같은 것만 단언합니다. 정확한 값을 박으면 다른 머신에서 항상 깨지는 테스트가 됩니다.

## 2. 설계 선택

**Testcontainers도, H2도 쓰지 않습니다.** 통합 테스트는 `docker compose`의 실제 Postgres 16과 Redis 7을 그대로 씁니다. `ddl-auto=validate` + 실제 Flyway 마이그레이션이 테스트에서도 그대로 돌기 때문에, "H2에서는 되는데 Postgres에서 안 되는" 부류의 문제(부분 유니크 인덱스, JSONB 컬럼, Postgres 전용 DDL)가 테스트에서 드러납니다. 대가는 테스트 전에 스택을 띄워야 한다는 것과, 아래 §4의 공유 인프라 문제입니다.

**Mocking 프레임워크가 없습니다.** Mockito/MockK 의존성이 없습니다. 외부 경계는 인터페이스 + Spring DI로 교체합니다:

| 경계 | 테스트 대체물 | 방식 |
|---|---|---|
| Anthropic API | `AnthropicLlmClient`의 오프라인 폴백 (API 키 비어 있음) | 프로덕션 코드의 실제 분기 |
| Google OAuth | [`FakeGoogleOAuthClient`](../backend/src/test/kotlin/com/sysdrill/backend/support/FakeGoogleOAuthClient.kt) | `@Import(FakeGoogleOAuthConfig::class)` + `@Primary` |
| SMTP | [`FakeEmailSender`](../backend/src/test/kotlin/com/sysdrill/backend/support/FakeEmailSender.kt) | 보낸 메일을 리스트에 쌓아 단언 |
| JWT 발급 | [`TestJwtIssuer`](../backend/src/test/kotlin/com/sysdrill/backend/support/TestJwtIssuer.kt) | 실제 `JwtService`로 테스트용 토큰 발급 |

**커버리지가 낮은 곳은 의도적입니다.** 2026-09-12 전체 실행(281/281 통과) 기준 대부분의 패키지가 87% 이상이고 `identity`/`certification`/`admin`/`submission`은 100%였습니다. 눈에 띄게 낮은 세 곳은 전부 외부 I/O 경계라 mock을 억지로 씌우지 않기로 했습니다:

- `tools` 0% — Spring Bean이 아닌 독립 CLI 유지보수 스크립트 (실 Kafka 브로커 필요)
- `mail` 47% — `SmtpEmailSender`는 SMTP 설정 시에만 활성화되는 5줄짜리 어댑터. 기본 경로(`LoggingEmailSender`)는 커버됨
- `evaluation/llm` 58% — 실제 Anthropic 응답을 역직렬화하는 DTO들. 테스트 환경엔 API 키가 없어 이 경로를 탈 수 없음

JaCoCo 리포트는 `./gradlew test` 뒤 `backend/build/reports/jacoco/test/html/`에 생성됩니다. **부분 실행(`--tests`) 뒤의 리포트는 그 실행분만 반영**하므로 수치를 인용하려면 전체 스위트를 돌린 뒤 봐야 합니다.

**테스트 워커 힙은 3GB입니다.** `@SpringBootTest` 클래스 51개가 각자 컨텍스트를 올리고 Spring의 컨텍스트 캐시가 여러 개를 동시에 살려두므로(각각 HikariCP 풀 + 스레드 풀), Gradle 기본 512m에서는 OOM이 나되 **깨끗하게 죽지 않고 몇 시간씩 GC 스래싱**하는 경우가 실제로 있었습니다.

## 3. 실행

### 기본

```bash
docker compose up -d
cd backend && ./gradlew test
```

특정 패키지/클래스만:

```bash
./gradlew test --tests "com.sysdrill.backend.simulation.*"
./gradlew test --tests "com.sysdrill.backend.evaluation.EvaluationWorkerIntegrationTest"
```

### 격리 실행

```bash
./scripts/run-tests-isolated.sh                                      # 전체
./scripts/run-tests-isolated.sh --tests "com.sysdrill.backend.organization.*"
```

옆 터미널에서 `bootRun`이 돌고 있거나, 다른 브랜치가 로컬 Postgres에 마이그레이션을 적용해둔 상태라면 이쪽을 쓰세요. 매 실행마다 **별도의 Postgres/Redis 컨테이너**를 임의 포트로 띄우고, 끝나면 지웁니다. 핵심은 격리 Postgres를 compose 네트워크에 별칭으로 붙여 `REALINFRA_TOXIPROXY_PG_UPSTREAM`으로 넘기는 것 — 실제 인프라 파일럿은 Toxiproxy가 compose 내부 네트워크로 직접 Postgres에 붙기 때문에, `DB_PORT`만 바꿔서는 그 경로가 격리되지 않습니다. 이 별칭 없이 돌리면 `RealInfraCouponControllerSessionTrackingTest`/`RealInfraCouponTracingTest` 두 개가 "버그가 아닌데" 항상 실패합니다.

### CI

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml)은 `push`/`pull_request`마다:

- **backend** — `docker compose up -d` → Postgres 준비 대기 → `./gradlew test` (전체, 실제 인프라 테스트 포함) → JUnit XML 아티팩트 업로드
- **frontend** — `npm ci` → `eslint` → `tsc --noEmit` → `next build` → `npm audit --audit-level=high`

CI 러너는 전용이라 격리 스크립트가 필요 없습니다 — 격리 스크립트는 순전히 개발자 머신에서 이미 떠 있는 스택과 공존하기 위한 것입니다.

## 4. 플레이키니스 — 겪은 것과 대응

실제 인프라 테스트는 정의상 환경에 민감합니다. 지금까지의 사례와 각각의 대응:

| 증상 | 원인 | 대응 |
|---|---|---|
| `EvaluationWorkerIntegrationTest`의 "중복 배달은 한 번만 처리" 테스트가 가끔 실패 | 실제 레이스였음 — 옆에서 돌던 `bootRun`의 워커가 두 번째 컨슈머 역할을 해서 앱 레벨 dedup 체크를 뚫음 | DB 부분 유니크 인덱스로 근본 수정 ([ADR-0027](adr/0027-evaluation-idempotency-guarded-by-db-constraint-not-just-in-app-dedup-check.md)) |
| `RealInfraCouponEngineTest`가 전체 스위트에서만 무작위로 실패, 단독 실행은 항상 통과 | Docker Desktop VM(10 CPU/8GB)을 다른 프로젝트 컨테이너 21개와 나눠 쓰던 상태. k6 컨테이너(`--cpus 1.0`)가 3초 측정 창 동안 CPU 시간을 못 받음 | 프로덕션 코드 무변경. 흔들린 3개 테스트에만 `retryFlaky { }` (매 시도마다 새 세션으로 재프로브, 최대 2회) |
| 실제 인프라 알림 테스트가 갈수록 잘 실패 | 중간에 죽은 테스트가 남긴 `realinfra-notify-*` 토픽이 쌓여 메타데이터 전파가 느려짐 | `./scripts/cleanup-stale-kafka-topics.sh` |
| 전체 스위트가 `FlywayValidateException`으로 시작조차 못 함 | 다른 세션/브랜치가 공유 Postgres에 이 체크아웃엔 없는 마이그레이션을 적용 | 격리 실행 스크립트. `flyway repair`로 모르는 마이그레이션을 지우지 않음 |

원칙: **실측 파이프라인(k6 옵션, Toxiproxy 지연값 등)을 테스트를 통과시키려고 건드리지 않습니다.** 그러면 측정 자체가 왜곡됩니다. 대응은 테스트 레벨(범위 단언, 바운드 재시도, 격리)에 머뭅니다.

## 5. Build 과제 테스트

`challenges/<slug>/stages/stageN_test.py`는 두 곳에서 같은 파일이 실행됩니다:

- **사용자 로컬** — `PYTHONPATH=. python3 stages/stage1_test.py`. 빠른 피드백용.
- **서버 채점** — DB에 시드된 같은 스크립트를 `python:3.12-slim` 컨테이너에서 `--network none`, CPU 0.5, 128MB, 10초 제한으로 실행. `RESULT:PASS`/`RESULT:FAIL:<이유>` 출력과 exit code로 판정.

`BuildRunnerWorker`/`SandboxExecutor` 자체의 테스트는 [`build/`](../backend/src/test/kotlin/com/sysdrill/backend/build/)에 있으며 실제 Docker 데몬이 필요합니다.
