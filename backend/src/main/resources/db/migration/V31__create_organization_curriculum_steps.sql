-- PLAN.md step 39 — a single ordered onboarding curriculum per organization
-- (not multiple tracks/assignment — see docs/adr/0030). Ordered rows with a
-- step_order column, same convention as scenario_steps, not a jsonb array.
create table organization_curriculum_steps (
    id                uuid primary key default gen_random_uuid(),
    organization_id   uuid not null references organizations (id) on delete cascade,
    scenario_id       uuid not null references scenarios (id),
    step_order        int not null,
    created_at        timestamptz not null default now(),
    unique (organization_id, step_order)
);
create index idx_org_curriculum_steps_organization_id on organization_curriculum_steps (organization_id);
