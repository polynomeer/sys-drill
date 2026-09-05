package com.sysdrill.backend.organization

/** PLAN.md step 38 — the set of organization admin/membership actions the audit log records (see docs/adr/0029 for why this is scoped to organization actions, not a platform-wide trail). */
enum class OrganizationAuditAction {
    ORGANIZATION_CREATED,
    MEMBER_INVITED,
    INVITATION_REVOKED,
    MEMBER_JOINED,
    MEMBER_REMOVED,
    MEMBER_LEFT,
    CUSTOM_SCENARIO_CREATED,
}
