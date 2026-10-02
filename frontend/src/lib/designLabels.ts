/**
 * PLAN.md Round E15 (C8) — Korean labels for canvas node kinds and the topology-driven
 * DesignTraits fields, for the writeup summary and comparison. Same names as
 * DiagramCanvas's NODE_KIND_META / NODE_TRAIT_CONFIG (and the backend TOPOLOGY_FIELDS).
 */
export const NODE_KIND_LABELS: Record<string, string> = {
  client: "Client",
  gateway: "API Gateway",
  service: "Service",
  db: "DB",
  cache: "Cache",
  queue: "Queue",
  cdn: "CDN",
};

export const TRAIT_LABELS: Record<string, string> = {
  cacheTtlSeconds: "캐시 TTL(초)",
  dbPoolSize: "DB 커넥션 풀",
  consumerCount: "컨슈머 수",
  readReplicaCount: "Read Replica 수",
  dispatcherWorkers: "디스패처 워커",
  holdTimeoutSeconds: "홀드 타임아웃(초)",
  chunkSize: "청크 크기",
  podReplicas: "Pod 수",
};
