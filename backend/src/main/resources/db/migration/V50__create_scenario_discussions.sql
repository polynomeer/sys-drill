-- 슬라이스 7 (docs/LEARNING_COMMUNITY_PLAN.md §6.5, docs/adr/0040) — 시나리오별 토론.
--
-- session_chat_messages 를 참조 구현으로 삼는다(ADR-0040): 한 컨테이너에 달린
-- 평평한 메시지 목록, 시간순 조회, 폴링. 스레드/대댓글은 넣지 않는다 — 시나리오
-- 하나당 스레드 하나라는 범위 제한 자체가 depth 를 대신한다.
create table scenario_discussions (
    id uuid primary key default gen_random_uuid(),

    -- 시나리오가 아니라 **버전**에 매단다(ADR-0040). 새 버전은 인시던트나 채점
    -- 기준이 달라질 수 있어서, 옛 버전의 문답이 새 버전 학습자를 잘못 이끌 수 있다.
    scenario_version_id uuid not null references scenario_versions(id),

    author_user_id uuid not null references users(id),
    body text not null,

    -- 공개 풀이를 인용한 댓글(ADR-0040 마지막 줄). 인용 대상은 ADR-0041 의 열람
    -- 조건을 그대로 따르므로, 미완료자에게는 인용이 잠긴 채로 내려간다.
    quoted_session_id uuid references sessions(id),

    -- 모더레이션: 신고 → PLATFORM_ADMIN 검토 → 숨김. 지우지 않고 숨기는 이유는
    -- 오판을 되돌릴 수 있어야 하고, 무엇을 왜 숨겼는지 남아야 하기 때문이다.
    hidden_at timestamptz,
    hidden_by_user_id uuid references users(id),

    created_at timestamptz not null default now()
);

create index idx_scenario_discussions_thread
    on scenario_discussions (scenario_version_id, created_at);

-- 신고는 사용자당 한 번이다. 앱에서 세는 대신 유니크 제약으로 막는다(ADR-0027 과
-- 같은 이유 — 중복 방지는 DB 가 할 수 있을 때 DB 가 한다).
create table scenario_discussion_reports (
    id uuid primary key default gen_random_uuid(),
    discussion_id uuid not null references scenario_discussions(id) on delete cascade,
    reporter_user_id uuid not null references users(id),
    reason text,
    created_at timestamptz not null default now(),
    unique (discussion_id, reporter_user_id)
);
