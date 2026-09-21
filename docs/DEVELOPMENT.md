# 개발 가이드

로컬에서 SysDrill을 띄우고, 코드를 고치고, 규칙에 맞게 커밋하기까지의 안내입니다. 제품/아키텍처 배경은 [PRD.md](PRD.md)·[ARCHITECTURE.md](ARCHITECTURE.md), 테스트는 [TESTING.md](TESTING.md)를 보세요.

## 1. 요구 사항

| 도구 | 버전 | 용도 |
|---|---|---|
| Docker Desktop | 최신 | Postgres/Redis/Kafka/Toxiproxy/Jaeger 컨테이너, Build 채점 샌드박스, k6 |
| JDK | 21 | 백엔드 (Gradle toolchain이 21을 요구) |
| Node.js | 22 | 프론트엔드 |
| Python | 3 | `scripts/run.sh`의 포트 검사, Build 과제 로컬 테스트 |

## 2. 실행

```bash
./scripts/run.sh
```

이 스크립트 하나가 **Docker 스택 → 백엔드(8081) → 프론트엔드(3000)** 순서로 띄우고 헬스체크를 기다립니다. 특징:

- 기본 포트가 다른 프로세스에 잡혀 있으면 죽이지 않고 **다음 빈 포트로 우회**하고, 서로의 실제 포트(CORS origin, API base URL, DB/Redis 포트, OTLP 엔드포인트)를 맞춰서 넘깁니다. 이 프로젝트 자신의 이전 컨테이너가 쥔 포트는 충돌로 치지 않습니다.
- Toxiproxy만은 우회하지 않습니다 — 앱이 `20000-20049` 범위를 고정 포트로 직접 참조하기 때문입니다. Toxiproxy가 못 뜨면 경고만 남기고 나머지는 정상 진행합니다 (실제 인프라 파일럿 기능만 비활성).
- `Ctrl+C`는 백엔드/프론트엔드만 종료합니다. Docker 컨테이너는 다른 터미널의 테스트가 계속 쓸 수 있도록 남겨둡니다.
- 로그는 `.run/backend.log`, `.run/frontend.log`.

수동으로 띄우려면:

```bash
docker compose up -d
cd backend && ./gradlew bootRun          # http://localhost:8081/actuator/health
cd frontend && npm install && npm run dev   # http://localhost:3000
```

### 기본 포트

| 서비스 | 호스트 포트 | 비고 |
|---|---|---|
| 백엔드 | 8081 | |
| 프론트엔드 | 3000 | |
| PostgreSQL | **5433** | 다른 로컬 Postgres(5432)와 충돌 회피 |
| Redis | 6379 | |
| Kafka | **19092** | 같은 이유로 9092 대신 |
| Toxiproxy | 8474 (admin), 20000-20049 (세션별 프록시) | 고정 |
| Jaeger UI | 16686 | OTLP 4317/4318 |

### 환경변수

모든 값은 선택이며 기본값만으로 전체 흐름이 동작합니다 (LLM 키가 없으면 스텁 평가, SMTP가 없으면 메일 링크를 로그로 출력).

```bash
cp .env.example backend/.env.local
```

`backend/.env.local`은 `bootRun`이 시작할 때만 환경변수로 읽습니다 (`backend/build.gradle.kts` 참고). `gradlew test`에는 적용되지 않으므로 테스트는 항상 오프라인 폴백 경로를 탑니다. 변수 목록과 설명은 [.env.example](../.env.example)에 있습니다.

## 3. 저장소 구조

