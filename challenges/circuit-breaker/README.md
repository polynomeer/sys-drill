# Build your own Circuit Breaker

SysDrill Build Mode 과제입니다. `circuit_breaker.py`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 정상 동작 (CLOSED) | pass-through 기본 동작 |
| 2 | failure threshold 도달 시 OPEN | fail fast — OPEN 상태에서는 실제 함수를 호출하지 않음 |
| 3 | recovery timeout 경과 후 HALF_OPEN 복구 | 언제, 어떻게 재시도를 허용할지 |
| 4 | HALF_OPEN 시도 실패 시 재차단 | 복구 판단이 틀렸을 때의 대응 |

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

`circuit_breaker.py`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 통과 여부와 피드백을 저장합니다. `submit.sh`가 출력하는 URL로 결과를 확인하세요.
