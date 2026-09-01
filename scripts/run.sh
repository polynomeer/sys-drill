#!/usr/bin/env bash
# 프로젝트 전체(Docker 인프라 + 백엔드 + 프론트엔드)를 한 번에 띄우는 스크립트.
#
# - Docker 컴포즈 서비스(postgres/redis/toxiproxy/jaeger/kafka)는 항상
#   --force-recreate로 띄운다 — 이미 떠 있어도 재시작된다.
# - 백엔드(기본 8081)/프론트엔드(기본 3000) 포트가 이미 사용 중이면 죽이지
#   않고 다음 빈 포트로 자동 우회한다. 서로의 실제 포트(CORS origin,
#   API base URL)는 자동으로 맞춰서 넘겨준다.
# - Ctrl+C를 누르면 백엔드/프론트엔드만 종료하고 Docker 컨테이너는 계속
#   띄워둔다(다른 터미널 작업이나 테스트가 그 인프라를 계속 쓸 수 있도록).
#
# 사용법: ./scripts/run.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$REPO_ROOT/.run"
mkdir -p "$RUN_DIR"

BACKEND_LOG="$RUN_DIR/backend.log"
FRONTEND_LOG="$RUN_DIR/frontend.log"

DEFAULT_BACKEND_PORT=8081
DEFAULT_FRONTEND_PORT=3000

BACKEND_PID=""
FRONTEND_PID=""

log()  { printf '\033[1;34m[run]\033[0m %s\n' "$1" >&2; }
warn() { printf '\033[1;33m[run]\033[0m %s\n' "$1" >&2; }
err()  { printf '\033[1;31m[run]\033[0m %s\n' "$1" >&2; }

# Recursively kills a PID's whole descendant tree, not just the PID itself.
# Process-group kill (`kill -PID`) isn't enough here: `gradlew --no-daemon`
# forks its own worker JVM in a NEW session (its own process group), which
# then forks the actual Spring Boot JVM — three processes across two
# unrelated groups, verified with `ps -o pid,ppid,pgid`. Walking by parent
# PID instead of process group reaches all of them regardless of grouping.
kill_tree() {
  local pid=$1
  local child
  for child in $(pgrep -P "$pid" 2>/dev/null || true); do
    kill_tree "$child"
  done
  kill -TERM "$pid" 2>/dev/null || true
}

cleanup() {
  log "종료 신호를 받았습니다 — 백엔드/프론트엔드만 정리합니다 (Docker 컨테이너는 계속 실행됩니다)."
  [[ -n "$FRONTEND_PID" ]] && kill_tree "$FRONTEND_PID"
  [[ -n "$BACKEND_PID" ]] && kill_tree "$BACKEND_PID"
  wait 2>/dev/null || true
  exit 0
}
trap cleanup INT TERM

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

is_port_free() {
  python3 - "$1" <<'PY' 2>/dev/null
import socket, sys
port = int(sys.argv[1])
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
try:
    s.bind(("127.0.0.1", port))
    s.close()
except OSError:
    sys.exit(1)
PY
}

find_free_port() {
  local port=$1
  while ! is_port_free "$port"; do
    port=$((port + 1))
  done
  echo "$port"
}

start_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    err "docker 명령을 찾을 수 없습니다. Docker Desktop을 설치해주세요."
    exit 1
  fi
  if ! docker info >/dev/null 2>&1; then
    err "Docker 데몬에 연결할 수 없습니다. Docker Desktop을 먼저 실행해주세요."
    exit 1
  fi

  log "Docker 서비스 기동 중 (postgres/redis/toxiproxy/jaeger/kafka) — 이미 떠 있으면 재시작합니다..."
  if ! (cd "$REPO_ROOT" && compose up -d --force-recreate --remove-orphans); then
    err "docker compose up 실패 — 포트 충돌일 수 있습니다."
    err "  postgres(5433)/redis(6379)/toxiproxy(8474, 20000-20049)/jaeger(16686, 4317, 4318)/kafka(19092)를"
    err "  다른 프로세스가 쓰고 있지 않은지 'lsof -i :<port>'로 확인해주세요."
    exit 1
  fi

  wait_for_container_healthy postgres 60
  wait_for_container_healthy redis 60
}

wait_for_container_healthy() {
  local service=$1 timeout=$2 waited=0 cid status
  cid=$(cd "$REPO_ROOT" && compose ps -q "$service")
  if [[ -z "$cid" ]]; then
    warn "$service 컨테이너를 찾을 수 없습니다 — 건너뜁니다."
    return
  fi
  log "$service 헬스체크 대기 중..."
  while true; do
    status=$(docker inspect --format='{{.State.Health.Status}}' "$cid" 2>/dev/null || echo "unknown")
    if [[ "$status" == "healthy" ]]; then
      log "$service: healthy"
      return
    fi
    if (( waited >= timeout )); then
      warn "$service 헬스체크가 ${timeout}초 안에 끝나지 않았습니다 — 계속 진행합니다."
      return
    fi
    sleep 2
    waited=$((waited + 2))
  done
}