```
backend/                      Kotlin · Spring Boot 4 · 모듈러 모놀리스
  src/main/kotlin/com/sysdrill/backend/
    identity, auth            사용자 · JWT · Google OAuth · RBAC
    content, scenario         시나리오 콘텐츠 (Flyway seed) · 마켓플레이스
    session, submission       세션 상태 머신 · 제출
    evaluation                Rule + LLM 하이브리드 평가 · Redis 큐 워커 · 프롬프트 템플릿
    simulation                규칙 기반 엔진 · 토폴로지 · 리플레이
    simulation/realinfra      쿠폰(Postgres+Toxiproxy+k6) · 알림(Kafka) 실제 인프라 파일럿
    build                     Build Mode 채점 (Docker 샌드박스 워커)
    organization, certification, postmortem, mentor, reporting, architecture, admin, mail, tools
  src/main/resources/db/migration/   Flyway — 스키마 + 콘텐츠 시드
frontend/                     Next.js 16 App Router · React 19 · Tailwind 4
  src/app/                    라우트 (design/[sessionId] 가 워크스페이스·워게임·리플레이·포스트모템)
  src/lib/api.ts              백엔드 호출 단일 진입점
challenges/<slug>/            Build 과제 스켈레톤 · stage 테스트 · submit.sh (사용자가 로컬에서 푸는 것)
docs/                         PRD · ARCHITECTURE · ROADMAP · ADR · 이 문서
scripts/                      run.sh · run-tests-isolated.sh · cleanup-stale-kafka-topics.sh
```

## 4. 백엔드 규칙

이 저장소에서 반복적으로 지켜지는 규칙입니다. 각각의 "왜"는 링크된 ADR에 있습니다.

