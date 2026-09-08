-- PLAN.md Phase 5 착수 — 채용/역량 평가 상품화. OrganizationInvitation과 같은
-- 이메일 바인딩 토큰 구조를 시나리오 하나로 스코프했다. 상태 컬럼은 없다 —
-- result_session_id(null=미시작)와 그 세션 자체의 status에서 매 요청 시
-- 파생 계산한다(ADR-0011, ADR-0033).
create table organization_assessments (
    id                 uuid primary key default gen_random_uuid(),
    organization_id    uuid not null references organizations (id) on delete cascade,
    scenario_id        uuid not null references scenarios (id),
    candidate_email    varchar not null,
    token              varchar not null unique,
    invited_by         uuid not null references users (id),
    result_session_id  uuid references sessions (id),
    expires_at         timestamptz not null,
    created_at         timestamptz not null default now()
);
create index idx_org_assessments_organization_id on organization_assessments (organization_id);
