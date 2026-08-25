# Build your own Distributed Lock

SysDrill Build Mode 과제입니다. `distributed_lock.py`의 `TODO`를 채워 4개 스테이지를 통과시키세요.

## 스테이지

| # | 이름 | 학습 포인트 |
|---|---|---|
| 1 | mutual exclusion | 기본 상호 배제 — 동시에 두 소유자가 같은 락을 가질 수 없다 |
| 2 | lease/TTL 만료 | release 없이도 lease가 지나면 락이 풀려야 하는 이유 |
| 3 | fencing token | 오래 멈췄다 깨어난 소유자(GC pause 등)가 새 소유자의 락에 영향을 주면 안 되는 이유 |
| 4 | 동시성 | 여러 요청이 동시에 acquire를 시도해도 정확히 하나만 성공 |

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

`distributed_lock.py`의 현재 내용을 SysDrill 서버로 보내 4개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 통과 여부와 피드백을 저장합니다. `submit.sh`가 출력하는 URL로 결과를 확인하세요.
