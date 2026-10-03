# Build your own Retry/Backoff Middleware (Java)

SysDrill Build Mode 과제입니다. `RetryPolicy.java`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/retry-backoff/`](../retry-backoff/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 `eclipse-temurin:25-jdk`에서 이 파일과 테스트 파일을 함께 `javac`로 컴파일한 뒤 실행합니다. 표준 라이브러리만 쓸 수 있고(빌드 도구·외부 의존성 없음), 테스트가 같은 패키지에서 클래스를 부르므로 `package` 선언은 넣지 마세요. `RetryPolicy` 외의 타입(`RetryBudget`, `RetryExhaustedException`)도 이 파일 안에 함께 있습니다.

`execute`는 `Callable<T>`를 받아, 예외가 나면 실패한 시도로 봅니다. 지연 시간은 Python 버전처럼 초 단위 `double`이고, 테스트는 실제로 기다리지 않도록 `sleepFn`(`DoubleConsumer`)을 넘겨 지연 값을 기록합니다 — `sleepFn`이 `null`이면 실제로 잠들도록 구현하세요.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 기본 재시도 동작 | 실패 시 재시도, 성공하면 즉시 반환 |
| 2 | 재시도 소진 | maxAttempts를 넘기면 RetryExhaustedException, 그 이상 시도하지 않음 |
| 3 | exponential backoff + jitter | 지수적으로 커지는 대기 시간과 thundering herd를 막는 지터 |
| 4 | retry budget | 여러 요청이 공유하는 재시도 예산으로 재시도 폭풍 억제 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
javac -d /tmp/rb RetryPolicy.java stages/stage1_test.java && java -cp /tmp/rb RunTest
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`RetryPolicy.java`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
