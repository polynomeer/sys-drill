-- docs/DRILLS_EXPANSION_PLAN.md M12 (PLAN.md Round E27) — 사용자가 도메인별로 키워가는 대응 순서.
-- 세션 파생값이 아니라 사용자 입력이라 저장한다. 다음 같은 도메인 인시던트에서 조사 기록(V61)·조치와 대조된다.
create table user_runbooks (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id),
    domain varchar(64) not null,
    steps jsonb not null default '[]'::jsonb,
    updated_at timestamptz not null default now(),
    unique (user_id, domain)
);
