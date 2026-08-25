# Build your own Queue

SysDrill Build Mode 과제입니다. `queue_impl.py`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 기본 FIFO enqueue/dequeue | 큐의 기본 순서 보장 |
| 2 | ack / visibility timeout | at-least-once, 미확인 메시지 재전달 |
| 3 | 최대 재시도 + DLQ | poison message 격리 |
| 4 | 동시성 안전성 | 두 컨슈머가 같은 메시지를 동시에 받지 않음 |

각 스테이지의 테스트는 `stages/stageN_test.py`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
cp stages/stage1_test.py .
python3 stage1_test.py
```

## 제출하기

```bash
export SYSDRILL_USER_ID=<대시보드/온보딩에서 발급받은 사용자 ID>
./submit.sh
```

`queue_impl.py`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 통과 여부와 피드백을 저장합니다. `submit.sh`가 출력하는 URL로 결과를 확인하세요.
