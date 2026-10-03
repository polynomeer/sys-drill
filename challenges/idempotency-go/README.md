# Build your own Idempotency Layer (Go)

SysDrill Build Mode 과제입니다. `idempotency.go`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/idempotency/`](../idempotency/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 이 파일과 테스트 파일을 같은 디렉터리에 두고 `go run -race *.go`(Go 1.25)로 실행합니다. **채점은 레이스 디텍터(`go run -race`)로 돕니다** — 결과가 맞아도 여러 고루틴이 동기화 없이 같은 값을 건드리면 그 자리에서 실패하고, 테스트 로그의 `WARNING: DATA RACE` 아래 두 스택이 부딪힌 위치입니다. 표준 라이브러리만 쓸 수 있고(go.mod·외부 모듈 없음), `main` 함수는 테스트 파일에 있으니 이 파일에는 넣지 마세요. `go run`은 `_test.go`로 끝나는 파일을 받지 않아서 스테이지 파일 이름이 `stages/stageN.go`입니다.

결제처럼 두 번 실행되면 안 되는 작업을 감싸, 같은 멱등성 키로 재시도된 요청(클라이언트 타임아웃 후 재전송, 더블 클릭)이 작업을 최대 한 번만 수행하고 처음 결과를 돌려받게 합니다.

Python의 예외 대신 error 값을 씁니다. `Execute(key, request, operation)`의 작업은 `func() (any, error)`이고, 작업이 돌려준 error가 실패입니다 — 그 error를 그대로 돌려주고 아무것도 저장하지 마세요. 충돌과 처리 중 거절은 sentinel error `ErrIdempotencyConflict`, `ErrIdempotencyInProgress`로 알리고 테스트는 `errors.Is`로 확인하므로, 키 같은 정보를 덧붙이려면 `fmt.Errorf("%w: ...", ErrIdempotencyConflict)`처럼 감싸세요. `request`는 `map[string]any`이고 **`reflect.DeepEqual`로 값 비교**합니다 — 맵은 `==`로 비교할 수 없고, 테스트는 매번 새로 만든 맵을 넘깁니다. 보존 기간은 `NewIdempotencyLayer(ttl time.Duration)`으로 받습니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 결과 재생 | 같은 키의 재시도는 작업을 다시 하지 않고 처음 결과를 돌려준다 |
| 2 | 키 재사용 충돌 | 같은 키에 다른 요청이면 재생이 아니라 오류(클라이언트 버그)다 |
| 3 | 처리 중 중복 | 결과 저장 전(처리 중)에 온 중복도 막는다 — 기다리지 않고 거절 |
| 4 | 실패와 보존 기간 | 실패는 저장하지 않아 재시도가 가능하고, 키는 보존 기간 뒤 잊힌다 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
cp stages/stage1.go . && go run -race idempotency.go stage1.go; rm stage1.go
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`idempotency.go`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
