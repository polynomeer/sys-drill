# Build your own Idempotency Layer (Kotlin)

SysDrill Build Mode 과제입니다. `IdempotencyLayer.kt`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/idempotency/`](../idempotency/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 이 파일과 테스트 파일을 함께 `kotlinc`(Kotlin 2.4, JDK 21)로 컴파일한 뒤 실행합니다. Kotlin 표준 라이브러리와 JDK만 쓸 수 있고(kotlinx.coroutines 등 외부 의존성 없음), 테스트가 같은 패키지에서 클래스를 부르므로 `package` 선언은 넣지 마세요. 단계마다 컴파일부터 하므로 채점 한 번에 30초 안팎이 걸립니다. 두 예외 클래스 `IdempotencyConflictException`, `IdempotencyInProgressException`(둘 다 `RuntimeException`)도 이 파일 안에 함께 있습니다.

결제처럼 두 번 실행되면 안 되는 작업을 감싸, 같은 멱등성 키로 재시도된 요청(클라이언트 타임아웃 후 재전송, 더블 클릭)이 작업을 최대 한 번만 수행하고 처음 결과를 돌려받게 합니다.

`execute(key, request, operation)`의 작업은 `() -> T`이고, 작업이 던진 예외는 그대로 호출자에게 전파돼야 합니다. `request`는 `Any`이고 **`==`(`equals`)로 값 비교**합니다 — 테스트는 `mapOf("amount" to 1000)`처럼 매번 새로 만든 `Map`을 넘기므로 `===`(참조 비교)로는 같은 요청을 알아보지 못합니다. 보존 기간 `ttlSeconds`는 Python 버전처럼 초 단위 `Double`이고 기본값은 86400초(하루)입니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 결과 재생 | 같은 키의 재시도는 작업을 다시 하지 않고 처음 결과를 돌려준다 |
| 2 | 키 재사용 충돌 | 같은 키에 다른 요청이면 재생이 아니라 오류(클라이언트 버그)다 |
| 3 | 처리 중 중복 | 결과 저장 전(처리 중)에 온 중복도 막는다 — 기다리지 않고 거절 |
| 4 | 실패와 보존 기간 | 실패는 저장하지 않아 재시도가 가능하고, 키는 보존 기간 뒤 잊힌다 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
kotlinc IdempotencyLayer.kt stages/stage1_test.kt -d /tmp/idem.jar && kotlin -cp /tmp/idem.jar RunTest
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`IdempotencyLayer.kt`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
