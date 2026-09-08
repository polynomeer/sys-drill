-- Phase 6 (docs/adr/0034) — a third independent axis on Scenario alongside
-- organization_id (34단계) and creator_user_id (Phase 5 marketplace). Default
-- PUBLIC keeps every existing row visible exactly as before; only the new
-- Architecture Linter feature ever creates a PRIVATE row.
alter table scenarios add column visibility varchar not null default 'PUBLIC';
