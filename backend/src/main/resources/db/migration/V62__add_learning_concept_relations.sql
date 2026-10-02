-- docs/LEARNING_EXPANSION_PLAN.md L7 (PLAN.md Round E18) — 지식 맵의 엣지.
-- 각 개념 행에 [{key, relation}]. PREREQUISITE는 key가 이 개념의 선행 개념(key → 이 개념),
-- RELATED는 방향 없는 연관이며 한쪽에만 저장한다. 25개 개념 전부가 하나 이상의 엣지에 걸린다(LearningConceptCatalogTest).
alter table learning_concepts add column related_concepts jsonb not null default '[]'::jsonb;

update learning_concepts set related_concepts = '[{"key": "MISSING_RESOURCE_LIMITS", "relation": "PREREQUISITE"}, {"key": "MISSING_OBSERVABILITY", "relation": "PREREQUISITE"}, {"key": "MISSING_ROLLOUT_SAFETY", "relation": "RELATED"}]'::jsonb where risk_key = 'MISSING_AUTOSCALING';
update learning_concepts set related_concepts = '[{"key": "MISSING_RETRY_BACKOFF", "relation": "RELATED"}]'::jsonb where risk_key = 'MISSING_CIRCUIT_BREAKER';
update learning_concepts set related_concepts = '[{"key": "MISSING_RETRY_BACKOFF", "relation": "PREREQUISITE"}, {"key": "MISSING_ASYNC_BOUNDARY", "relation": "PREREQUISITE"}]'::jsonb where risk_key = 'MISSING_DLQ';
update learning_concepts set related_concepts = '[{"key": "MISSING_IDEMPOTENCY", "relation": "PREREQUISITE"}, {"key": "MISSING_ASYNC_BOUNDARY", "relation": "PREREQUISITE"}]'::jsonb where risk_key = 'MISSING_IDEMPOTENT_CONSUMER';
update learning_concepts set related_concepts = '[{"key": "MISSING_CONCURRENCY_CONTROL", "relation": "PREREQUISITE"}, {"key": "MISSING_RESERVATION_TIMEOUT", "relation": "RELATED"}]'::jsonb where risk_key = 'MISSING_INVENTORY_CONSISTENCY';
update learning_concepts set related_concepts = '[{"key": "MISSING_SINGLE_FLIGHT", "relation": "RELATED"}]'::jsonb where risk_key = 'MISSING_KEY_DISTRIBUTION';
update learning_concepts set related_concepts = '[{"key": "MISSING_IDEMPOTENCY", "relation": "PREREQUISITE"}]'::jsonb where risk_key = 'MISSING_PAYMENT_IDEMPOTENCY';
update learning_concepts set related_concepts = '[{"key": "MISSING_RETRY_BACKOFF", "relation": "PREREQUISITE"}]'::jsonb where risk_key = 'MISSING_PG_RETRY_BACKOFF';
update learning_concepts set related_concepts = '[{"key": "MISSING_CIRCUIT_BREAKER", "relation": "RELATED"}, {"key": "MISSING_ASYNC_BOUNDARY", "relation": "RELATED"}]'::jsonb where risk_key = 'MISSING_RATE_LIMIT';
update learning_concepts set related_concepts = '[{"key": "MISSING_CACHE_POLICY_SEPARATION", "relation": "RELATED"}]'::jsonb where risk_key = 'MISSING_READ_REPLICA';
update learning_concepts set related_concepts = '[{"key": "MISSING_IDEMPOTENT_CONSUMER", "relation": "RELATED"}, {"key": "MISSING_RESTARTABILITY", "relation": "RELATED"}]'::jsonb where risk_key = 'MISSING_RECONCILIATION';
update learning_concepts set related_concepts = '[{"key": "MISSING_CONCURRENCY_CONTROL", "relation": "PREREQUISITE"}]'::jsonb where risk_key = 'MISSING_RESERVATION_LOCKING';
update learning_concepts set related_concepts = '[{"key": "MISSING_RESERVATION_LOCKING", "relation": "RELATED"}]'::jsonb where risk_key = 'MISSING_RESERVATION_TIMEOUT';
update learning_concepts set related_concepts = '[{"key": "MISSING_CHUNKING", "relation": "PREREQUISITE"}]'::jsonb where risk_key = 'MISSING_RESTARTABILITY';
update learning_concepts set related_concepts = '[{"key": "MISSING_IDEMPOTENCY", "relation": "PREREQUISITE"}]'::jsonb where risk_key = 'MISSING_RETRY_BACKOFF';
update learning_concepts set related_concepts = '[{"key": "MISSING_OBSERVABILITY", "relation": "PREREQUISITE"}]'::jsonb where risk_key = 'MISSING_ROLLOUT_SAFETY';
update learning_concepts set related_concepts = '[{"key": "MISSING_CACHE_POLICY_SEPARATION", "relation": "PREREQUISITE"}]'::jsonb where risk_key = 'MISSING_SINGLE_FLIGHT';
update learning_concepts set related_concepts = '[{"key": "MISSING_PG_RETRY_BACKOFF", "relation": "RELATED"}, {"key": "MISSING_PAYMENT_IDEMPOTENCY", "relation": "RELATED"}]'::jsonb where risk_key = 'MISSING_TRANSACTION_BOUNDARY';
