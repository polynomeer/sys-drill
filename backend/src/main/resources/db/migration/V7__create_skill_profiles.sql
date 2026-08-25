-- docs/ARCHITECTURE.md §4.1 skill_profiles: "장기 개인화 프로필". v1 actively
-- populates `weaknesses` (a riskKey -> occurrence-count map, built from
-- RuleEvaluator findings across a user's evaluations — the "반복되는 사고 패턴"
-- docs/PRD.md §11.3 describes) and `trend` (a rolling list of recent
-- totalScores). `strengths` is reserved for when a similar repeated-pattern
-- need shows up on that side.

create table skill_profiles (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null unique references users (id),
    strengths   jsonb,
    weaknesses  jsonb,
    trend       jsonb,
    updated_at  timestamptz not null default now()
);
