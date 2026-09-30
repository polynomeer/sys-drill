#!/usr/bin/env bash
# SysDrill 앱 자체(학습 시뮬레이션이 아니라 실제 공유 인프라)의 트래픽 과부하
# 벤치마크. RPS를 단계적으로 올려가며(10 → 25 → 50 → 100 → 200 req/s, 각
# 30초) /scenarios·/sessions·/submissions를 실제로 때리고, k6의
# --summary-export로 단계별 p50/p95/p99/에러율/달성 RPS를 기록한다. 동시에
# EvaluationQueue(Redis)의 큐 길이를 2초 간격으로 폴링해 EvaluationWorker의
# 단일 스레드 처리가 부하 아래서 얼마나 적체되는지도 함께 남긴다.
#
# 이 저장소엔 실 프로덕션 배포가 없다(docker-compose는 인프라용, 앱 자체는
# 컨테이너가 아닌 순수 JVM 프로세스) — 이 스크립트의 결과는 이 로컬 개발
# 머신 기준이지, 프로덕션 용량 증명이 아니다.
#
# 사전 준비: Docker(k6 이미지 실행용), jq. run-tests-isolated.sh처럼 별도
# 격리 컨테이너를 새로 띄우지 않고, 공유 docker-compose의 postgres/redis를
# 그대로 쓰는 "SERVER_PORT=8084 격리 bootRun" 패턴이다(이 세션 실 검증에서
# 반복 써온 것과 같음) — DB_PORT/REDIS_PORT는 실제 떠 있는 컨테이너의 매핑을
# `docker compose port`로 직접 물어봐서 정하므로 하드코딩하지 않는다.
#
# 사용법:
#   ./scripts/benchmark-traffic.sh              # 전체 5단계
#   ./scripts/benchmark-traffic.sh 5 10s         # sanity-check: RATE=5, DURATION=10s 한 단계만

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_PORT="${BENCHMARK_APP_PORT:-8085}"
TS="$(date +%Y%m%d-%H%M%S)"
RESULTS_DIR="$REPO_ROOT/scripts/benchmark/results/$TS"
BACKEND_LOG="$RESULTS_DIR/backend.log"
K6_SCRIPT="$REPO_ROOT/scripts/benchmark/app-load.js"
K6_IMAGE="${REALINFRA_K6_IMAGE:-grafana/k6}"
QUEUE_KEY="sysdrill:evaluation:jobs"

log()  { printf '\033[1;34m[bench]\033[0m %s\n' "$1" >&2; }
warn() { printf '\033[1;33m[bench]\033[0m %s\n' "$1" >&2; }
err()  { printf '\033[1;31m[bench]\033[0m %s\n' "$1" >&2; }

compose() {
  if docker compose version >/dev/null 2>&1; then
    (cd "$REPO_ROOT" && docker compose "$@")
  else
    (cd "$REPO_ROOT" && docker-compose "$@")
  fi
}

