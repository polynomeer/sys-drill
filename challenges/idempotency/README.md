# Build your own Idempotency Layer

SysDrill Build Mode 과제입니다. `idempotency.py`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

결제처럼 두 번 실행되면 안 되는 작업을 감싸, 같은 멱등성 키로 재시도된 요청(클라이언트 타임아웃 후 재전송, 더블 클릭)이 작업을 최대 한 번만 수행하고 처음 결과를 돌려받게 합니다. 이 과제의 메커니즘은 `결제` Drill의 워게임 액션(멱등 PG 재시도)으로 이어집니다.

Java·Kotlin·Go 판은 [`challenges/idempotency-java`](../idempotency-java/), [`-kotlin`](../idempotency-kotlin/), [`-go`](../idempotency-go/)에 있습니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 결과 재생 | 같은 키의 재시도는 작업을 다시 하지 않고 처음 결과를 돌려준다 |
| 2 | 키 재사용 충돌 | 같은 키에 다른 요청이면 재생이 아니라 오류(클라이언트 버그)다 |
| 3 | 처리 중 중복 | 결과 저장 전(처리 중)에 온 중복도 막는다 — 기다리지 않고 거절 |
| 4 | 실패와 보존 기간 | 실패는 저장하지 않아 재시도가 가능하고, 키는 보존 기간 뒤 잊힌다 |

각 스테이지의 테스트는 `stages/stageN_test.py`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
cp stages/stage1_test.py .
python3 stage1_test.py
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`idempotency.py`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
