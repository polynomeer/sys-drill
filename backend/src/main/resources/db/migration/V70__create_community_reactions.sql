-- docs/COMMUNITY_EXPANSION_PLAN.md C14 (PLAN.md Round E32) — 토론 글·풀이 리뷰에 다는 유형 반응.
-- 도메인별 평판 합계는 읽기 시점에 파생한다(저장하지 않음). Drill Score·랭킹과 무관.
create table community_reactions (
    id uuid primary key default gen_random_uuid(),
    target_type varchar(32) not null,
    target_id uuid not null,
    user_id uuid not null references users(id),
    kind varchar(32) not null,
    created_at timestamptz not null default now(),
    unique (target_type, target_id, user_id, kind)
);
create index idx_community_reactions_target on community_reactions (target_type, target_id);
