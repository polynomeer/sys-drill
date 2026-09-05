-- PLAN.md step 36 — Game Day's spectate-and-chat channel. Persisted (not
-- WebSocket/pub-sub) and polled, same as every other real-time-ish surface
-- in this app so far.
create table session_chat_messages (
    id                uuid primary key default gen_random_uuid(),
    session_id        uuid not null references sessions (id) on delete cascade,
    author_user_id    uuid not null references users (id),
    body              text not null,
    created_at        timestamptz not null default now()
);
create index idx_session_chat_messages_session_id on session_chat_messages (session_id);
