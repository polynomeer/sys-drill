-- PLAN.md 28단계: 세션 시작 시 옵트인하는 면접형 타이머 모드. 켜져 있으면 각 단계
-- (session_phases.started_at 기준)에 phase 유형별 제한 시간이 적용되고, 그 시각을
-- 넘겨 제출됐는지가 submissions.on_time에 기록된다. 면접형이 아닌 세션의 제출은
-- 항상 null(해당 없음) — false와는 다른 의미다.

alter table sessions add column interview_mode boolean not null default false;
alter table submissions add column on_time boolean;
