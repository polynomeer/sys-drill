---
status: accepted
---

# 초기 기술 선택(Kotlin/Spring Boot, 모듈러 모놀리스, PostgreSQL, Redis 리스트 큐, 규칙 기반 시뮬레이션, Docker 샌드박스, Next.js)의 이유와 대안

이 문서는 2026-10-05에 작성했고, 첫 커밋 `fff176e`(2026-08-24)부터 0~7단계 커밋(`c077f21` 스캐폴딩, `9198da8` docker-compose, `c0c391a` Redis 큐, `8728376` LLM 평가 등)에 보이는 선택의 이유를 당시 문서(`docs/ARCHITECTURE.md` `aeb3029`판, `docs/PRD.md` `f672f7d`판, `PLAN.md`, `docs/archive/`의 원본 기획서)에서 모아 재구성한 기록이다. 저장소에 이유가 적혀 있는 항목은 출처를 밝혔고, 이유가 적혀 있지 않은 비교는 **재구성**이라고 표시했다. 벤치마크나 당시의 토론을 새로 지어내지 않았다.

당시 문서가 정한 요구사항 중 기술 선택에 직접 걸리는 것은 다섯 가지다.

- 제품 도메인과 학습 UX가 빨리 바뀔 수 있으니 서비스 경계보다 실험 속도를 우선한다(ARCHITECTURE §1-1).
- LLM 평가는 수초~수십초가 걸리고 실패할 수 있으니 제출과 평가를 분리한다(§1-5, §8).
- 평가는 재현 가능해야 한다: Scenario·Rubric·PromptTemplate 버전을 저장한다(§1-6).
- 사용자마다 실제 인프라를 띄우지 않는다(§1-7, PRD §14 "실제 인프라 비용 폭증").
- 사용자 코드를 실행하는 경로는 Build Mode 하나이고, 그 경로에만 강한 격리를 둔다(§11).

## 1. 백엔드 언어와 프레임워크: Kotlin + Spring Boot

**저장소에 적힌 이유**: `docs/archive/멘토링_서비스_시스템_설계_문서.docx` §16은 Kotlin + Spring Boot를 "도메인 상태와 비동기 작업, 운영 안정성에 적합"하다고 적고, 이 서비스가 상태 전이·멱등성·재처리·장애 복구를 가르치므로 애플리케이션 구조도 이를 드러내는 편이 좋다고 했다. 같은 시기 `backend_wargame_integrated_plan_and_design.docx` 부록 B는 "Kotlin + Spring Boot 또는 TypeScript + NestJS"를 함께 후보로 올렸다. 둘 중 무엇을 왜 버렸는지는 적혀 있지 않다.

**대안 비교 (재구성)**:

| 후보 | 이 프로젝트의 필요와 맞는 점 | 걸리는 점 |
|---|---|---|
| Kotlin + Spring Boot 4.1 | 트랜잭션 경계, `@TransactionalEventListener`, Spring Data JPA·Redis·Flyway를 한 프레임워크 안에서 쓴다. Spring Framework API는 JSpecify 널 주석을 달고 있고 Kotlin 2.1부터 이를 엄격히 반영한다 | Kotlin 클래스는 기본이 `final`이라 `kotlin-spring`/`all-open`, JPA용 `no-arg`(`kotlin-jpa`) 플러그인이 필요하다. 실제로 `build.gradle.kts`에 `allOpen` 블록이 있다 |
| Java + Spring Boot | 같은 생태계, 플러그인 불필요 | 널 안전성을 타입으로 표현하지 못한다. 데이터 클래스(`SystemState`, `DesignTraits`)를 ARCHITECTURE §6처럼 쓰려면 record나 보일러플레이트가 필요하다 |
| TypeScript + NestJS | 프론트엔드와 언어를 맞춘다. NestJS는 Node.js 위에서 Express(기본) 또는 Fastify를 쓴다 | 저장소에 이 선택을 지지하거나 배제하는 근거가 없다. 운영 엔지니어링(트랜잭션 후 이벤트, 낙관적 상태 전이)을 보여주려는 문서의 의도와는 Spring 쪽이 더 직접 맞는다(재구성) |

**대가**: JVM 기동 시간과 메모리, Kotlin+JPA 플러그인 설정, 그리고 Spring Boot 4/Spring 7 전환기의 함정을 직접 겪었다(`PLAN.md` 1·3단계: 테스트 패키지 이동, `@Transactional`과 `@TransactionalEventListener` 동시 사용 불가, `@Modifying(clearAutomatically = true)`가 flush 없이 비우는 문제).

