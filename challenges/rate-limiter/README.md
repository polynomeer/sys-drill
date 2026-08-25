# Build your own Rate Limiter

SysDrill Build Mode 과제입니다. `rate_limiter.py`의 `TODO`를 채워 6개 스테이지를 통과시키세요.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | 단일 프로세스 fixed window | 경계 구간 burst 문제 |
| 2 | 윈도우 회복 (sliding/token bucket 감각) | 정확도·메모리 비용 |
| 3 | 동시성 안전성 | atomicity |
| 4 | 공유("분산") 스토어 | 네트워크·Redis 의존성 |
| 5 | fail-open / fail-closed | 가용성과 보호의 trade-off |
| 6 | 운영 metric | reject rate, latency, key skew |

각 스테이지의 테스트는 `stages/stageN_test.py`에 있습니다. 로컬에서 직접 실행해 확인할 수 있습니다.

```bash
python3 -c "import sys; sys.path.insert(0, '.'); exec(open('stages/stage1_test.py').read())"
```

또는 심볼릭 링크/복사로 `rate_limiter.py`와 같은 디렉터리에서 실행해도 됩니다:

```bash
cp stages/stage1_test.py .
python3 stage1_test.py
```

## 제출하기

```bash
export SYSDRILL_USER_ID=<대시보드/온보딩에서 발급받은 사용자 ID>
./submit.sh
```

`rate_limiter.py`의 현재 내용을 SysDrill 서버로 보내 6개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 통과 여부와 피드백을 저장합니다. `submit.sh`가 출력하는 URL로 결과를 확인하세요.