BACKEND_PORT=""

# Sets the globals $BACKEND_PID/$BACKEND_PORT directly instead of echoing a
# return value — this must NOT be called via `$(...)`. Command substitution
# forks a subshell, and `BACKEND_PID=$!` assigned inside that subshell would
# vanish the instant the substitution finished, leaving the real script with
# an empty BACKEND_PID and no way to find (or clean up) the backend process
# at all — found empirically: `cleanup()` silently skipped killing the
# backend entirely because of exactly this.
start_backend() {
  local frontend_port=$1
  BACKEND_PORT=$(find_free_port "$DEFAULT_BACKEND_PORT")
  if [[ "$BACKEND_PORT" != "$DEFAULT_BACKEND_PORT" ]]; then
    warn "백엔드 기본 포트 $DEFAULT_BACKEND_PORT 사용 중 — $BACKEND_PORT 로 우회합니다."
  fi

  log "백엔드 기동 중 (port $BACKEND_PORT, 로그: $BACKEND_LOG)..."
  (
    cd "$REPO_ROOT/backend"
    export SERVER_PORT="$BACKEND_PORT"
    export FRONTEND_ORIGIN="http://localhost:$frontend_port"
    exec ./gradlew bootRun --no-daemon
  ) > "$BACKEND_LOG" 2>&1 &
  BACKEND_PID=$!

  log "백엔드 준비 대기 중..."
  local waited=0
  until curl -sf -o /dev/null "http://localhost:$BACKEND_PORT/actuator/health" 2>/dev/null; do
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
      err "백엔드 프로세스가 예기치 않게 종료됐습니다. 로그 확인: $BACKEND_LOG"
      tail -n 40 "$BACKEND_LOG" >&2
      exit 1
    fi
    if (( waited >= 180 )); then
      err "백엔드가 180초 안에 기동되지 않았습니다. 로그 확인: $BACKEND_LOG"
      exit 1
    fi
    sleep 3
    waited=$((waited + 3))
  done
  log "백엔드 기동 완료 → http://localhost:$BACKEND_PORT"
}

start_frontend() {
  local backend_port=$1
  local frontend_port=$2

  log "프론트엔드 기동 중 (port $frontend_port, 로그: $FRONTEND_LOG)..."
  (
    cd "$REPO_ROOT/frontend"
    export NEXT_PUBLIC_API_BASE_URL="http://localhost:$backend_port"
    exec npm run dev -- -p "$frontend_port"
  ) > "$FRONTEND_LOG" 2>&1 &
  FRONTEND_PID=$!

  log "프론트엔드 준비 대기 중..."
  local waited=0
  until curl -sf -o /dev/null "http://localhost:$frontend_port" 2>/dev/null; do
    if ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
      err "프론트엔드 프로세스가 예기치 않게 종료됐습니다. 로그 확인: $FRONTEND_LOG"
      tail -n 40 "$FRONTEND_LOG" >&2
      exit 1
    fi
    if (( waited >= 90 )); then
      err "프론트엔드가 90초 안에 기동되지 않았습니다. 로그 확인: $FRONTEND_LOG"
      exit 1
    fi
    sleep 2
    waited=$((waited + 2))
  done
  log "프론트엔드 기동 완료 → http://localhost:$frontend_port"
}

main() {
  start_docker

  local frontend_port
  frontend_port=$(find_free_port "$DEFAULT_FRONTEND_PORT")
  if [[ "$frontend_port" != "$DEFAULT_FRONTEND_PORT" ]]; then
    warn "프론트엔드 기본 포트 $DEFAULT_FRONTEND_PORT 사용 중 — $frontend_port 로 우회합니다."
  fi

  start_backend "$frontend_port"
  start_frontend "$BACKEND_PORT" "$frontend_port"

  echo >&2
  log "모든 서비스가 준비됐습니다."
  log "  프론트엔드   → http://localhost:$frontend_port"
  log "  백엔드       → http://localhost:$BACKEND_PORT"
  log "  Jaeger UI    → http://localhost:16686"
  log "로그: $BACKEND_LOG / $FRONTEND_LOG"
  log "Ctrl+C를 누르면 백엔드·프론트엔드만 종료됩니다 (Docker 컨테이너는 계속 실행됨)."
  echo >&2

  wait
}

main "$@"
