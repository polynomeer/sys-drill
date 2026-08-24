-- MVP core schema per docs/ARCHITECTURE.md §4.
-- Aggregate roots: Scenario (+ScenarioVersion/ScenarioStep), Session (+SessionPhase/Submission/Evaluation/Report).
-- Cross-aggregate references (sessions.user_id, sessions.scenario_version_id, submissions.session_id)
-- use plain foreign keys without cascade; within-aggregate children cascade with their parent.

create table users (
    id                 uuid primary key default gen_random_uuid(),
    email              varchar(255) not null unique,
    password_hash      varchar(255) not null,
    nickname           varchar(100) not null,
    experience_years   int,
    primary_stack      varchar(100),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);

create table content_items (
    id          uuid primary key default gen_random_uuid(),
    type        varchar(50) not null,
    title       varchar(255) not null,
    difficulty  varchar(50),
    version     int not null default 1,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table scenarios (
    id                uuid primary key default gen_random_uuid(),
    content_id        uuid not null references content_items (id),
    domain            varchar(100) not null,
    base_requirements jsonb,
    scoring_profile   jsonb,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);
create index idx_scenarios_content_id on scenarios (content_id);

create table scenario_versions (
    id              uuid primary key default gen_random_uuid(),
    scenario_id     uuid not null references scenarios (id) on delete cascade,
    version_no      int not null,
    status          varchar(20) not null default 'DRAFT',
    followup_rules  jsonb,
    incident_rules  jsonb,
    created_at      timestamptz not null default now(),
    unique (scenario_id, version_no)
);
create index idx_scenario_versions_scenario_id on scenario_versions (scenario_id);

create table scenario_steps (
    id                    uuid primary key default gen_random_uuid(),
    scenario_version_id   uuid not null references scenario_versions (id) on delete cascade,
    step_order            int not null,
    step_type             varchar(30) not null,
    trigger_condition     jsonb,
    content               jsonb,
    created_at            timestamptz not null default now(),
    unique (scenario_version_id, step_order)
);
create index idx_scenario_steps_scenario_version_id on scenario_steps (scenario_version_id);

create table sessions (
    id                    uuid primary key default gen_random_uuid(),
    user_id               uuid not null references users (id),
    scenario_version_id   uuid not null references scenario_versions (id),
    status                varchar(30) not null default 'IN_PROGRESS',
    current_phase         varchar(50),
    seed                  varchar(100),
    started_at            timestamptz not null default now(),
    completed_at          timestamptz,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now()
);
create index idx_sessions_user_id on sessions (user_id);
create index idx_sessions_scenario_version_id on sessions (scenario_version_id);
create index idx_sessions_status on sessions (status);

create table session_phases (
    id            uuid primary key default gen_random_uuid(),
    session_id    uuid not null references sessions (id) on delete cascade,
    phase_type    varchar(50) not null,
    phase_order   int not null,
    status        varchar(30) not null default 'PENDING',
    started_at    timestamptz,
    completed_at  timestamptz,
    created_at    timestamptz not null default now(),
    unique (session_id, phase_order)
);
create index idx_session_phases_session_id on session_phases (session_id);

create table submissions (
    id                  uuid primary key default gen_random_uuid(),
    session_id          uuid not null references sessions (id) on delete cascade,
    phase               varchar(50) not null,
    raw_text            text,
    structured_json     jsonb,
    revision_no         int not null default 1,
    client_request_id   varchar(100) unique,
    created_at          timestamptz not null default now()
);
create index idx_submissions_session_id on submissions (session_id);

create table evaluations (
    id               uuid primary key default gen_random_uuid(),
    submission_id    uuid not null references submissions (id) on delete cascade,
    rubric_version   varchar(50),
    total_score      int,
    score_dimensions jsonb,
    strengths        jsonb,
    weaknesses       jsonb,
    risk_points      jsonb,
    is_active        boolean not null default true,
    created_at       timestamptz not null default now()
);
create index idx_evaluations_submission_id on evaluations (submission_id);

create table evaluation_risk_flags (
    id             uuid primary key default gen_random_uuid(),
    evaluation_id  uuid not null references evaluations (id) on delete cascade,
    risk_key       varchar(100) not null,
    severity       varchar(20) not null,
    description    text,
    created_at     timestamptz not null default now()
);
create index idx_evaluation_risk_flags_evaluation_id on evaluation_risk_flags (evaluation_id);

create table reports (
    id                  uuid primary key default gen_random_uuid(),
    session_id          uuid not null references sessions (id) on delete cascade,
    version             int not null default 1,
    summary             text,
    timeline_feedback   jsonb,
    improvement_guide   jsonb,
    created_at          timestamptz not null default now(),
    unique (session_id, version)
);
create index idx_reports_session_id on reports (session_id);
