# Build your own Cache (Kotlin)

SysDrill Build Mode 과제입니다. `Cache.kt`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/cache/`](../cache/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 이 파일과 테스트 파일을 함께 `kotlinc`(Kotlin 2.4, JDK 21)로 컴파일한 뒤 실행합니다. Kotlin 표준 라이브러리와 JDK만 쓸 수 있고(kotlinx.coroutines 등 외부 의존성 없음), 테스트가 같은 패키지에서 클래스를 부르므로 `package` 선언은 넣지 마세요. 단계마다 컴파일부터 하므로 채점 한 번에 30초 안팎이 걸립니다.

상품 조회처럼 읽기가 몰리는 서비스 앞에 두는 읽기 캐시를 만듭니다. TTL과 LRU로 메모리를 관리하고, hot key가 만료된 순간 몰린 요청이 전부 DB로 가는 **cache stampede**를 single-flight로 막습니다.

Python 버전과 다른 점: `get()`은 없거나 만료된 키에 `null`을 돌려줍니다. TTL은 Python처럼 초 단위 `Double`(`ttlSeconds`)입니다. Kotlin 관례대로 loader를 마지막 인자로 옮겨 `getOrLoad(key, ttlSeconds) { ... }`처럼 trailing lambda로 넘길 수 있습니다(loader 타입은 `() -> Any?`). `stats()`는 dict 대신 `Stats(hits, misses, hitRatio)` data class를 돌려줍니다. `capacity`의 기본값은 100입니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | TTL get/set | 캐시 값의 수명 — TTL이 지나면 원본을 다시 읽는다 |
| 2 | LRU eviction | 유한한 메모리 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다 |
| 3 | single-flight (cache stampede) | hot key 만료 순간의 동시 miss를 로드 한 번으로 합친다 |
| 4 | invalidation + hit ratio | 원본이 바뀌면 지우고, hit ratio로 TTL·용량을 튜닝한다 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
kotlinc Cache.kt stages/stage1_test.kt -d /tmp/c.jar && kotlin -cp /tmp/c.jar RunTest
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`Cache.kt`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
