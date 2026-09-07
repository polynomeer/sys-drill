-- PLAN.md Phase 5 착수 — Scenario Marketplace. null이면 Flyway 시드(ADR-0002)
-- 또는 조직 전용 커스텀 시나리오(34단계), set이면 마켓플레이스에 공개 등록된
-- 시나리오. organization_id와 독립적인 축이다.
alter table scenarios add column creator_user_id uuid references users (id);
create index idx_scenarios_creator_user_id on scenarios (creator_user_id) where creator_user_id is not null;
