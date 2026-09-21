# 기술 하이라이트

> 이 프로젝트에서 "어려웠던 것"과 "왜 그렇게 풀었는지"를 골라 정리한 문서입니다. 전체 결정 기록은 [ADR 38건](adr/README.md), 단계별 작업 일지는 [PLAN.md](../PLAN.md)에 있습니다. 여기는 그중 코드 리뷰어나 면접관이 10분 안에 볼 만한 것만 추렸습니다.

각 항목은 **문제 → 선택 → 근거/결과 → 코드 위치** 순서입니다.

---

## 1. 신뢰할 수 없는 코드를 채점하는 Docker 샌드박스

**문제.** Build Mode는 사용자가 제출한 Python 코드를 서버가 실행해서 stage별로 채점합니다. 제출 코드는 무한 루프를 돌거나, fork bomb을 만들거나, 외부로 데이터를 보내거나, 출력을 수십 MB 쏟아낼 수 있습니다.

**선택.** `(submission, stage)` 쌍마다 새 컨테이너 하나:

```
docker run --rm --name sysdrill-build-<uuid> \
  --network none --cpus 0.5 --memory 128m --pids-limit 64 \
  -v <workdir>:/work:ro -w /work python:3.12-slim \
  timeout --kill-after=5s 10 python3 run_test.py
```

**근거와 디테일.**
- `--network none`이 유출을, CPU/메모리/PID 상한이 폭주를 막습니다. gVisor/Firecracker는 보안 요구가 커질 때의 업그레이드 경로로 남겨두되, "컨테이너-per-stage"라는 모양은 그때도 바뀌지 않도록 설계했습니다.
- **컨테이너를 죽이는 경로가 둘이어야 합니다.** JVM 쪽 `Process.destroyForcibly()`는 `docker run` *클라이언트*만 죽이고 컨테이너는 데몬 아래 계속 돕니다. `--rm`도 정상 종료 시에만 동작합니다. 그래서 컨테이너에 이름을 붙이고 타임아웃 시 `docker rm -f`를 직접 호출합니다.
- **출력은 실행과 동시에 비워야 합니다.** `waitFor()` 후에 읽으면 파이프 버퍼(~64KB)가 차는 순간 컨테이너가 `write`에서 멈춰 멀쩡한 제출이 타임아웃으로 오판되고, 데드라인을 넘긴 실행에서는 `readText()`가 EOF를 영영 못 만나 호출 스레드가 멈춥니다. 별도 drain 스레드가 실행과 나란히 읽습니다.
- 컨테이너 안의 `timeout`은 기본적으로 SIGTERM만 보내므로 `signal.signal(SIGTERM, SIG_IGN)` 세 줄이면 우회됩니다. `--kill-after`로 SIGKILL을 뒤따르게 했습니다.
- 채점 스크립트 자체는 파일시스템이 아니라 DB에 버전 관리됩니다 ([ADR-0006](adr/0006-config-as-data.md)) — 과제 콘텐츠 변경이 배포가 아니라 마이그레이션이 되도록.

**코드.** [`build/SandboxExecutor.kt`](../backend/src/main/kotlin/com/sysdrill/backend/build/SandboxExecutor.kt) · [ADR-0007](adr/0007-docker-sandboxed-build-execution.md) · [ADR-0008](adr/0008-python-for-build-challenges.md)

---

## 2. 커밋 후에만 큐에 넣는다 — 같은 버그를 두 번 만난 뒤 규칙이 됨

**문제.** 제출(Submission)을 저장하고 Redis 큐에 평가 job을 push하는 코드가 처음엔 같은 `@Transactional` 메서드 안에 있었습니다. 워커는 별도 트랜잭션에서 job을 꺼내 `findById`를 호출하는데, **enqueue한 트랜잭션이 아직 커밋되지 않은 시점**에 꺼내면 행이 안 보여서 job을 조용히 버렸습니다. 간헐적이라 재현이 어려웠고, 평가 파이프라인에서 한 번 고친 뒤 Build 파이프라인에서 **독립적으로 다시 발견**됐습니다.

