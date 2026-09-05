-- PLAN.md step 34 — org-scoped custom scenarios coexist with the public,
-- Flyway-seeded ones (docs/adr/0002). null = public; non-null = that
-- organization's private scenario, authored via API instead of a migration.
alter table scenarios add column organization_id uuid null references organizations (id) on delete cascade;
create index idx_scenarios_organization_id on scenarios (organization_id);
