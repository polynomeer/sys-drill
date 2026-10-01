package com.sysdrill.backend.organization

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface OrganizationRepository : JpaRepository<Organization, UUID>

interface OrganizationMembershipRepository : JpaRepository<OrganizationMembership, UUID> {
    fun findByUserId(userId: UUID): List<OrganizationMembership>
    fun findByOrganizationIdAndUserId(organizationId: UUID, userId: UUID): OrganizationMembership?
    fun findByOrganizationId(organizationId: UUID): List<OrganizationMembership>
    fun countByOrganizationIdAndRole(organizationId: UUID, role: OrganizationRole): Long
}

interface OrganizationInvitationRepository : JpaRepository<OrganizationInvitation, UUID> {
    fun findByToken(token: String): OrganizationInvitation?

    /** Notifications (PLAN.md Round B16): invitations waiting for this email. */
    fun findByInviteeEmailAndStatus(inviteeEmail: String, status: OrganizationInvitationStatus): List<OrganizationInvitation>
    fun findByOrganizationIdAndStatus(organizationId: UUID, status: OrganizationInvitationStatus): List<OrganizationInvitation>
    fun findByOrganizationIdAndInviteeEmailAndStatus(
        organizationId: UUID,
        inviteeEmail: String,
        status: OrganizationInvitationStatus,
    ): OrganizationInvitation?
}

interface OrganizationAssessmentRepository : JpaRepository<OrganizationAssessment, UUID> {
    fun findByToken(token: String): OrganizationAssessment?
    fun findByOrganizationId(organizationId: UUID): List<OrganizationAssessment>

    /** Which of these sessions are hiring-assessment results — kept out of public activity (community/ActivityService). */
    fun findByResultSessionIdIn(sessionIds: Collection<UUID>): List<OrganizationAssessment>

    fun existsByResultSessionId(sessionId: UUID): Boolean
}