## 2. 아키텍처 스타일: 모듈러 모놀리스 + 별도 워커

**저장소에 적힌 이유**: `멘토링_서비스_시스템_설계_문서.docx` §3.1과 ARCHITECTURE §1-1. (1) 도메인과 UX가 아직 바뀌므로 실험 속도가 중요하다, (2) 세션·답안·평가 상태를 한 트랜잭션 모델로 다루기 쉽다, (3) 처음부터 MSA를 택할 때의 배포·네트워크·분산 추적·데이터 일관성 비용을 피한다, (4) 모듈 경계를 유지하면 나중에 평가·리포트·콘텐츠를 떼어내기 쉽다. 보안·자원 격리가 필요한 Build Runner와 지연이 큰 AI 평가만 워커로 분리한다.

| 후보 | 비교 |
|---|---|
| 모듈러 모놀리스 + 워커 (채택) | 배포 단위 1개, 패키지 경계 8개(`identity`…`reporting`)를 `c077f21`에서 미리 만들었다 |
| 마이크로서비스 | 위 (3)의 비용. Martin Fowler의 "MonolithFirst"(2015)도 새 프로젝트를 마이크로서비스로 시작하지 말라고 권한다 |
| 경계 없는 단일 모놀리스 | 나중에 평가 워커를 분리할 근거(§13 "Worker의 독립 확장성 우선")를 잃는다(재구성) |

**대가**: 경계를 컴파일러가 강제하지 않는다. 그래서 1단계에서 집계 간 참조를 UUID 스칼라로만 두는 규칙(ADR-0001)을 따로 세웠다. 워커는 같은 JVM 안의 백그라운드 스레드로 시작했고 별도 프로세스 분리는 미뤘다(`PLAN.md` 3단계).

## 3. 주 저장소: PostgreSQL

**저장소에 적힌 이유**: ARCHITECTURE §3, §9: 사용자·시나리오 버전·세션·제출·평가처럼 정합성이 필요한 영속 데이터. §4.1 ERD 원칙: 조회·필터링에 쓰는 값은 정규 컬럼, 자주 바뀌는 scenario rule·simulation effect·AI 구조화 결과는 JSONB.

**대안 비교 (재구성)**: PostgreSQL 문서는 `jsonb`가 분해된 바이너리 형식으로 저장되어 재파싱이 없고 인덱스를 지원한다고 설명하고, 대부분의 애플리케이션에 `jsonb`를 권한다. 첫날 `V3` 마이그레이션은 `(session_id, client_request_id)` 부분 유니크 인덱스(predicate가 있는 인덱스)로 멱등성 키 범위를 좁혔다. 관계형 정합성 + JSON 문서 컬럼 + 부분 유니크 인덱스를 한 엔진에서 쓰는 것이 이 프로젝트의 필요였다. MySQL이나 문서형 DB로 같은 제약을 어떻게 표현할지는 당시 문서가 비교하지 않았고, 이 문서도 추가로 비교하지 않는다.

**대가**: JSONB 컬럼은 Kotlin에서 원시 JSON 문자열(`String?`)로 다루기로 했고(`PLAN.md` 1단계), 타입 안전한 접근은 뒤로 미뤘다.

## 4. 비동기 작업 큐: Redis 리스트 (Kafka 미도입)

**저장소에 적힌 이유**: ARCHITECTURE §3 "MVP에서는 Kafka를 도입하지 않는다… 이벤트를 여러 독립 소비자가 사용하거나 장기 이벤트 보존이 실제 제품 요구가 될 때 Kafka를 검토한다." `멘토링_서비스_시스템_설계_문서.docx` §8: Redis Queue나 SQS로 시작할 수 있고, Kafka는 그 전에는 과도하다. `backend_wargame_integrated_plan_and_design.docx`는 RabbitMQ 또는 Redis Streams를 후보로 적었다. 또 Redis는 세션 캐시·시뮬레이션 입력 저장에 어차피 필요했다(§9).

