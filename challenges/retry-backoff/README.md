# Build your own Retry/Backoff Middleware

SysDrill Build Mode 과제입니다. `retry_backoff.py`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 기본 재시도 동작 | 실패 시 재시도, 성공하면 즉시 반환 |
| 2 | 재시도 소진 | max_attempts를 넘기면 RetryExhaustedError, 그 이상 시도하지 않음 |
| 3 | exponential backoff + jitter | 지수적으로 커지는 대기 시간과 thundering herd를 막는 지터 |
| 4 | retry budget | 여러 요청이 공유하는 재시도 예산으로 재시도 폭풍 억제 |

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

`retry_backoff.py`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 통과 여부와 피드백을 저장합니다. `submit.sh`가 출력하는 URL로 결과를 확인하세요.
