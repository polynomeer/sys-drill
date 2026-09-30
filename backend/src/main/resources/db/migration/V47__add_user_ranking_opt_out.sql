-- docs/LEARNING_COMMUNITY_PLAN.md §6.6 / ADR-0042 — 랭킹 노출 거부.
--
-- 기본은 참여(false)다. 보드가 비어 있으면 아무 의미가 없고, 노출되는 것은
-- 닉네임·티어·점수뿐이며 답안이나 약점 프로필은 포함되지 않기 때문이다.
-- 그래도 빠질 수 있어야 하므로 스위치를 둔다(§7 프라이버시).
alter table users add column ranking_opt_out boolean not null default false;
