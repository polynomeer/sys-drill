-- docs/COMMUNITY_EXPANSION_PLAN.md C12 (PLAN.md Round E28) — 주간 "What Would You Do?" 응답.
-- 퍼즐 자체는 저장하지 않는다(주차가 seed인 L9 생성기). 한 주에 한 번, 수정 없음.
create table wwyd_answers (
    id uuid primary key default gen_random_uuid(),
    week int not null,
    user_id uuid not null references users(id),
    choice varchar(32) not null,
    reason varchar(200),
    reason_public boolean not null default false,
    created_at timestamptz not null default now(),
    unique (week, user_id)
);
