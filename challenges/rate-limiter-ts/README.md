# Build your own Rate Limiter (TypeScript)

SysDrill Build Mode 과제입니다. `rate_limiter.ts`의 `TODO`를 채워 6개 스테이지를 통과시키세요.

**1단계는 `allow()` 안 "Stage 1 — uncomment" 아래 주석 처리된 줄들의 주석을 풀고 제출하면 통과합니다** — 제출 흐름을 먼저 한 번 경험해 보세요.

이 챌린지는 [`challenges/rate-limiter/`](../rate-limiter/)의 Python 버전과 같은 6개 스테이지를 다룹니다. 채점 샌드박스는 `node --experimental-strip-types`로 `.ts` 파일을 직접 실행합니다 — **타입만 벗겨낼 뿐 완전한 트랜스파일이 아니므로**, 생성자 파라미터 프로퍼티(`constructor(private x: number)`) 같은 일부 TS 문법은 지원하지 않습니다. `rate_limiter.ts` 스텁처럼 일반 필드 선언 + 생성자 본문에서 대입하는 방식을 쓰세요.

`InMemoryStore`는 (Python 버전과 달리) 이미 완성된 채로 제공됩니다 — `incr()`가 일부러 원자적이지 않게 구현돼 있어(실제 Redis 라운드트립을 흉내낸 await 지점 포함) `RateLimiter.allow()`가 동시 호출에도 안전한지가 stage 3의 진짜 관건입니다. Node는 싱글스레드라 Python의 `threading` 경쟁을 그대로 옮길 수 없어서, 대신 `Promise.all`로 수백 개의 `allow()`를 동시에 날려 async 인터리빙 경쟁을 만듭니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 단일 프로세스 fixed window | 경계 구간 burst 문제 |
| 2 | 윈도우 회복 (sliding/token bucket 감각) | 정확도·메모리 비용 |
| 3 | 동시성 안전성 (async 인터리빙) | atomicity |
| 4 | 공유("분산") 스토어 | 네트워크·Redis 의존성 |
| 5 | fail-open / fail-closed | 가용성과 보호의 trade-off |
| 6 | 운영 metric | reject rate, latency, key skew |

각 스테이지의 테스트는 `stages/stageN_test.ts`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
cp stages/stage1_test.ts .
node --experimental-strip-types stage1_test.ts
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`rate_limiter.ts`의 현재 내용을 SysDrill 서버로 보내 6개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
