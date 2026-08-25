-- Bridge Mode (PLAN.md step 10): links a session to the Build submission that
-- preceded it, and lets a session's report include that submission's summary.

alter table sessions add column build_submission_id uuid references build_submissions (id);
create index idx_sessions_build_submission_id on sessions (build_submission_id);

alter table reports add column build_summary jsonb;
