---
status: accepted
---

# The audit log is scoped to organization admin/membership actions, not a platform-wide trail, and is written synchronously in the same transaction as the action it records

`OrganizationAuditLogService.record()` is called at the end of `OrganizationService`'s existing `@Transactional` methods (create/invite/revoke/accept/remove/leave) and `CustomScenarioService.create()` — never from a separate event or queue path. ROADMAP.md lists "SSO/RBAC/Audit Log" together as one line, which could as easily read as a platform-wide security audit trail (every mutating API call, reviewed by a platform admin — the natural reading once [0025](0025-platform-rbac-v1-single-role-403-and-signup-allowlist-bootstrap.md) exists). We scoped it to organization admin actions instead: PRD.md's business model table groups Audit Log with Team/Enterprise-tier features (team dashboard, Game Day, custom scenarios) — the audience is an org admin reviewing what happened *in their own team*, not a platform-wide compliance trail. Session/training activity (who completed what, when) is deliberately excluded — [0033's team dashboard](../../PLAN.md) already covers it, and folding it in here would just duplicate that view under a different name.

Recording synchronously inside the same transaction (not via `ApplicationEventPublisher`/a Redis queue, the pattern this app otherwise reaches for once real per-transaction side effects are involved — see [0004](0004-async-jobs-enqueued-only-after-commit.md)) is deliberate: an audit entry that could exist without its action (or vice versa) defeats the point of an audit trail. The action and its log entry commit or roll back together, by construction, because they're one transaction — no reconciliation logic needed to keep them consistent.