| 후보 | 성질(1차 출처) | 이 프로젝트 기준 |
|---|---|---|
| Redis 리스트 `RPUSH` + 블로킹 `LPOP` (채택, `c0c391a`) | `BLPOP`은 꺼낸 원소를 리스트에서 지운다. 처리 중 클라이언트가 죽으면 그 원소는 사라진다 | 추가 인프라 0개. 재시도·DLQ 리스트·`submission_id` 멱등성은 앱이 구현 |
| Redis 리스트 + `LMOVE`/`BLMOVE` processing 리스트 | 꺼내는 동시에 processing 리스트로 옮기고 처리 후 `LREM`. 오래 남은 항목을 다시 넣는 감시자가 필요 | 유실은 막지만 감시자를 직접 만들어야 한다 |
| Redis Streams + 소비자 그룹 | 미확인 메시지가 PEL에 남고 `XAUTOCLAIM`으로 회수. at-least-once | 같은 Redis로 가능하지만 API 표면이 커진다 |
| Amazon SQS | 받은 메시지는 visibility timeout(기본 30초) 동안 숨겨지고, 삭제하지 않으면 다시 보인다. DLQ 설정 가능 | 로컬 docker-compose만으로 개발하려는 0단계 기준과 맞지 않는다(재구성) |
| Kafka | 소비 후에도 이벤트를 지우지 않고 토픽별 보존 기간을 둔다. 토픽은 파티션으로 나뉜다 | 보존과 다중 소비자가 아직 요구가 아니다(저장소의 이유) |

**대가**: `BLPOP` 방식은 워커가 job을 꺼낸 뒤 처리 전에 죽으면 그 job을 잃는다. 첫 구현은 이 경우를 다루지 않았다. 또 enqueue를 트랜잭션 안에서 하면 워커가 커밋 전 행을 못 찾는 문제가 있어 `AFTER_COMMIT` 리스너로 옮겼다(ADR-0004).

## 5. 시뮬레이션: 실제 인프라가 아닌 규칙 기반 상태 계산

**저장소에 적힌 이유**: ARCHITECTURE §1-7 "100% digital twin을 약속하지 않는다", §6 `NextState = f(CurrentState, Incident, DesignTraits, AppliedAction, Time)`과 utilization 구간(0~60/60~80/80~95/95+/100+). `backend_wargame_platform_product_plan.docx` §16.2: 사용자마다 실제 Kubernetes·Kafka·Redis 클러스터를 만들면 비용과 운영 복잡도가 급증한다. 실제 런타임(k6, Toxiproxy, 컨테이너 의존성)은 ROADMAP Phase 3로 미뤘다.

| 후보 | 비교 |
|---|---|
| 규칙 기반 순수 함수 (채택, `2da8390`) | 손계산 값과 단위 테스트로 대조 가능(초기 p95 640ms/error 0.30 → 3개 액션 후 80ms/0.001) |
| 세션별 실제 컨테이너 인프라 | 비용·기동 시간. 저장소가 명시적으로 Phase 3로 미룸 |
| 일반화된 데이터 기반 엔진 | 인시던트가 1개일 때는 근거 부족. 콘텐츠가 늘 때 재검토(`PLAN.md` 4단계, 이후 ADR-0010) |

**대가**: 수치가 실제 시스템의 측정값이 아니다. PRD §14는 이를 "장난감처럼 보임" 리스크로 적었다.

## 6. 평가: Rule + LLM 하이브리드

**저장소에 적힌 이유**: ARCHITECTURE §1-2, §7, PRD §9: 결정 가능한 사실(요구사항 누락, 임계값 위반)은 규칙 엔진이, 트레이드오프 타당성·설명·꼬리질문은 LLM이 맡는다. LLM 출력은 구조화 JSON으로 강제하고 검증한 뒤 저장한다.

| 후보 | 비교 |
|---|---|
| 하이브리드 (채택, `8728376`) | 점수는 모델이 보고한 합계를 믿지 않고 차원별로 재계산·클램프 |
| LLM 단독 | PRD §14 "LLM 피드백이 뻔함", 재현성(§1-6) 문제 |
| 규칙 단독 | 트레이드오프 설명과 꼬리질문을 만들 수 없다 |

**대가**: 외부 API 의존과 비용. 키가 없을 때는 오프라인 캔드 응답으로 파이프라인을 계속 돌게 했다(`PLAN.md` 5단계).

## 7. Build 코드 실행 격리: Docker 컨테이너 (gVisor/Firecracker는 다음 단계)

**저장소에 적힌 이유**: ARCHITECTURE §3, §11과 ADR-0007(구현은 2026-08-25 `269ba28`). 사용자 코드를 실행하는 유일한 경로이므로 ephemeral 컨테이너, CPU·메모리·시간 제한, outbound 차단.

