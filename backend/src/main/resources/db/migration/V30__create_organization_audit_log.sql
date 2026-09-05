-- PLAN.md step 38 — organization-scoped audit log for admin/membership
-- actions (not a platform-wide audit trail; see docs/adr/0029).
create table organization_audit_log_entries (
    id                uuid primary key default gen_random_uuid(),
    organization_id   uuid not null references organizations (id) on delete cascade,
    actor_user_id     uuid not null references users (id),
    action            varchar(40) not null,
    detail            jsonb,
    created_at        timestamptz not null default now()
);
create index idx_org_audit_log_organization_id on organization_audit_log_entries (organization_id);
