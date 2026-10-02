-- docs/OBSERVABILITY_UI_PLAN.md O0-b (PLAN.md Round E17) — 인시던트 중 무엇을 보고 판단했는지.
-- applied_actions와 따로 둔다: 저것은 상태를 바꾸는 입력이고 리플레이 재계산의 원천이다.
-- 조사는 상태를 바꾸지 않으므로 섞으면 재계산 경로가 오염된다. 점수에는 쓰지 않는다(1차는 포스트모템 표시만).
create table investigation_events (
    id uuid primary key default gen_random_uuid(),
    session_id uuid not null references sessions(id),
    kind varchar(32) not null,
    target varchar(200),
    created_at timestamptz not null default now()
);

create index idx_investigation_events_session on investigation_events (session_id, created_at);
