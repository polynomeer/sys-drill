-- docs/ARCHITECTURE.md §7: the AI's structured output includes fields our V1
-- schema didn't yet have columns for (followup_questions, recommended_changes),
-- plus a minimal slice of §7.1's "반드시 저장해야 할 AI 메타데이터" — just enough
-- for basic traceability (which model produced this, how long it took). Token
-- counts/cost tracking are deferred until something actually needs them.

alter table evaluations
    add column followup_questions jsonb,
    add column recommended_changes jsonb,
    add column model_provider varchar(50),
    add column model_name varchar(100),
    add column latency_ms int;