BACKEND_PID=""
QUEUE_POLL_PID=""
ENV_LOCAL="$REPO_ROOT/backend/.env.local"
ENV_LOCAL_MOVED=""
cleanup() {
  log "정리 중..."
  [ -n "$QUEUE_POLL_PID" ] && kill "$QUEUE_POLL_PID" >/dev/null 2>&1 || true
  [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" >/dev/null 2>&1 || true
  if [ -n "$ENV_LOCAL_MOVED" ]; then
    mv "$ENV_LOCAL.benchmark-disabled" "$ENV_LOCAL"
    log ".env.local 복원 완료"
  fi
}
trap cleanup EXIT

# 2026-09-30 사고 재발 방지: bootRun은 backend/.env.local을 환경변수로 로드하는데
# (build.gradle.kts), 여기 실 LLM_ANTHROPIC_API_KEY가 있으면 벤치마크의 가짜
# 트래픽이 실제 Anthropic API를 호출해버린다(과거 실제로 발생 — PLAN.md 참고).
# 셸에서 LLM_ANTHROPIC_API_KEY=를 넘겨도 이 dotenv 로더가 무조건 덮어써서 안 먹히므로,
# 파일 자체를 벤치마크 동안 치워 로더가 아예 못 찾게 한다(가장 확실한 방법).
if [ -f "$ENV_LOCAL" ]; then
  mv "$ENV_LOCAL" "$ENV_LOCAL.benchmark-disabled"
  ENV_LOCAL_MOVED=1
  warn "backend/.env.local을 벤치마크 동안 임시로 치웠습니다(실 API 키 오발동 방지) — 종료 시 자동 복원됩니다."
fi

if ! docker info >/dev/null 2>&1; then
  err "Docker 데몬에 연결할 수 없습니다. Docker Desktop을 먼저 실행해주세요."
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  err "jq가 필요합니다 (brew install jq)."
  exit 1
fi
if lsof -ti:"$APP_PORT" >/dev/null 2>&1; then
  err "포트 $APP_PORT가 이미 다른 프로세스에 점유돼 있습니다(이 스크립트가 만든 게 아니면 건드리지 않습니다)."
  err "BENCHMARK_APP_PORT=<다른 포트>로 다시 실행해주세요."
  exit 1
fi

mkdir -p "$RESULTS_DIR"

log "postgres/redis/jaeger 기동 확인..."
compose up -d postgres redis jaeger >/dev/null

# 컨테이너가 이미(다른 세션/run.sh의 포트 자동 우회로) 떠 있을 수 있어 기본값을
# 가정하지 않고 실제 매핑된 호스트 포트를 직접 물어본다.
DB_PORT="$(compose port postgres 5432 | cut -d: -f2)"
REDIS_PORT="$(compose port redis 6379 | cut -d: -f2)"
log "감지된 포트 — postgres:$DB_PORT, redis:$REDIS_PORT"

log "격리 백엔드 기동 중 (포트 $APP_PORT)... 로그: $BACKEND_LOG"
(
  cd "$REPO_ROOT/backend"
  SERVER_PORT=$APP_PORT FRONTEND_ORIGIN=http://localhost:3002 DB_PORT=$DB_PORT REDIS_PORT=$REDIS_PORT \
    EVALUATION_DAILY_LIMIT_PER_USER=1000000 \
    ./gradlew bootRun --no-daemon >"$BACKEND_LOG" 2>&1 &
  echo $! > "$RESULTS_DIR/backend.pid"
)
BACKEND_PID="$(cat "$RESULTS_DIR/backend.pid")"

log "백엔드 헬스체크 대기 중..."
for i in $(seq 1 60); do
  if curl -sf "http://localhost:$APP_PORT/actuator/health" >/dev/null 2>&1; then
    log "백엔드 기동 완료 (${i}s)"
    break
  fi
  if [ "$i" -eq 60 ]; then
    err "60초 내에 백엔드가 기동되지 않았습니다. $BACKEND_LOG 확인."
    exit 1
  fi
  sleep 1
done

log "벤치마크용 테스트 유저 가입 중..."
SIGNUP_EMAIL="benchmark-$TS@example.com"
SIGNUP_RESPONSE="$(curl -s -X POST "http://localhost:$APP_PORT/auth/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$SIGNUP_EMAIL\",\"password\":\"Benchmark!Pass123\",\"nickname\":\"BenchmarkRunner\",\"termsAccepted\":true}")"
JWT_TOKEN="$(echo "$SIGNUP_RESPONSE" | jq -r '.token')"
if [ -z "$JWT_TOKEN" ] || [ "$JWT_TOKEN" = "null" ]; then
  err "가입/토큰 발급 실패: $SIGNUP_RESPONSE"
  exit 1
fi

log "EvaluationQueue 적체 추이 기록 시작 (2초 간격)..."
QUEUE_CSV="$RESULTS_DIR/queue-depth.csv"
echo "timestamp,depth" > "$QUEUE_CSV"
(
  while true; do
    DEPTH="$(compose exec -T redis redis-cli LLEN "$QUEUE_KEY" 2>/dev/null | tr -d '\r')"
    echo "$(date +%s),${DEPTH:-0}" >> "$QUEUE_CSV"
    sleep 2
  done
) &
QUEUE_POLL_PID=$!

# 인자로 RATE/DURATION을 주면 그 한 단계만(sanity-check용), 아니면 5단계 전체.
if [ $# -ge 1 ]; then
  STAGES="$1:${2:-30s}"
else
  STAGES="10:30s 25:30s 50:30s 100:30s 200:30s"
fi

for STAGE in $STAGES; do
  RATE="${STAGE%%:*}"
  DURATION="${STAGE##*:}"
  log "단계 시작: RATE=$RATE req/s, DURATION=$DURATION"
  docker run --rm \
    --add-host host.docker.internal:host-gateway \
    -v "$K6_SCRIPT:/scripts/app-load.js:ro" \
    -v "$RESULTS_DIR:/out" \
    -e "TARGET_URL=http://host.docker.internal:$APP_PORT" \
    -e "JWT_TOKEN=$JWT_TOKEN" \
    -e "RATE=$RATE" \
    -e "DURATION=$DURATION" \
    "$K6_IMAGE" run --summary-export="/out/summary-$RATE.json" /scripts/app-load.js \
    2>&1 | tee "$RESULTS_DIR/k6-stdout-$RATE.log" | tail -20
  log "단계 완료: RATE=$RATE"
done

log "마지막 단계 이후 큐 드레인 관찰을 위해 60초 더 기록..."
sleep 60
kill "$QUEUE_POLL_PID" >/dev/null 2>&1 || true
QUEUE_POLL_PID=""

log "결과 요약:"
printf '%-8s %-10s %-10s %-10s %-10s %-10s\n' "RATE" "achieved" "p50(ms)" "p95(ms)" "p99(ms)" "errRate"
for STAGE in $STAGES; do
  RATE="${STAGE%%:*}"
  SUMMARY="$RESULTS_DIR/summary-$RATE.json"
  [ -f "$SUMMARY" ] || continue
  jq -r --arg rate "$RATE" '
    [$rate,
     (.metrics.http_reqs.rate // 0 | tostring),
     (.metrics.http_req_duration.med // 0 | tostring),
     (.metrics.http_req_duration."p(95)" // 0 | tostring),
     (.metrics.http_req_duration."p(99)" // 0 | tostring),
     (.metrics.http_req_failed.value // 0 | tostring)]
    | @tsv' "$SUMMARY" | awk -F'\t' '{printf "%-8s %-10s %-10s %-10s %-10s %-10s\n", $1, $2, $3, $4, $5, $6}'
done

log "원본 결과: $RESULTS_DIR"
log "큐 적체 CSV: $QUEUE_CSV"
