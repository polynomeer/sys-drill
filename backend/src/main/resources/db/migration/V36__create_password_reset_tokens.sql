-- docs/COMMERCIALIZATION.md — self-service password reset. Same opaque-token
-- shape as organization_invitations (ADR-0022): a random token, an expiry,
-- and a used_at marker instead of deleting the row on use (keeps an audit
-- trail of resets, same reasoning as invitations keeping a status column).
create table password_reset_tokens (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references users (id) on delete cascade,
    token      varchar not null unique,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now()
);
create index idx_password_reset_tokens_user_id on password_reset_tokens (user_id);
