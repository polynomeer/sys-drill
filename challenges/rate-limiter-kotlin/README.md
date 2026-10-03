# Build your own Rate Limiter (Kotlin)

SysDrill Build Mode 과제입니다. `RateLimiter.kt`의 `TODO`를 채워 6개 스테이지를 통과시키세요.

**1단계는 "Stage 1 — uncomment" 아래 주석 처리된 세 줄의 주석을 풀고, 그 아래 `TODO("not implemented")` 줄을 지운 뒤 제출하면 통과합니다** — 제출 흐름을 먼저 한 번 경험해 보세요.

이 챌린지는 [`challenges/rate-limiter/`](../rate-limiter/)의 Python 버전과 같은 6개 스테이지를 다룹니다. 채점 샌드박스는 이 파일과 테스트 파일을 함께 `kotlinc`(Kotlin 2.4, JDK 21)로 컴파일한 뒤 실행합니다. Kotlin 표준 라이브러리와 JDK만 쓸 수 있고(kotlinx.coroutines 등 외부 의존성 없음), 테스트가 같은 패키지에서 클래스를 부르므로 `package` 선언은 넣지 마세요. 단계마다 컴파일부터 하므로 채점 한 번에 30초 안팎이 걸립니다.

Python 버전처럼 `InMemoryStore.expire`는 비어 있어 윈도우 회복(stage 2)을 직접 구현해야 합니다. 반면 `incr`는 읽기와 쓰기 사이에 네트워크 왕복을 흉내 낸 지연이 있는 **원자적이지 않은** 구현으로 제공됩니다(Redis에 GET 후 SET 하는 것과 같습니다) — stage 3은 스레드로 `allow`를 동시에 불러, 동시성 제어 없이 이 지연을 그대로 노출하는 구현을 걸러냅니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 단일 프로세스 fixed window | 경계 구간 burst 문제 |
| 2 | 윈도우 회복 (sliding/token bucket 감각) | 정확도·메모리 비용 |
| 3 | 동시성 안전성 (스레드 200회 동시 호출) | atomicity |
| 4 | 공유("분산") 스토어 | 네트워크·Redis 의존성 |
| 5 | fail-open / fail-closed | 가용성과 보호의 trade-off |
| 6 | 운영 metric | reject rate, latency, key skew |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
kotlinc RateLimiter.kt stages/stage1_test.kt -d /tmp/rl.jar && kotlin -cp /tmp/rl.jar RunTest
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`RateLimiter.kt`의 현재 내용을 SysDrill 서버로 보내 6개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
