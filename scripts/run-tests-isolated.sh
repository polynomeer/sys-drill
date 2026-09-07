#!/usr/bin/env bash
# 다른 세션/반복 실행과 절대 충돌하지 않는 완전 격리 Postgres/Redis를 새로
# 띄워 백엔드 테스트를 돌린다. 이 세션 안에서 반복적으로 겪은 "공유
# docker-compose Postgres/Redis를 그대로 쓰면 Flyway 마이그레이션 충돌이나
# Redis 큐 오염이 생긴다"는 문제를 근본적으로 피한다.
#
# 격리 Postgres를 docker-compose 네트워크(sys-drill_default)에 별칭으로 붙이고
# REALINFRA_TOXIPROXY_PG_UPSTREAM을 그 별칭으로 넘기는 게 핵심이다 —
# RealInfraCouponController의 실전 인프라 파일럿은 Toxiproxy가 앱의 DB_PORT
# 오버라이드와 무관하게 컴포즈 네트워크 안의 고정 서비스명(postgres:5432)에
# 접속하도록 설계돼 있어서(application.yml 주석 참고), 이 별칭 없이 그냥
# DB_PORT만 격리하면 RealInfraCouponControllerSessionTrackingTest /
# RealInfraCouponTracingTest 두 개가 "실제로는 버그가 아닌데" 항상 실패한다
# — 이 세션에서 여러 단계에 걸쳐 "알려진 무관한 실패 2개"로 그냥 넘겨온
# 바로 그 이슈. 이 스크립트로 돌리면 그 2개도 정상 통과한다.
#
# 사용법:
#   ./scripts/run-tests-isolated.sh                 # 전체 테스트
#   ./scripts/run-tests-isolated.sh --tests "com.sysdrill.backend.organization.*"
#   (뒤에 붙는 인자는 그대로 ./gradlew test에 전달된다)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NETWORK="sys-drill_default"
RUN_ID="$$"
PG_CONTAINER="sysdrill-isolated-pg-$RUN_ID"
REDIS_CONTAINER="sysdrill-isolated-redis-$RUN_ID"
PG_ALIAS="sysdrill-isolated-pg-$RUN_ID"

log()  { printf '\033[1;34m[test]\033[0m %s\n' "$1" >&2; }
warn() { printf '\033[1;33m[test]\033[0m %s\n' "$1" >&2; }
err()  { printf '\033[1;31m[test]\033[0m %s\n' "$1" >&2; }

compose() {
  if docker compose version >/dev/null 2>&1; then
    (cd "$REPO_ROOT" && docker compose "$@")
  else
    (cd "$REPO_ROOT" && docker-compose "$@")
  fi
}

cleanup() {
  log "격리 컨테이너 정리 중..."
  docker rm -f "$PG_CONTAINER" "$REDIS_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT

if ! docker info >/dev/null 2>&1; then
  err "Docker 데몬에 연결할 수 없습니다. Docker Desktop을 먼저 실행해주세요."
  exit 1
fi

# toxiproxy/kafka/jaeger는 공유 docker-compose 스택 것을 그대로 쓴다 — 격리가
# 필요한 건 Flyway 마이그레이션과 Redis 큐가 있는 postgres/redis뿐이다.
log "toxiproxy/kafka/jaeger 기동 확인 (이미 떠 있으면 그대로 둔다)..."
compose up -d toxiproxy kafka jaeger >/dev/null

log "격리 Postgres/Redis 기동 중 ($PG_CONTAINER, $REDIS_CONTAINER)..."
docker run -d --name "$PG_CONTAINER" \
  -e POSTGRES_DB=sysdrill -e POSTGRES_USER=sysdrill -e POSTGRES_PASSWORD=sysdrill \
  -P postgres:16 -c max_connections=300 >/dev/null
docker run -d --name "$REDIS_CONTAINER" -P redis:7 >/dev/null

log "격리 Postgres를 $NETWORK 네트워크에 별칭 $PG_ALIAS 로 연결 (Toxiproxy가 접속할 수 있도록)..."
docker network connect "$NETWORK" "$PG_CONTAINER" --alias "$PG_ALIAS"

DB_PORT=$(docker port "$PG_CONTAINER" 5432/tcp | head -1 | cut -d: -f2)
REDIS_PORT=$(docker port "$REDIS_CONTAINER" 6379/tcp | head -1 | cut -d: -f2)

log "Postgres 준비 대기 중 (port $DB_PORT)..."
waited=0
until docker exec "$PG_CONTAINER" pg_isready -U sysdrill >/dev/null 2>&1; do
  if (( waited >= 60 )); then
    err "Postgres가 60초 안에 준비되지 않았습니다."
    exit 1
  fi
  sleep 2
  waited=$((waited + 2))
done

log "테스트 실행 중 (DB_PORT=$DB_PORT REDIS_PORT=$REDIS_PORT REALINFRA_TOXIPROXY_PG_UPSTREAM=$PG_ALIAS:5432)..."
cd "$REPO_ROOT/backend"
DB_PORT="$DB_PORT" REDIS_PORT="$REDIS_PORT" REALINFRA_TOXIPROXY_PG_UPSTREAM="$PG_ALIAS:5432" \
  ./gradlew test --no-daemon "$@"
