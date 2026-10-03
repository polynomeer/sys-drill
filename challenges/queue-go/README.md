# Build your own Queue (Go)

SysDrill Build Mode 과제입니다. `queue.go`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/queue/`](../queue/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 이 파일과 테스트 파일을 같은 디렉터리에 두고 `go run -race *.go`(Go 1.25)로 실행합니다. **채점은 레이스 디텍터(`go run -race`)로 돕니다** — 결과가 맞아도 여러 고루틴이 동기화 없이 같은 값을 건드리면 그 자리에서 실패하고, 테스트 로그의 `WARNING: DATA RACE` 아래 두 스택이 부딪힌 위치입니다. 표준 라이브러리만 쓸 수 있고(go.mod·외부 모듈 없음), `main` 함수는 테스트 파일에 있으니 이 파일에는 넣지 마세요. `go run`은 `_test.go`로 끝나는 파일을 받지 않아서 스테이지 파일 이름이 `stages/stageN.go`입니다.

만들 큐는 단순한 FIFO가 아니라 SQS 같은 at-least-once 메시지 큐입니다. 꺼낸 메시지는 ack 되거나 visibility timeout이 지날 때까지 다른 컨슈머에게 보이지 않고, ack 없이 timeout이 지나면 다시 전달되며, 최대 재시도 횟수를 넘기면 dead-letter queue로 옮겨집니다. `Dequeue()`는 `(Message, bool)`을 돌려주고, 보이는 메시지가 없으면 `ok`가 `false`입니다. visibility timeout은 `time.Duration`으로 받습니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 기본 FIFO enqueue/dequeue | 큐의 기본 순서 보장 |
| 2 | ack / visibility timeout | at-least-once, 미확인 메시지 재전달 |
| 3 | 최대 재시도 + DLQ | poison message 격리 |
| 4 | 동시성 안전성 (고루틴 5개 동시 소비) | 두 컨슈머가 같은 메시지를 동시에 받지 않음 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
cp stages/stage1.go . && go run -race queue.go stage1.go; rm stage1.go
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`queue.go`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
