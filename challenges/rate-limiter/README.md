# Build your own Rate Limiter

SysDrill Build Mode 과제입니다. `rate_limiter.py`의 `TODO`를 채워 6개 스테이지를 통과시키세요.

**1단계는 `allow()` 안 "Stage 1 — uncomment" 아래 주석 처리된 줄들의 주석을 풀고 제출하면 통과합니다** — 제출 흐름을 먼저 한 번 경험해 보세요.

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

웹 에디터(`/bridge`) 대신 자기 에디터에서 풀고 터미널에서 제출할 수 있습니다. `/bridge`의 **로컬에서 풀기** 패널에서 토큰을 복사해 설정하세요.

```bash
export SYSDRILL_TOKEN=<"로컬에서 풀기" 패널에서 복사한 토큰>
export SYSDRILL_API_BASE_URL=<API 주소, 기본값 http://localhost:8081>
./submit.sh
```

`rate_limiter.py`의 현재 내용을 SysDrill 서버로 보내 6개 스테이지를 격리된 샌드박스(Docker, 네트워크 차단, CPU/메모리 제한)에서 실행하고, 단계별 결과를 `[stage-N]` 형식으로 터미널에 바로 보여줍니다. 같은 결과가 `/bridge` 화면의 테스트 로그와 단계 목록에도 반영됩니다.

토큰은 로그인 세션 토큰이라 비밀번호처럼 다루세요 — 저장소에 커밋하거나 공유하지 마세요. 만료되면(HTTP 401) 패널에서 다시 복사하면 됩니다.