**선택.** 서비스는 트랜잭션 안에서 Spring 애플리케이션 이벤트만 발행하고, 실제 `enqueue()`는 `@TransactionalEventListener(phase = AFTER_COMMIT)` 컴포넌트가 수행합니다. "save한 행을 백그라운드 워커에 넘기는" 흐름은 전부 이 패턴을 따르는 것이 저장소 규칙입니다.

**뒤따른 함정.** AFTER_COMMIT 콜백은 방금 끝난 트랜잭션과 **같은 스레드**에서 실행되는데, 그 안에서 `REQUIRED` 전파로 새 트랜잭션을 열면 "No active transaction for update or delete query"가 납니다 — 스레드 로컬 트랜잭션 상태가 아직 완전히 정리되지 않은 상태라서. 콜백 안의 상태 전이(`SUBMITTED → EVALUATING`)는 `PROPAGATION_REQUIRES_NEW` 템플릿으로 실행합니다.

**코드.** [`evaluation/EvaluationRequestPublisher.kt`](../backend/src/main/kotlin/com/sysdrill/backend/evaluation/EvaluationRequestPublisher.kt) · [`common/TransactionSupportConfig.kt`](../backend/src/main/kotlin/com/sysdrill/backend/common/TransactionSupportConfig.kt) · [ADR-0004](adr/0004-async-jobs-enqueued-only-after-commit.md)

---

## 3. 멱등성은 앱 코드가 아니라 DB 제약이 보장한다

**문제.** `EvaluationWorker`는 같은 job이 두 번 배달돼도 한 번만 평가하도록 `existsBySubmissionIdAndIsActiveTrue`를 먼저 확인했습니다. 그런데 이 확인과 뒤의 INSERT는 한 트랜잭션 안의 **두 문장**이지 원자 연산이 아닙니다. 중복 배달이 동시에 두 트랜잭션에서 돌면 둘 다 "활성 평가 0건"을 보고 둘 다 INSERT합니다. 한 제출에 활성 평가가 2건 생기는 걸 통합 테스트가 잡았는데, 재현 조건이 "두 번째 컨슈머"였고 실제로는 로컬에 남아 있던 `bootRun` 프로세스가 그 역할을 하고 있었습니다.

**선택.** 부분 유니크 인덱스:

```sql
create unique index idx_evaluations_one_active_per_submission
  on evaluations (submission_id) where is_active;
```

워커는 `DataIntegrityViolationException`을 "다른 배달이 이겼다"는 정상 결과로 처리합니다.

**기각한 대안.** Redis `SETNX`로 제출별 처리 키를 잡는 방식. 스키마를 안 건드리지만 *이 큐를 통한* 중복 배달만 막고, 다른 경로로 Evaluation이 써지는 경우엔 무력하며, TTL/정리가 필요한 두 번째 진실 공급원이 생깁니다. "제출당 활성 평가는 최대 1건"은 DB가 공짜로 강제할 수 있는 불변식입니다.

**디테일.** 유니크 위반 시 Postgres는 그 트랜잭션을 이미 abort한 상태라, 같은 `catch`에서 재시도 처리(`handleFailure`)를 하면 그 안의 상태 업데이트가 또 실패합니다. 별도로 잡아 트랜잭션을 깨끗이 되감은 뒤 상위에서 info 로그로 남깁니다.

**코드.** [`V28__unique_active_evaluation_per_submission.sql`](../backend/src/main/resources/db/migration/V28__unique_active_evaluation_per_submission.sql) · [`evaluation/EvaluationWorker.kt`](../backend/src/main/kotlin/com/sysdrill/backend/evaluation/EvaluationWorker.kt) · [ADR-0027](adr/0027-evaluation-idempotency-guarded-by-db-constraint-not-just-in-app-dedup-check.md)

---

## 4. 세션마다 실제 인프라 — 컨테이너가 아니라 Postgres 스키마 단위로

**문제.** 규칙 기반 시뮬레이션은 결정론적이고 빠르지만 "DB pool이 고갈되면 p95가 어떻게 되는가"를 공식으로 보여줄 뿐입니다. 쿠폰 도메인에서만 실험적으로 **진짜 인프라**를 쓰기로 했습니다 — 실제 Postgres에 실제 k6 부하를 쏘고 실제 지연을 주입해서.

