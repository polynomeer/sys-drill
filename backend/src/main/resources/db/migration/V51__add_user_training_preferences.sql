-- docs/CODECRAFTERS_BENCHMARK.md §3.4 (PLAN.md Round B10) — two optional onboarding answers.
-- Each has one concrete use, nothing speculative:
--   preferred_language → /bridge opens in that language
--   training_goal      → INTERVIEW pre-checks the interview timer on Drill overviews,
--                        TEAM surfaces the organization features on Home
-- Nullable: existing users and anyone who skips the questions simply get today's defaults.
alter table users add column preferred_language varchar(20);
alter table users add column training_goal varchar(20);