**Flyway 마이그레이션**
- `spring.jpa.hibernate.ddl-auto=validate` — 스키마는 오직 마이그레이션으로만 바뀝니다. 엔티티를 고쳤으면 `V<N>__<slug>.sql`을 같이 추가하세요. `N`은 `db/migration/`에서 가장 큰 번호 + 1.
- 시나리오·Build 과제·프롬프트 템플릿 같은 **콘텐츠도 마이그레이션으로** 들어갑니다 ([ADR-0002](adr/0002-content-via-migrations-not-admin-crud.md), [ADR-0006](adr/0006-config-as-data.md)). 관리자 CRUD 화면은 없습니다.
- 브랜치를 오가며 작업하다 보면 로컬 Postgres에 "이 체크아웃엔 없는 마이그레이션이 적용된" 상태가 생겨 `FlywayValidateException`이 납니다. 모르는 마이그레이션을 `flyway repair`로 지우지 말고, [격리 컨테이너로 테스트](TESTING.md#격리-실행)하거나 볼륨을 새로 만드세요 (`docker compose down -v` — 로컬 데이터가 모두 사라집니다).

**비동기 작업**
- 행을 저장하고 백그라운드 워커에 넘기는 흐름은 **반드시** 트랜잭션 안에서 이벤트만 발행하고 `@TransactionalEventListener(AFTER_COMMIT)`에서 enqueue합니다 ([ADR-0004](adr/0004-async-jobs-enqueued-only-after-commit.md)). `@Transactional` 메서드 안에서 `queue.enqueue()`를 직접 부르지 마세요.
- 워커의 멱등성은 앱 코드의 "이미 있나?" 검사가 아니라 DB 제약으로 보장합니다 ([ADR-0027](adr/0027-evaluation-idempotency-guarded-by-db-constraint-not-just-in-app-dedup-check.md)).

**도메인 모델**
- 애그리거트 경계를 넘는 참조는 JPA 관계가 아니라 **UUID 스칼라 필드**입니다 ([ADR-0001](adr/0001-plain-uuid-scalar-references-across-aggregates.md)).
- 파생값(SystemState, 스킬 추세, 인증 통과 여부)은 저장하지 않고 읽을 때 계산합니다 ([ADR-0011](adr/0011-derived-values-are-never-persisted.md)).
- 새 인시던트 도메인은 기존 것의 이름만 바꾼 복사본이 아니라 다른 병목 메커니즘을 가져야 합니다 ([ADR-0012](adr/0012-new-incident-domains-get-distinct-mechanisms.md)). 시뮬레이션 수치는 `SimulationEngineTest`에 손계산 값으로 고정합니다.

**환경변수 네이밍**
- LLM 자격증명은 `LLM_ANTHROPIC_*` ([ADR-0005](adr/0005-llm-credential-env-var-namespace.md)) — 셸에 이미 있을 수 있는 `ANTHROPIC_*`와 충돌하지 않도록.

## 5. 프론트엔드 규칙

- 백엔드 호출은 전부 [`src/lib/api.ts`](../frontend/src/lib/api.ts)를 거칩니다. `fetch`를 컴포넌트에서 직접 부르지 마세요. 인증 토큰은 `localStorage`의 `sysdrill:token`에서 자동으로 붙습니다.
- 브라우저 로컬 상태(답안 초안, 캔버스 초안, Build 초안)는 `sysdrill:*` 접두사의 `localStorage` 키를 씁니다. 키 목록은 [`src/lib/localSession.ts`](../frontend/src/lib/localSession.ts).
- 캔버스의 노드 종류별 config(`NODE_TRAIT_CONFIG`)는 백엔드 `SystemTopologyService.TOPOLOGY_FIELDS`와 **필드 단위로 미러링**돼야 합니다. 한쪽을 바꾸면 다른 쪽도 바꾸세요.
- 프론트엔드 단위 테스트는 없습니다. CI는 `eslint` + `tsc --noEmit` + `next build` + `npm audit`를 돌립니다.
- 자세한 구조는 [frontend/README.md](../frontend/README.md).

## 6. Build 과제 추가하기

1. `challenges/<slug>/`에 스켈레톤(`<name>.py`), `stages/stageN_test.py`, `README.md`, `submit.sh`를 만듭니다. 기존 `rate-limiter`를 복사해서 시작하세요. 각 stage 테스트는 `RESULT:PASS` / `RESULT:FAIL:<이유>`를 stdout에 찍고 exit code로 결과를 알립니다.
2. 같은 내용을 **시드 마이그레이션**으로 등록합니다 (`V<N>__seed_<slug>_challenge.sql`) — 서버 채점은 파일시스템이 아니라 DB의 스크립트를 씁니다.
3. 채점은 `python:3.12-slim` 컨테이너에서 `--network none`으로 돌아가므로 stage 테스트는 표준 라이브러리만 써야 합니다 ([ADR-0007](adr/0007-docker-sandboxed-build-execution.md), [ADR-0008](adr/0008-python-for-build-challenges.md)).

## 7. 공유 로컬 인프라 주의점

로컬 `docker compose` 스택은 이 저장소의 모든 실행(앱, 테스트, 다른 터미널의 작업)이 공유합니다. 실제로 겪은 문제들:

- **Redis 큐 오염** — 테스트가 넣은 평가 job을 옆에서 돌던 `bootRun`의 워커가 가져가 버리면 테스트가 실패합니다. 반대로 `bootRun`이 남긴 job이 테스트를 오염시키기도 합니다.
- **Toxiproxy 경로는 `DB_PORT`로 격리되지 않습니다** — 실제 인프라 파일럿은 Toxiproxy가 compose 내부 네트워크의 `postgres:5432`에 직접 붙도록 설계돼 있어서, 앱의 DB 포트만 바꿔서는 그 경로가 여전히 공유 Postgres를 봅니다. `scripts/run-tests-isolated.sh`가 격리 Postgres를 compose 네트워크에 별칭으로 붙여 이 문제를 해결합니다.
- **Kafka stale 토픽** — 실제 인프라 알림 테스트가 중간에 죽으면 `realinfra-notify-*` 토픽이 남고, 쌓일수록 메타데이터 전파가 느려져 다음 테스트가 더 잘 실패합니다. `./scripts/cleanup-stale-kafka-topics.sh`로 정리하세요.
- **Docker Desktop VM 자원** — 다른 프로젝트의 컨테이너가 많이 떠 있으면 k6 컨테이너(`--cpus 1.0`)가 CPU 시간을 못 받아 실제 인프라 테스트가 흔들립니다. 코드 문제가 아닙니다.

## 8. 커밋과 ADR

- [Conventional Commits](https://www.conventionalcommits.org/) — `<type>(<scope>): <subject>`. 논리적 작업 단위 하나당 커밋 하나. 규칙 전체는 [CLAUDE.md](../CLAUDE.md).
- 되돌리기 비용이 크고, 맥락 없이 보면 놀랍고, 진짜 대안 중 하나를 고른 결정이면 `docs/adr/`에 ADR을 남깁니다. 세 조건을 모두 만족할 때만입니다. 형식과 판단 기준은 [CLAUDE.md](../CLAUDE.md#아키텍처-결정-기록-adr), 예시는 [adr/README.md](adr/README.md)의 "Start here".
