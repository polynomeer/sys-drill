# Build your own Transactional Outbox (Go)

SysDrill Build Mode 과제입니다. `outbox.go`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/outbox/`](../outbox/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 이 파일과 테스트 파일을 같은 디렉터리에 두고 `go run -race *.go`(Go 1.25)로 실행합니다. **채점은 레이스 디텍터(`go run -race`)로 돕니다** — 결과가 맞아도 여러 고루틴이 동기화 없이 같은 값을 건드리면 그 자리에서 실패하고, 테스트 로그의 `WARNING: DATA RACE` 아래 두 스택이 부딪힌 위치입니다. 표준 라이브러리만 쓸 수 있고(go.mod·외부 모듈 없음), `main` 함수는 테스트 파일에 있으니 이 파일에는 넣지 마세요. `go run`은 `_test.go`로 끝나는 파일을 받지 않아서 스테이지 파일 이름이 `stages/stageN.go`입니다.

주문 저장과 이벤트 발행을 따로 하면(dual write) 둘 중 하나만 성공하는 순간이 반드시 옵니다. 이벤트를 주문과 **같은 트랜잭션**으로 outbox 테이블에 쓰고, 별도의 relay가 그것을 브로커로 옮기는 transactional outbox 패턴을 만듭니다. `Database`·`Transaction`(트랜잭션 지원 인메모리 DB)과 `Broker`는 스텁에 완성된 채로 제공됩니다 — 고치지 마세요. 스테이지 테스트가 이들의 장애 훅(`FailNextCommit()`, `FailNextPublish()`, `FailNextMarkPublished()`)을 씁니다. 구현할 것은 `OrderService.PlaceOrder`, `OutboxRelay.RunOnce`, `InventoryConsumer.Handle`입니다.

Python 버전과 다른 점:

- 이벤트는 `Event{ID, Type, Payload}` 구조체이고 payload는 `map[string]any`입니다(`map[string]any{"order_id": orderID, "amount": amount}` — 키 이름은 Python과 같은 `order_id`/`amount`, `amount`는 `int`). 테스트는 payload를 `reflect.DeepEqual`로 값 비교합니다.
- "발행됨" 표시는 이벤트가 아니라 outbox 행의 컬럼이라 `Database` 안에만 있습니다. 아직 표시되지 않은 이벤트는 `db.PendingEvents(limit)`(오래된 순, 복사본)으로, 커밋된 전체 이벤트는 `db.Outbox()`로 봅니다. `db.Orders()`, `db.Commits()`, `broker.Published()`(중복 포함, 보낸 순서)도 메서드입니다.
- 실패는 `error`입니다: `Commit`·`MarkPublished`는 `ErrDatabase`를, `Publish`는 `ErrBroker`를 감싼 에러를 돌려줍니다(테스트는 `errors.Is`로 확인합니다). `PlaceOrder`는 커밋 에러를 그대로 돌려줘야 하고, `RunOnce(batchSize)`는 `(보낸 개수, error)`를 돌려주는데 — 브로커 실패는 에러가 아니라 "여기서 멈춤"이므로 그때까지 보낸 개수와 `nil`을 돌려줘야 합니다. 표시(`MarkPublished`) 실패는 에러로 돌려줘도 됩니다.
- Go에는 기본 인자가 없어서 테스트는 Python의 `run_once()` 대신 `RunOnce(100)`을 부릅니다. 생성자는 `NewDatabase()`, `NewBroker()`, `NewOrderService(db, broker)`, `NewOutboxRelay(db, broker)`, `NewInventoryConsumer(stock)`이고 남은 재고는 `Stock()`으로 읽습니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | one transaction | 주문과 이벤트를 한 트랜잭션에 — 브로커에 직접 쓰지 않는다(dual write 금지) |
| 2 | relay | relay가 커밋된 이벤트를 순서대로 발행하고 표시한다 |
| 3 | broker failure | 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가, 먼저 표시하면 이벤트가 사라진다 |
| 4 | idempotent consumer | 발행 후 표시 전에 죽으면 재발행된다(at-least-once) — 소비자가 이벤트 id로 중복을 거른다 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
cp stages/stage1.go . && go run -race outbox.go stage1.go; rm stage1.go
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`outbox.go`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
