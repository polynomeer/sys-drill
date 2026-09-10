-- ADR-0037 다음 슬라이스(영속화만, PLAN.md "Drills 고도화" 참고): Architecture
-- Canvas의 노드/엣지 그래프를 세션당 서버에 저장한다. graph는 프론트가
-- 이미 로컬 드래프트에 쓰는 {nodes, edges} JSON을 그대로 담는 불투명
-- 블롭이다 — 백엔드는 내부 구조를 파싱/검증하지 않는다(노드별로 쿼리하는
-- 소비자가 아직 없음 — RuleBasedSimulationEngine은 여전히 DesignTraits만
-- 읽는다, Slice 1).
create table system_topologies (
    id          uuid primary key default gen_random_uuid(),
    session_id  uuid not null unique references sessions (id) on delete cascade,
    graph       jsonb not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
