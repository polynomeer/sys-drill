-- docs/COMMUNITY_EXPANSION_PLAN.md C7 (PLAN.md Round E3) — 토론 마무리.
--
-- parent_id: 답글은 한 단계만. 답글의 답글은 앱에서 거부한다 — 시나리오당 스레드
--   하나라는 범위 제한(ADR-0040) 안에서 질문과 답이 흩어지지 않게 하는 정도면 된다.
-- contains_spoiler: 작성자가 풀이 내용을 담았다고 표시한 글. 그 시나리오를 완료하지
--   않은 사람에게는 본문 대신 잠금이 내려간다(인용 풀이 잠금과 같은 ADR-0041 게이트).
-- kind: 필터용 글 종류. 입력 템플릿은 두지 않는다.
alter table scenario_discussions
    add column parent_id uuid references scenario_discussions(id),
    add column contains_spoiler boolean not null default false,
    add column kind varchar(20) not null default 'QUESTION';

create index idx_scenario_discussions_parent on scenario_discussions (parent_id);
