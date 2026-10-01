-- docs/DRILLS_EXPANSION_PLAN.md §5-1 / docs/OBSERVABILITY_UI_PLAN.md §5-1 (PLAN.md Round E8) —
-- 세션 단위 미션 진행 상태 한 곳: 확인한 요구사항 질문(M1), 고정한 꼬리설계 변형 키,
-- 그리고 이후 라운드의 SLO(M3)·알림 규칙(O5)·Readiness(O7). 세션당 1:1이고 슬라이스마다
-- 필드가 늘어나는 값이라 정규 컬럼이 아니라 JSONB(ARCHITECTURE §4.1 기준).
-- 제출과 함께 확정되는 입력(규모 추정·설계 방어 등)은 여기가 아니라 submissions.structured_json.
alter table sessions add column mission_state jsonb not null default '{}';
