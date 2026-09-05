-- PLAN.md step 35 — platform-wide RBAC, distinct from OrganizationRole
-- (which is scoped to a single organization). Only gate today is
-- /admin/prompt-templates.
alter table users add column platform_role varchar(20) not null default 'USER';
