package com.sysdrill.backend.organization

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "organization_audit_log_entries")
class OrganizationAuditLogEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "organization_id", nullable = false)
    var organizationId: UUID,

    @Column(name = "actor_user_id", nullable = false)
    var actorUserId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var action: OrganizationAuditAction,

    /** Raw ids/strings only (e.g. target user id, invitee email) — never a resolved nickname; joined at read time, same as OrganizationService.toDetail (ADR-0011). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var detail: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
