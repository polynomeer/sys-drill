-- docs/COMMUNITY_EXPANSION_PLAN.md C10 (PLAN.md Round E22) — 공개 풀이에 위치가 있는 리뷰.
-- 토론(V37 scenario_discussions)과 같은 신고·숨김 모양을 복제한다 — 가시성 규칙이 달라
-- (풀이는 ADR-0041 게이트 + 비공개 전환 시 함께 숨김) 공용 추상화는 두지 않는다.
create table writeup_comments (
    id uuid primary key default gen_random_uuid(),
    session_id uuid not null references sessions(id),
    author_user_id uuid not null references users(id),
    anchor_type varchar(16) not null,
    anchor_ref varchar(100),
    anchor_label varchar(200),
    kind varchar(16) not null,
    body text not null,
    hidden_at timestamptz,
    hidden_by_user_id uuid references users(id),
    created_at timestamptz not null default now()
);
create index idx_writeup_comments_session on writeup_comments (session_id, created_at);

create table writeup_comment_reports (
    id uuid primary key default gen_random_uuid(),
    comment_id uuid not null references writeup_comments(id),
    reporter_user_id uuid not null references users(id),
    reason varchar(500),
    created_at timestamptz not null default now(),
    unique (comment_id, reporter_user_id)
);
