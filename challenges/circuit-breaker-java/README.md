# Build your own Circuit Breaker (Java)

SysDrill Build Mode 과제입니다. `CircuitBreaker.java`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/circuit-breaker/`](../circuit-breaker/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 `eclipse-temurin:25-jdk`에서 이 파일과 테스트 파일을 함께 `javac`로 컴파일한 뒤 실행합니다. 표준 라이브러리만 쓸 수 있고(빌드 도구·외부 의존성 없음), 테스트가 같은 패키지에서 클래스를 부르므로 `package` 선언은 넣지 마세요. `CircuitBreaker` 외의 타입(`State`, `CircuitOpenException`)도 이 파일 안에 함께 있습니다.

감싼 함수는 `java.util.concurrent.Callable<T>`로 받습니다. 함수가 던진 예외는 실패로 세고 그대로 호출자에게 다시 던지세요 — 테스트는 그 예외(`IllegalArgumentException`)를 직접 받습니다. OPEN 상태에서는 함수를 부르지 말고 `CircuitOpenException`을 던집니다. `recoveryTimeoutSeconds`는 Python 버전처럼 초 단위 `double`입니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 정상 동작 (CLOSED) | pass-through 기본 동작 |
| 2 | failure threshold 도달 시 OPEN | fail fast — OPEN 상태에서는 실제 함수를 호출하지 않음 |
| 3 | recovery timeout 경과 후 HALF_OPEN 복구 | 언제, 어떻게 재시도를 허용할지 |
| 4 | HALF_OPEN 시도 실패 시 재차단 | 복구 판단이 틀렸을 때의 대응 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
javac -d /tmp/cb CircuitBreaker.java stages/stage1_test.java && java -cp /tmp/cb RunTest
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`CircuitBreaker.java`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
