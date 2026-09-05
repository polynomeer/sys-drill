package com.sysdrill.backend.organization

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrganizationAuditLogRepository : JpaRepository<OrganizationAuditLogEntry, UUID> {
    fun findTop200ByOrganizationIdOrderByCreatedAtDesc(organizationId: UUID): List<OrganizationAuditLogEntry>
}