**선택.** 세션마다 Postgres 컨테이너를 띄우는 대신 **전용 스키마 + 전용 `HikariDataSource` + 전용 Toxiproxy 프록시**를 할당합니다. 가르치려는 세 가지(rate limit, cache TTL, DB pool size)는 물리적 컨테이너 격리 없이도 스키마·풀 단위 격리로 충분히 관측 가능합니다.

**실측에서 배운 것.**
- 규칙 기반 엔진의 300/6000 RPS를 그대로 쓰면 노트북의 공유 Postgres가 "가르치려는 특성"이 아니라 "머신 한계"에서 포화됩니다. Toxiproxy로 쿼리당 ~300ms 지연을 넣으면 4-커넥션 풀은 4/0.3s ≈ **13 req/s**에서 캡이 걸리므로, incident RPS는 30이면 충분히 극적입니다 (baseline p95 ~1.3s → 조치 후 ~0.7s).
- 액션마다 스키마를 재생성하던 첫 구현은 **행(hang)**이 걸렸습니다 — `DROP SCHEMA CASCADE`가 직전 프로브에서 아직 끝나지 않은 요청이 잡고 있는 커넥션을 기다리기 때문. 이제 스키마는 `startIncident`에서 한 번만 만듭니다.
- Toxiproxy가 주입한 네트워크 지연에는 **의도적으로 완화 액션을 두지 않았습니다** ([ADR-0015](adr/0015-toxiproxy-fault-has-no-mitigating-action.md)). 세 액션 모두 지연 자체를 없앨 수 없다는 게 학습 포인트입니다.
- 유휴 세션의 스키마와 풀은 저절로 사라지지 않으므로 백그라운드 sweep이 6시간 미접촉 세션을 회수합니다.

**테스트 전략의 예외.** 규칙 기반 엔진 테스트는 손으로 계산한 정확한 값을 `isCloseTo`로 단언합니다. 실제 인프라 테스트는 머신마다 숫자가 달라지므로 **범위와 상대 비교**(rate-limit 적용 p95 ≤ 미적용 p95)만 단언합니다 ([ADR-0014](adr/0014-real-infra-tests-use-range-assertions.md)). Docker Desktop VM을 다른 프로젝트 컨테이너 21개와 나눠 쓰던 날 k6가 CPU 시간을 못 받아 흔들린 테스트는, 프로덕션 코드를 건드리지 않고 테스트 레벨 bounded retry로만 대응했습니다.

**코드.** [`simulation/realinfra/`](../backend/src/main/kotlin/com/sysdrill/backend/simulation/realinfra/) (`RealInfraCouponEngine`, `CouponSchemaProvisioner`, `ToxiproxySessionProxy`, `RealInfraSessionSweepWorker`) · [ADR-0013](adr/0013-coupon-real-infra-pilot-schema-per-session.md)

---

## 5. 파생값은 저장하지 않는다 — 그리고 그 규칙의 단 하나의 예외

**규칙.** `SystemState`(p95, error rate, cache hit ratio …)는 Redis에 저장된 작은 입력(`incidentActive`, `DesignTraits`)에서 매번 재계산합니다. 스킬 프로필의 "IMPROVING/DECLINING" 추세, 인증(certification) 통과 여부도 읽기 시점에 계산합니다. 저장하면 원본과 어긋나는 순간이 생기고, 그 어긋남은 발견하기 어려운 버그가 됩니다. 인증 통과 점수(70점)는 설정값이라 기준이 바뀌어도 마이그레이션이 필요 없습니다.

**이 규칙 덕분에 공짜로 얻은 것.** 인시던트 리플레이. 규칙 기반 세션은 `(domain, incidentActive, N번째 액션까지의 traits)`의 순수 함수이므로, 이미 기록 중이던 `AppliedAction` 행만 순서대로 다시 엔진에 넣으면 타임라인 전체가 복원됩니다. 새 테이블 없음.

