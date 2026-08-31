-- PLAN.md 26단계: 사용자가 세션 완료 후 직접 작성하는 구조화된 포스트모템.
-- MTTD/MTTR·조치 타임라인·전후 지표는 applied_actions로부터 매번 다시
-- 계산되므로(ADR-0011) 여기엔 저장하지 않는다 — 여기 저장되는 건 사용자가
-- 직접 쓴 서술(근본 원인·완화/근본 조치 구분·재발 방지 항목)뿐이다.

create table postmortems (
    id                  uuid primary key default gen_random_uuid(),
    session_id          uuid not null unique references sessions (id) on delete cascade,
    root_cause          text not null,
    mitigation_actions  jsonb not null default '[]'::jsonb,
    root_fix_actions    jsonb not null default '[]'::jsonb,
    prevention_items    jsonb not null default '[]'::jsonb,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);
