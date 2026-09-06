#!/usr/bin/env bash
# 공유 docker-compose Kafka 컨테이너(sys-drill-kafka-1)에 남은 stale
# realinfra-notify-* 토픽을 정리한다.
#
# NotificationTopicProvisionerTest가 assert 실패로 중간에 죽으면 (혹은 실행
# 중 강제 종료되면) provisioner.drop()이 실행되지 않아 토픽이 영구히 남는다.
# 반복 실행될수록 이런 토픽이 쌓이고, listTopics() 응답이 커지면서 메타데이터
# 전파가 느려져 다음 테스트가 더 잘 실패하는 악순환이 생긴다 — 이 스크립트는
# 그 누적분을 정리한다.
#
# Kafka는 KAFKA_ADVERTISED_LISTENERS=localhost:<KAFKA_PORT>로 떠 있어서
# 컨테이너 안(docker exec)에서는 그 주소가 리졸브되지 않는다. 그래서 이
# 스크립트는 (테스트가 하듯) 호스트에서 직접 JVM으로 접속한다.
#
# 사용법:
#   ./scripts/cleanup-stale-kafka-topics.sh          # 목록 확인 후 y/N 확인
#   ./scripts/cleanup-stale-kafka-topics.sh --yes    # 확인 없이 바로 삭제
#
# 환경변수:
#   KAFKA_PORT (기본 19092) — scripts/run.sh와 동일한 기본값

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOOTSTRAP_SERVERS="localhost:${KAFKA_PORT:-19092}"

GRADLE_ARGS=(-PbootstrapServers="$BOOTSTRAP_SERVERS")
if [[ "${1:-}" == "--yes" ]]; then
  GRADLE_ARGS+=(-Pyes)
fi

cd "$REPO_ROOT/backend"
./gradlew -q cleanupStaleKafkaTopics "${GRADLE_ARGS[@]}"
