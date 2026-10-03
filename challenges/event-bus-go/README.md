# Build your own Event Bus (Go)

SysDrill Build Mode 과제입니다. `event_bus.go`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/event-bus/`](../event-bus/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 이 파일과 테스트 파일을 같은 디렉터리에 두고 `go run -race *.go`(Go 1.25)로 실행합니다. **채점은 레이스 디텍터(`go run -race`)로 돕니다** — 결과가 맞아도 여러 고루틴이 동기화 없이 같은 값을 건드리면 그 자리에서 실패하고, 테스트 로그의 `WARNING: DATA RACE` 아래 두 스택이 부딪힌 위치입니다. 표준 라이브러리만 쓸 수 있고(go.mod·외부 모듈 없음), `main` 함수는 테스트 파일에 있으니 이 파일에는 넣지 마세요. `Poll`은 `(Event, bool)`을 돌려주고, 꺼낼 이벤트가 없으면 `Event{}, false`입니다. stage 4는 고루틴 8개가 이벤트 1000개를 한 구독자에서 동시에 `Poll`해 같은 이벤트가 두 번 나가거나 사라지지 않는지 봅니다. `go run`은 `_test.go`로 끝나는 파일을 받지 않아서 스테이지 파일 이름이 `stages/stageN.go`입니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | pub/sub fan-out | 하나의 publish가 해당 topic의 모든 구독자에게 전달됨 |
| 2 | at-least-once delivery | ack 없이 visibility timeout이 지나면 재전달 |
| 3 | ordering | 같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달 |
| 4 | 동시성 | 한 구독자에 대해 여러 고루틴이 동시에 poll해도 중복/유실 없음 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
cp stages/stage1.go . && go run -race event_bus.go stage1.go; rm stage1.go
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`event_bus.go`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
