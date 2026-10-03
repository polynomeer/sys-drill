# Build your own Distributed Lock (Java)

SysDrill Build Mode 과제입니다. `DistributedLock.java`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/distributed-lock/`](../distributed-lock/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 `eclipse-temurin:25-jdk`에서 이 파일과 테스트 파일을 함께 `javac`로 컴파일한 뒤 실행합니다. 표준 라이브러리만 쓸 수 있고(빌드 도구·외부 의존성 없음), 테스트가 같은 패키지에서 클래스를 부르므로 `package` 선언은 넣지 마세요. `DistributedLock` 외의 타입(`LockStore`)도 이 파일 안에 함께 있습니다.

Python 버전에서 "토큰 또는 `None`"이던 반환값은 `Long`(획득 실패 시 `null`)입니다. 같은 `LockStore` 객체를 여러 `DistributedLock`에 넘기는 것이 "여러 인스턴스가 같은 외부 락 서비스(Redis 등)를 바라보는" 상황의 흉내입니다 — stage 4는 새 키 200개마다 스레드 8개가 `acquire`를 동시에 불러, 매번 정확히 하나만 성공하는지 봅니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | mutual exclusion | 기본 상호 배제 — 동시에 두 소유자가 같은 락을 가질 수 없다 |
| 2 | lease/TTL 만료 | release 없이도 lease가 지나면 락이 풀려야 하는 이유 |
| 3 | fencing token | 오래 멈췄다 깨어난 소유자(GC pause 등)가 새 소유자의 락에 영향을 주면 안 되는 이유 |
| 4 | 동시성 (키 200개 × 스레드 8개 동시 acquire) | 여러 요청이 동시에 acquire를 시도해도 정확히 하나만 성공 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
javac -d /tmp/dl DistributedLock.java stages/stage1_test.java && java -cp /tmp/dl RunTest
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`DistributedLock.java`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
