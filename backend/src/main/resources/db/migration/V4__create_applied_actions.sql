-- docs/ARCHITECTURE.md §4.1: "정규화된 사용자 조치" — the permanent decision log
-- for actions taken during a wargame. The live SystemState/DesignTraits used to
-- compute effects live in Redis (docs/ARCHITECTURE.md §9: "실시간 시뮬레이션 상태");
-- this table is the durable record of what was applied and when.

create table applied_actions (
    id            uuid primary key default gen_random_uuid(),
    session_id    uuid not null references sessions (id) on delete cascade,
    action_type   varchar(50) not null,
    target        varchar(100),
    parameters    jsonb,
    effect        text,
    created_at    timestamptz not null default now()
);
create index idx_applied_actions_session_id on applied_actions (session_id);