| 후보 | 성질(1차 출처) |
|---|---|
| Docker 컨테이너 (채택) | 기본은 자원 제한이 없고 `--cpus`, `--memory`, `--pids-limit`로 건다. `--network none`이면 loopback만 생긴다 |
| gVisor | 사용자 공간의 애플리케이션 커널. 호환성 저하와 시스템 콜당 오버헤드를 대가로 격리를 강화 |
| Firecracker microVM | KVM 기반, 호스트에 하드웨어 가상화와 Linux 4.14+ 필요 |
| 서브프로세스 | 격리가 없다(ADR-0007) |

**대가**: 컨테이너는 호스트 커널을 공유한다. 로컬 개발 머신에서도 돌아야 하는 MVP에서 KVM 요구를 지는 대신 이 위험을 받아들이고, 업그레이드 경로만 문서에 남겼다(재구성).

## 8. 프론트엔드: Next.js (App Router) + Tailwind, Polling

**저장소에 적힌 이유**: `멘토링_서비스_시스템_설계_문서.docx` §16 "데스크톱 중심 설계 인터페이스와 관리 콘솔". 실시간 갱신은 §10과 같은 문서 §11: Polling은 가장 단순해 MVP에 맞고, SSE는 진행 알림이 중요해질 때, WebSocket은 실시간 멀티플레이 전에는 불필요.

**대안 비교 (재구성)**: React 문서는 새 앱을 프레임워크(Next.js App Router 등)로 시작하길 권하고, Vite 등으로 처음부터 만들면 라우팅·데이터 페칭을 직접 골라야 한다고 설명한다. 이 MVP의 화면(온보딩, 대시보드, 워크스페이스, 리포트)은 라우팅이 필요했지만 서버 렌더링이 필요한 근거는 문서에 없다. 실제 화면은 백엔드 API를 클라이언트에서 호출한다. 즉 Next.js를 고른 이유는 기본 구성(라우팅, TypeScript, Tailwind 템플릿)이며, Vite SPA로도 같은 MVP가 가능했다.

**대가**: Next.js 16의 새 동작(async `params`, Turbopack 기본 번들러 등)을 따라가야 한다. 자동저장은 서버 draft API가 없어 localStorage에만 둔다(`PLAN.md` 6단계).

## 9. 배포와 관측

ARCHITECTURE §3, §12, §14는 Container + PaaS/ECS/Fargate(초기 Kubernetes 불필요)와 OpenTelemetry + Prometheus/Grafana, `submission → queue → worker → llm → persistence` 단일 correlation id를 계획했다. 첫날 커밋에는 Actuator 헬스체크와 로컬 docker-compose(PostgreSQL 16, Redis 7)만 있었다. 이 항목은 계획이었고 첫 구현의 선택은 아니다.

## 참고한 1차 출처

- Redis `BLPOP` — https://redis.io/docs/latest/commands/blpop/
- Redis `LMOVE` (reliable queue 패턴) — https://redis.io/docs/latest/commands/lmove/
- Redis Streams — https://redis.io/docs/latest/develop/data-types/streams/
- Amazon SQS visibility timeout — https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html
- Apache Kafka Introduction — https://kafka.apache.org/intro
- Docker resource constraints — https://docs.docker.com/engine/containers/resource_constraints/
- Docker none network driver — https://docs.docker.com/engine/network/drivers/none/
- gVisor — https://gvisor.dev/docs/
- Firecracker — https://firecracker-microvm.github.io/
- Spring Boot 4.1.1 system requirements — https://docs.spring.io/spring-boot/system-requirements.html
- Spring Framework Kotlin null-safety — https://docs.spring.io/spring-framework/reference/languages/kotlin/null-safety.html
- Kotlin all-open / no-arg plugins — https://kotlinlang.org/docs/all-open-plugin.html, https://kotlinlang.org/docs/no-arg-plugin.html
- NestJS — https://docs.nestjs.com/
- PostgreSQL JSON types, partial indexes — https://www.postgresql.org/docs/16/datatype-json.html, https://www.postgresql.org/docs/16/indexes-partial.html
- React, Creating a React App — https://react.dev/learn/creating-a-react-app
- Next.js 16 — https://nextjs.org/blog/next-16
- Martin Fowler, MonolithFirst — https://martinfowler.com/bliki/MonolithFirst.html
