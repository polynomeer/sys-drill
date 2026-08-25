-- docs/ARCHITECTURE.md §4.1 build_challenges/build_stages/build_submissions/
-- build_stage_results — Build Mode (PLAN.md step 9). test_script stores the
-- full runnable test file text directly (same "config as data" pattern as
-- prompt_templates.template_body and scenario_steps.content) rather than a
-- filesystem path, so the backend doesn't need to locate challenges/ on disk.

create table build_challenges (
    id                 uuid primary key default gen_random_uuid(),
    slug               varchar(100) not null unique,
    title              varchar(255) not null,
    languages          varchar(100) not null,
    source_file_name   varchar(100) not null,
    created_at         timestamptz not null default now()
);

create table build_stages (
    id            uuid primary key default gen_random_uuid(),
    challenge_id  uuid not null references build_challenges (id) on delete cascade,
    stage_order   int not null,
    title         varchar(255) not null,
    spec          text,
    test_script   text not null,
    created_at    timestamptz not null default now(),
    unique (challenge_id, stage_order)
);

create table build_submissions (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid not null references users (id),
    challenge_id  uuid not null references build_challenges (id),
    commit_ref    varchar(100),
    source_code   text not null,
    status        varchar(30) not null default 'QUEUED',
    score         int,
    created_at    timestamptz not null default now(),
    completed_at  timestamptz
);
create index idx_build_submissions_user_id on build_submissions (user_id);

create table build_stage_results (
    id             uuid primary key default gen_random_uuid(),
    submission_id  uuid not null references build_submissions (id) on delete cascade,
    stage_id       uuid not null references build_stages (id),
    status         varchar(30) not null,
    feedback       text,
    created_at     timestamptz not null default now()
);
create index idx_build_stage_results_submission_id on build_stage_results (submission_id);
