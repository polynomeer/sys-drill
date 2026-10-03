# Build your own Consistent Hashing

SysDrill Build Mode 과제입니다. `consistent_hash.py`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

키(캐시 키, 세션, 파티션 키)를 여러 서버에 나눠 담을 때, 서버를 하나 늘리거나 빼도 꼭 옮겨야 하는 키만 옮겨 가게 하는 해시 링을 만듭니다. `hash(key) % N`은 노드 하나만 늘어도 키 대부분이 자리를 옮겨 캐시 전체가 한꺼번에 비는 것과 같아집니다. 이 과제는 `대규모 상품 조회` Drill과 Learning의 **Hot Key 분산** 개념으로 이어집니다.

링 위치를 계산하는 `ring_hash()`(MD5 앞 4바이트)는 스텁에 제공됩니다 — 키와 노드 모두 이 함수로 해시하세요.

Java·Kotlin·Go 판은 [`challenges/consistent-hashing-java`](../consistent-hashing-java/), [`-kotlin`](../consistent-hashing-kotlin/), [`-go`](../consistent-hashing-go/)에 있습니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | ring lookup | 키에서 시계 방향으로 처음 만나는 노드가 주인 |
| 2 | minimal remapping | 노드가 늘거나 줄어도 그 노드의 몫만 옮겨 간다 |
| 3 | virtual nodes | 노드당 여러 점으로 링 구간을 고르게 편다 |
| 4 | replicas | 복제본은 시계 방향의 서로 다른 노드에 — 주 노드가 빠지면 다음 복제본이 주인 |

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

`consistent_hash.py`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
