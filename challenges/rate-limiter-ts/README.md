# Build your own Rate Limiter (TypeScript)

SysDrill Build Mode 과제입니다. `rate_limiter.ts`의 `TODO`를 채워 6개 스테이지를 통과시키세요.

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

```bash
export SYSDRILL_TOKEN=<로그인 후 발급받은 JWT>
./submit.sh
```

`rate_limiter.ts`의 현재 내용을 SysDrill 서버로 보내 6개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 통과 여부와 피드백을 저장합니다. `submit.sh`가 출력하는 URL로 결과를 확인하세요.
