# Build your own Queue (Java)

SysDrill Build Mode 과제입니다. `Queue.java`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

이 챌린지는 [`challenges/queue/`](../queue/)의 Python 버전과 같은 4개 스테이지를 다룹니다. 채점 샌드박스는 `eclipse-temurin:25-jdk`에서 이 파일과 테스트 파일을 함께 `javac`로 컴파일한 뒤 실행합니다. 표준 라이브러리만 쓸 수 있고(빌드 도구·외부 의존성 없음), 테스트가 같은 패키지에서 클래스를 부르므로 `package` 선언은 넣지 마세요. `dequeue()`가 돌려주는 `Message` 레코드도 이 파일 안에 함께 있습니다. 클래스 이름이 `java.util.Queue`와 같아서 `import java.util.Queue;`를 넣으면 컴파일 오류가 납니다 — 내부 저장소로는 `ArrayDeque`, `LinkedHashMap` 등을 쓰세요.

만들 큐는 단순한 FIFO가 아니라 SQS 같은 at-least-once 메시지 큐입니다. 꺼낸 메시지는 ack 되거나 visibility timeout이 지날 때까지 다른 컨슈머에게 보이지 않고, ack 없이 timeout이 지나면 다시 전달되며, 최대 재시도 횟수를 넘기면 dead-letter queue로 옮겨집니다. `dequeue()`는 보이는 메시지가 없으면 `null`을, 있으면 `Message(id, payload)`를 돌려줍니다. `visibilityTimeoutSeconds`는 Python 버전처럼 초 단위 `double`입니다.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 기본 FIFO enqueue/dequeue | 큐의 기본 순서 보장 |
| 2 | ack / visibility timeout | at-least-once, 미확인 메시지 재전달 |
| 3 | 최대 재시도 + DLQ | poison message 격리 |
| 4 | 동시성 안전성 (스레드 5개 동시 소비) | 두 컨슈머가 같은 메시지를 동시에 받지 않음 |

각 스테이지의 테스트는 `stages/`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
javac -d /tmp/q Queue.java stages/stage1_test.java && java -cp /tmp/q RunTest
```

## 제출하기

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`Queue.java`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
