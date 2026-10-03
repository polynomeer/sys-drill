# Build your own Circuit Breaker (Go)

SysDrill Build Mode 과제입니다. `circuit_breaker.go`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/circuit-breaker/`](../circuit-breaker/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 이 파일과 테스트 파일을 같은 디렉터리에 두고 `go run *.go`(Go 1.25)로 실행합니다. 표준 라이브러리만 쓸 수 있고(go.mod·외부 모듈 없음), `main` 함수는 테스트 파일에 있으니 이 파일에는 넣지 마세요.

감싼 함수는 `func() (any, error)`로 받고, `Call`은 그 결과와 에러를 그대로 돌려줍니다 — Python의 예외 대신 nil이 아닌 `error`가 실패입니다. OPEN 상태에서는 함수를 부르지 말고 `ErrCircuitOpen`을 돌려주세요(테스트는 `errors.Is`로 확인합니다). `go run`은 `_test.go`로 끝나는 파일을 받지 않아서 스테이지 파일 이름이 `stages/stageN.go`입니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 정상 동작 (CLOSED) | pass-through 기본 동작 |
| 2 | failure threshold 도달 시 OPEN | fail fast — OPEN 상태에서는 실제 함수를 호출하지 않음 |
| 3 | recovery timeout 경과 후 HALF_OPEN 복구 | 언제, 어떻게 재시도를 허용할지 |
| 4 | HALF_OPEN 시도 실패 시 재차단 | 복구 판단이 틀렸을 때의 대응 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
cp stages/stage1.go . && go run circuit_breaker.go stage1.go; rm stage1.go
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`circuit_breaker.go`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
