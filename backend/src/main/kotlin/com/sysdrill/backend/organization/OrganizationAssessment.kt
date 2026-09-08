package com.sysdrill.backend.organization

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

/** Phase 5 (docs/adr/0033) — OrganizationInvitation's email-bound token, scoped to one scenario instead of org membership. No status column: derived from resultSessionId + the Session's own status at read time (ADR-0011). */
@Entity
@Table(name = "organization_assessments")
class OrganizationAssessment(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "organization_id", nullable = false)
    var organizationId: UUID,

    @Column(name = "scenario_id", nullable = false)
    var scenarioId: UUID,

    @Column(name = "candidate_email", nullable = false)
    var candidateEmail: String,

    @Column(nullable = false, unique = true)
    var token: String,

    @Column(name = "invited_by", nullable = false)
    var invitedBy: UUID,

    @Column(name = "result_session_id")
    var resultSessionId: UUID? = null,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
