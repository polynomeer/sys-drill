-- ADR-0026 — at-least-once delivery from the Redis evaluation queue means
-- EvaluationWorker's in-transaction "already evaluated?" check can race with
-- a concurrent redelivery (another poll of the same job, or another worker
-- instance): both can see zero active evaluations and both insert. A partial
-- unique index makes the second insert fail at the DB instead of silently
-- producing two active rows for one submission.
create unique index idx_evaluations_one_active_per_submission
    on evaluations (submission_id)
    where is_active;
