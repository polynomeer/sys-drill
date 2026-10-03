# Build your own Event Bus (Kotlin)

SysDrill Build Mode 과제입니다. `EventBus.kt`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/event-bus/`](../event-bus/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 이 파일과 테스트 파일을 함께 `kotlinc`(Kotlin 2.4, JDK 21)로 컴파일한 뒤 실행합니다. Kotlin 표준 라이브러리와 JDK만 쓸 수 있고(kotlinx.coroutines 등 외부 의존성 없음), 테스트가 같은 패키지에서 클래스를 부르므로 `package` 선언은 넣지 마세요. 단계마다 컴파일부터 하므로 채점 한 번에 30초 안팎이 걸립니다. `poll`이 돌려주는 `Event` data class(`id`, `payload`)도 이 파일 안에 함께 있고, 꺼낼 이벤트가 없으면 `null`을 돌려줍니다. stage 4는 스레드 5개가 한 구독자를 동시에 `poll`해 같은 이벤트가 두 번 나가거나 사라지지 않는지 봅니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | pub/sub fan-out | 하나의 publish가 해당 topic의 모든 구독자에게 전달됨 |
| 2 | at-least-once delivery | ack 없이 visibility timeout이 지나면 재전달 |
| 3 | ordering | 같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달 |
| 4 | 동시성 | 한 구독자에 대해 여러 스레드가 동시에 poll해도 중복/유실 없음 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
kotlinc EventBus.kt stages/stage1_test.kt -d /tmp/eb.jar && kotlin -cp /tmp/eb.jar RunTest
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`EventBus.kt`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
