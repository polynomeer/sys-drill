---
status: accepted
---

# Platform RBAC v1: a single `platformRole` enum, a new 403 distinct from the existing 404 ownership convention, and signup-allowlist bootstrap with no promote/demote API

`User.platformRole` (`USER` | `PLATFORM_ADMIN`) is the only platform-wide role, checked by a new `PlatformAccessGuard.requirePlatformAdmin()` gating `/admin/prompt-templates` (previously entirely unauthenticated — the only endpoint that needed this). This is deliberately flatter than a full roles/permissions table: the only thing v1 needs to express is "can touch prompt templates," and a per-organization `OrganizationRole` already exists for org-scoped authorization — a second, unrelated axis.

`PlatformAccessGuard` throws a new `ForbiddenException` (403), not the existing `NotFoundException` (404) that `SessionAccessGuard`/`OrganizationAccessGuard` use for authorization failures. Those guards use 404 deliberately, to keep "doesn't exist" indistinguishable from "exists but you can't see it" for a specific resource instance a caller might not own. `/admin/prompt-templates` isn't scoped to any instance the caller might or might not own — it's a role gate on an endpoint's entire surface, whose existence isn't a secret — so hiding it behind 404 would just be dishonest without protecting anything. This is the first 403 in the codebase and sets the precedent for any future role gate of this shape.

There is no promote/demote API. The only way to become `PLATFORM_ADMIN` is signing up with an email present in `sysdrill.auth.platform-admin-emails` (`SYSDRILL_AUTH_PLATFORM_ADMIN_EMAILS`, comma-separated, empty by default), checked once at signup. An API endpoint that grants platform-wide admin is itself a privilege-escalation surface, and nothing in this app yet needs runtime role changes (no admin UI, no team of operators to manage) — an ops-configured allowlist is the smallest thing that works. Revisit if platform admins need to be added or removed without redeploying, or once SSO (ROADMAP.md Phase 4) gives this app an actual identity provider to source roles from.
