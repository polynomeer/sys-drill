# Build your own Event Bus

SysDrill Build Mode 과제입니다. `event_bus.py`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | pub/sub fan-out | 하나의 publish가 해당 topic의 모든 구독자에게 전달됨 |
| 2 | at-least-once delivery | ack 없이 visibility timeout이 지나면 재전달 |
| 3 | ordering | 같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달 |
| 4 | 동시성 | 한 구독자에 대해 여러 스레드가 동시에 poll해도 중복/유실 없음 |

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

`event_bus.py`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 통과 여부와 피드백을 저장합니다. `submit.sh`가 출력하는 URL로 결과를 확인하세요.
