-- docs/COMMUNITY_EXPANSION_PLAN.md C13 (PLAN.md Round E31, ADR-0050) — 기간 한정 챌린지.
-- 콘텐츠가 아니라 운영 일정이라 관리자 화면에서 만든다. 공식 시나리오를 가리킬 뿐이다.
create table challenge_events (
    id uuid primary key default gen_random_uuid(),
    title varchar(120) not null,
    scenario_id uuid not null references scenarios(id),
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    created_by uuid not null references users(id),
    created_at timestamptz not null default now(),
    check (ends_at > starts_at)
);
create index idx_challenge_events_period on challenge_events (starts_at, ends_at);
