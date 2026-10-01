-- docs/CODECRAFTERS_BENCHMARK.md §3.9 (PLAN.md Round B16) — the notification bell.
-- Notifications themselves are derived on read from existing rows (ADR-0011): graded
-- submissions, finished Build runs, pending organization invitations, new discussion
-- messages. The only stored state is when the user last opened the list — anything newer
-- counts as unseen. Null means never opened (everything in the window is new).
alter table users add column notifications_seen_at timestamptz;
