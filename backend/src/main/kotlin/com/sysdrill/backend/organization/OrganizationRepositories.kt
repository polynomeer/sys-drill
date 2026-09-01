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
    fun findByOrganizationIdAndStatus(organizationId: UUID, status: OrganizationInvitationStatus): List<OrganizationInvitation>
    fun findByOrganizationIdAndInviteeEmailAndStatus(
        organizationId: UUID,
        inviteeEmail: String,
        status: OrganizationInvitationStatus,
    ): OrganizationInvitation?
}
