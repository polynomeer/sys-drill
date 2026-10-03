# Build your own Consistent Hashing (Go)

SysDrill Build Mode 과제입니다. `consistent_hash.go`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/consistent-hashing/`](../consistent-hashing/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 이 파일과 테스트 파일을 같은 디렉터리에 두고 `go run -race *.go`(Go 1.25)로 실행합니다. **채점은 레이스 디텍터(`go run -race`)로 돕니다** — 결과가 맞아도 여러 고루틴이 동기화 없이 같은 값을 건드리면 그 자리에서 실패하고, 테스트 로그의 `WARNING: DATA RACE` 아래 두 스택이 부딪힌 위치입니다. 표준 라이브러리만 쓸 수 있고(go.mod·외부 모듈 없음), `main` 함수는 테스트 파일에 있으니 이 파일에는 넣지 마세요. `go run`은 `_test.go`로 끝나는 파일을 받지 않아서 스테이지 파일 이름이 `stages/stageN.go`입니다.

키(캐시 키, 세션, 파티션 키)를 여러 서버에 나눠 담을 때, 서버를 하나 늘리거나 빼도 꼭 옮겨야 하는 키만 옮겨 가게 하는 해시 링을 만듭니다. `hash(key) % N`은 노드 하나만 늘어도 키 대부분이 자리를 옮겨 캐시 전체가 한꺼번에 비는 것과 같아집니다. 이 과제는 `대규모 상품 조회` Drill과 Learning의 **Hot Key 분산** 개념으로 이어집니다.

링 위치를 계산하는 `ringHash(value string) uint32`(MD5 앞 4바이트)는 스텁에 제공되며 Python 버전의 `ring_hash()`와 같은 값을 냅니다 — 키와 노드 모두 이 함수로 해시하세요. `hash/maphash`는 프로세스마다 시드가 달라지고, `h*31+c` 같은 간단한 문자열 해시는 `"node-1#7"`과 `"node-1#8"`처럼 비슷한 문자열을 링 위 바로 옆자리에 몰아 놓으니 쓰지 마세요. 가상 노드는 Python처럼 `"node#0"`, `"node#1"`, ... 을 해시해 배치합니다. 기본 인자가 없으므로 `NewHashRing(virtualNodes)`에 가상 노드 수를 직접 넘깁니다(Python 기본값은 100). `GetNode()`는 `(string, bool)`을 돌려주고 링이 비어 있으면 `ok`가 `false`이며, `GetNodes(key, n)`은 `[]string`을 돌려줍니다. 정렬된 위치 목록에서 "키 이상인 첫 위치"는 `slices.BinarySearch`(또는 `sort.Search`)로 찾을 수 있습니다. 테스트는 고루틴을 쓰지 않지만 채점은 똑같이 `-race`로 돕니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | ring lookup | 키에서 시계 방향으로 처음 만나는 노드가 주인 |
| 2 | minimal remapping | 노드가 늘거나 줄어도 그 노드의 몫만 옮겨 간다 |
| 3 | virtual nodes | 노드당 여러 점으로 링 구간을 고르게 편다 |
| 4 | replicas | 복제본은 시계 방향의 서로 다른 노드에 — 주 노드가 빠지면 다음 복제본이 주인 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
cp stages/stage1.go . && go run -race consistent_hash.go stage1.go; rm stage1.go
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`consistent_hash.go`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
