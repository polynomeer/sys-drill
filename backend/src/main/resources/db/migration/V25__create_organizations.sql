-- PLAN.md step 32 — Organization/Team data model (Phase 4's first B2B
-- feature). Organization is the aggregate root; OrganizationMembership/
-- OrganizationInvitation are within-aggregate children (cascade on
-- organization_id, per ADR-0001's convention), while user_id/invited_by/
-- created_by are cross-aggregate plain FKs with no cascade (User is a
-- separate aggregate root, mirrors sessions.user_id). Invitation status is
-- deliberately just PENDING/ACCEPTED/REVOKED, no EXPIRED — expiry is a
-- derived fact (expires_at < now()), computed at read time per ADR-0011,
-- not persisted as its own state requiring a sweep job.

create table organizations (
    id          uuid primary key default gen_random_uuid(),
    name        varchar(255) not null,
    created_by  uuid not null references users (id),
    created_at  timestamptz not null default now()
);

create table organization_memberships (
    id               uuid primary key default gen_random_uuid(),
    organization_id  uuid not null references organizations (id) on delete cascade,
    user_id          uuid not null references users (id),
    role             varchar(20) not null default 'MEMBER',
    created_at       timestamptz not null default now(),
    unique (organization_id, user_id)
);
create index idx_organization_memberships_user_id on organization_memberships (user_id);

create table organization_invitations (
    id               uuid primary key default gen_random_uuid(),
    organization_id  uuid not null references organizations (id) on delete cascade,
    invitee_email    varchar(255) not null,
    role             varchar(20) not null default 'MEMBER',
    token            varchar(36) not null unique,
    status           varchar(20) not null default 'PENDING',
    invited_by       uuid not null references users (id),
    expires_at       timestamptz not null,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);
-- Doubles as (a) the "no duplicate concurrent pending invite to the same
-- email in the same org" invariant and (b) the exact index the "list this
-- org's pending invitations" query needs (already filtered to PENDING).
create unique index idx_organization_invitations_org_email_pending
    on organization_invitations (organization_id, invitee_email)
    where status = 'PENDING';