**예외.** 실제 인프라 세션은 재현이 불가능합니다 — 같은 프로브를 다시 돌려도 인프라는 이미 sweep으로 사라졌고, 남아 있어도 숫자가 다릅니다. 그래서 real-infra 세션만 각 스텝의 실측 `SystemState`를 `AppliedAction.parameters`(이미 있던 미사용 JSONB 컬럼)에 스냅샷으로 남깁니다. 모든 행에 `engineMode`를 기록해 리플레이가 Redis TTL(6h)에 의존하지 않고 경로를 고를 수 있게 했습니다. "측정이 근본적으로 비재현적인 곳"에만 허용하는 좁은 예외이며, 4번의 테스트 전략 예외와 같은 범주입니다.

**코드.** [`simulation/SimulationService.kt`](../backend/src/main/kotlin/com/sysdrill/backend/simulation/SimulationService.kt) (`getTimeline`) · [ADR-0011](adr/0011-derived-values-are-never-persisted.md) · [ADR-0016](adr/0016-incident-replay-snapshots-only-for-real-infra.md)

---

## 6. 아키텍처 캔버스를 "그림"에서 "시뮬레이션 입력"으로 — 이전 결정을 명시적으로 뒤집기

**배경.** [ADR-0036](adr/0036-diagram-canvas-is-an-input-method-that-still-serializes-to-mermaid-text.md)은 캔버스를 라벨만 있는 7종 노드의 *입력 방법*으로 제한하고 Mermaid 텍스트로만 직렬화했습니다. 이유는 명확했습니다 — "두 번째 진실 공급원을 만들지 않는다."

**뒤집은 이유.** 제품 비전의 핵심 메커니즘은 "리드 레플리카를 하나 추가하면 시뮬레이션 결과가 달라진다"입니다. 라벨만 있는 캔버스는 UI를 아무리 다듬어도 이걸 표현할 수 없습니다. 격차는 *어떻게 그리느냐*가 아니라 *캔버스가 무엇이냐*에 있었습니다. 그래서 [ADR-0037](adr/0037-architecture-canvas-becomes-the-simulation-topology-source-of-truth.md)에서 0036이 "대안"으로 명명하고 기각했던 방향을 정확히 그대로 채택했습니다.

**구현.** 노드별 config(DB pool size, cache TTL, consumer 수, read replica 수, pod replicas …)가 `SystemTopology` 엔티티에 세션 단위로 저장되고, `startIncident`가 이를 `DesignTraits`로 변환해 엔진에 넘깁니다. 백엔드 `TOPOLOGY_FIELDS`와 프론트 `NODE_TRAIT_CONFIG`는 7개 도메인 전부에서 필드 단위로 미러링됩니다.

**뒤집은 뒤 발견한 버그 두 개.** ① `getTimeline` 리플레이가 빈 `DesignTraits()`로 시작해서 저장된 토폴로지가 있는 세션은 라이브 수치와 리플레이 수치가 달랐음. ② real-infra 분기가 토폴로지를 아예 안 읽고 항상 undersized 기본값(pool=4)으로 시작해서, 사용자가 캔버스에서 설정한 값이 토글을 켜는 순간 조용히 무시됐음 — 그것도 실제 HikariCP 풀 크기로 쓰이는 값이. 둘 다 "캔버스에서 바꾼 값과 규칙 기반 기본값이 다를 때만 채택"하는 식으로 교육적 의도(undersized로 시작해야 액션 효과가 보임)를 보존하며 수정했습니다.

**코드.** [`simulation/SystemTopologyService.kt`](../backend/src/main/kotlin/com/sysdrill/backend/simulation/SystemTopologyService.kt) · [`frontend/.../DiagramCanvas.tsx`](../frontend/src/app/design/[sessionId]/DiagramCanvas.tsx)

---

## 7. 도메인별 순수 함수 7개 vs 범용 엔진 — 아직도 범용 엔진을 안 만든 이유

**결정.** `SimulationEngine`은 쿠폰·알림·상품조회·결제·예약·배치정산·오토스케일링 각각의 `computeState`/`applyAction` 순수 함수입니다. 공유하는 건 도메인 무관한 utilization-band 수학(`latencyMultiplier`, `errorRateFor`)뿐입니다.

**근거.** 일곱 인시던트는 병목 자원이 다릅니다 — DB 쓰기 용량, consumer 처리량, 캐시 hit ratio, 재처리 대상 레코드 수, pod 수. 이걸 데이터 주도 엔진으로 표현하려면 작은 공식 언어가 필요하고, 그러면 **모든 인시던트의 출력 숫자를 손계산과 대조하는 테스트**(첫 인시던트부터 이어온 관행 — 예: `traffic=20000rps / p95=1150ms / error=60.0%`가 정확히 일치)가 공식을 추적하기 어려워집니다. 새 도메인은 "기존 것의 이름만 바꾼 복사본"이 아니라 **진짜 다른 메커니즘**이어야 한다는 게 추가 규칙입니다 ([ADR-0012](adr/0012-new-incident-domains-get-distinct-mechanisms.md)). 네 번째 도메인이 기존 것과 거의 같아지는 순간이 범용화 재검토 시점인데, 일곱 번째까지 그 순간은 오지 않았습니다.

**코드.** [`simulation/RuleBasedSimulationEngine.kt`](../backend/src/main/kotlin/com/sysdrill/backend/simulation/RuleBasedSimulationEngine.kt) · [ADR-0010](adr/0010-simulation-engine-per-domain-functions.md)

---

## 8. 삽질 기록 (짧게)

한 줄로 끝나지 않는 것들은 [PLAN.md](../PLAN.md)에 그 단계의 맥락과 함께 있습니다.

| 겪은 것 | 배운 것 |
|---|---|
| Spring Boot 4에서 `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`를 수동 조합하니 컴파일은 되는데 트레이스가 **0건** | Boot 4는 OTLP 자동설정 클래스가 `spring-boot-starter-opentelemetry`가 끌어오는 모듈로 이동했고, 프로퍼티 키도 `management.otlp.tracing.*`(3.x)에서 `management.opentelemetry.tracing.export.otlp.*`로 바뀜. 3.x 키는 메타데이터에 남아 있어 **조용히 no-op** — DEBUG 로그에서 SdkSpan까지 도달하는 걸 보고서야 export 단계 문제임을 특정 |
| 통합 테스트가 다른 터미널의 `bootRun`과 같은 Postgres/Redis를 공유해서 셋 다 서로를 오염 (한 세션의 orphan `V28` 마이그레이션이 다른 세션의 `FlywayValidateException`으로) | `scripts/run-tests-isolated.sh` — 별도 포트의 Postgres/Redis 컨테이너로 테스트 실행. 단, Toxiproxy 경유 경로는 compose 내부 네트워크로 직접 붙어서 `DB_PORT` 오버라이드로는 격리되지 않음을 문서화 |
| JaCoCo 리포트가 마지막 부분 실행분만 반영해 커버리지가 왜곡 | 전체 스위트 재실행 후 XML을 직접 파싱해 패키지별 집계. 낮은 3곳(`tools` 0%, `mail` 47%, `evaluation/llm` 58%)은 전부 외부 I/O 경계(CLI 스크립트, SMTP 어댑터, 실 API DTO)로 mock을 억지로 씌우지 않기로 판단 |
| Kafka KRaft 단일 노드가 `KAFKA_LISTENERS`에 `0.0.0.0`을 쓰면 시작 실패 | 빈 호스트(`PLAINTEXT://:9092`)가 "모든 인터페이스 바인드"의 관용구. `0.0.0.0` 리터럴은 CONTROLLER 리스너의 advertised 폴백에 그대로 상속돼 검증에서 거부됨 |
| `run.sh`의 포트 충돌 감지가 Docker Desktop의 포트 포워딩 프록시를 못 봄 | `SO_REUSEADDR`를 켠 bind는 이미 `0.0.0.0`에 바인드된 리스너에 대해 성공할 수 있음. 옵션 없이 plain bind해야 정확 |
| 조직 초대 토큰을 로그인처럼 서명 JWT로 만들까? | 로그인 토큰의 서명은 매 요청마다 DB 없이 검증하기 위한 것. 초대는 어차피 모든 redemption 경로가 DB에서 조직·역할·상태를 조회하므로 서명이 추가로 확인해주는 게 없다 — 불투명 UUID + DB 행이 곧 상태 ([ADR-0022](adr/0022-organization-invitation-token-is-an-opaque-db-code-not-a-jwt.md)) |
