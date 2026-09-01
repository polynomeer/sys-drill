package com.sysdrill.backend.organization

import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.identity.UserRepository
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val membershipRepository: OrganizationMembershipRepository,
    private val invitationRepository: OrganizationInvitationRepository,
    private val userRepository: UserRepository,
    private val accessGuard: OrganizationAccessGuard,
    @Value("\${sysdrill.organization.invitation-ttl-days}") private val invitationTtlDays: Long,
) {

    @Transactional
    fun createOrganization(userId: UUID, name: String): OrganizationDetailResponse {
        val organization = organizationRepository.save(Organization(name = name, createdBy = userId))
        membershipRepository.save(
            OrganizationMembership(organizationId = organization.id!!, userId = userId, role = OrganizationRole.ADMIN)
        )
        return toDetail(organization, userId)
    }

    fun listMyOrganizations(userId: UUID): List<OrganizationSummaryResponse> {
        val memberships = membershipRepository.findByUserId(userId)
        val orgsById = organizationRepository.findAllById(memberships.map { it.organizationId }).associateBy { it.id }
        return memberships.mapNotNull { membership ->
            val org = orgsById[membership.organizationId] ?: return@mapNotNull null
            OrganizationSummaryResponse(id = org.id!!, name = org.name, myRole = membership.role)
        }
    }

    fun getOrganization(orgId: UUID, userId: UUID): OrganizationDetailResponse {
        accessGuard.requireMember(orgId, userId)
        val organization = organizationRepository.findById(orgId)
            .orElseThrow { NotFoundException("Organization not found: $orgId") }
        return toDetail(organization, userId)
    }

    @Transactional
    fun inviteMember(orgId: UUID, adminUserId: UUID, email: String, role: OrganizationRole): OrganizationInvitationResponse {
        accessGuard.requireAdmin(orgId, adminUserId)
        val normalizedEmail = email.lowercase()

        userRepository.findByEmail(normalizedEmail)?.let { existingUser ->
            if (membershipRepository.findByOrganizationIdAndUserId(orgId, existingUser.id!!) != null) {
                throw ConflictException("$email is already a member of this organization")
            }
        }
        if (invitationRepository.findByOrganizationIdAndInviteeEmailAndStatus(orgId, normalizedEmail, OrganizationInvitationStatus.PENDING) != null) {
            throw ConflictException("A pending invitation for $email already exists")
        }

        val invitation = invitationRepository.save(
            OrganizationInvitation(
                organizationId = orgId,
                inviteeEmail = normalizedEmail,
                role = role,
                token = UUID.randomUUID().toString(),
                invitedBy = adminUserId,
                expiresAt = Instant.now().plusSeconds(invitationTtlDays * 24 * 3600),
            )
        )
        return toInvitationResponse(invitation)
    }

    fun listInvitations(orgId: UUID, adminUserId: UUID): List<OrganizationInvitationResponse> {
        accessGuard.requireAdmin(orgId, adminUserId)
        return invitationRepository.findByOrganizationIdAndStatus(orgId, OrganizationInvitationStatus.PENDING)
            .map(::toInvitationResponse)
    }

    @Transactional
    fun revokeInvitation(orgId: UUID, adminUserId: UUID, invitationId: UUID) {
        accessGuard.requireAdmin(orgId, adminUserId)
        val invitation = invitationRepository.findById(invitationId)
            .orElseThrow { NotFoundException("Invitation not found: $invitationId") }
        if (invitation.organizationId != orgId) throw NotFoundException("Invitation not found: $invitationId")
        if (invitation.status != OrganizationInvitationStatus.PENDING) {
            throw ConflictException("Invitation $invitationId is not pending")
        }
        invitation.status = OrganizationInvitationStatus.REVOKED
        invitationRepository.save(invitation)
    }

    fun previewInvitation(token: String): InvitationPreviewResponse {
        val invitation = invitationRepository.findByToken(token)
            ?: throw NotFoundException("Invitation not found: $token")
        val organization = organizationRepository.findById(invitation.organizationId)
            .orElseThrow { NotFoundException("Organization not found: ${invitation.organizationId}") }
        return InvitationPreviewResponse(
            organizationName = organization.name,
            inviteeEmail = invitation.inviteeEmail,
            role = invitation.role,
            expired = invitation.expiresAt.isBefore(Instant.now()),
            alreadyResolved = invitation.status != OrganizationInvitationStatus.PENDING,
        )
    }

    @Transactional
    fun acceptInvitation(token: String, userId: UUID): OrganizationDetailResponse {
        val caller = userRepository.findById(userId).orElseThrow { NotFoundException("User not found: $userId") }
        val invitation = accessGuard.requireInvitationRecipient(token, caller.email)
        if (invitation.status != OrganizationInvitationStatus.PENDING) {
            throw ConflictException("Invitation $token is no longer pending")
        }
        if (invitation.expiresAt.isBefore(Instant.now())) {
            throw ConflictException("Invitation $token has expired")
        }
        if (membershipRepository.findByOrganizationIdAndUserId(invitation.organizationId, userId) != null) {
            throw ConflictException("Already a member of this organization")
        }

        membershipRepository.save(
            OrganizationMembership(organizationId = invitation.organizationId, userId = userId, role = invitation.role)
        )
        invitation.status = OrganizationInvitationStatus.ACCEPTED
        invitationRepository.save(invitation)

        val organization = organizationRepository.findById(invitation.organizationId)
            .orElseThrow { NotFoundException("Organization not found: ${invitation.organizationId}") }
        return toDetail(organization, userId)
    }

    @Transactional
    fun removeMember(orgId: UUID, adminUserId: UUID, targetUserId: UUID) {
        accessGuard.requireAdmin(orgId, adminUserId)
        val target = membershipRepository.findByOrganizationIdAndUserId(orgId, targetUserId)
            ?: throw NotFoundException("Member not found: $targetUserId")
        requireNotLastAdmin(orgId, target)
        membershipRepository.delete(target)
    }

    @Transactional
    fun leaveOrganization(orgId: UUID, userId: UUID) {
        val membership = accessGuard.requireMember(orgId, userId)
        requireNotLastAdmin(orgId, membership)
        membershipRepository.delete(membership)
    }

    private fun requireNotLastAdmin(orgId: UUID, membership: OrganizationMembership) {
        if (membership.role != OrganizationRole.ADMIN) return
        val adminCount = membershipRepository.countByOrganizationIdAndRole(orgId, OrganizationRole.ADMIN)
        if (adminCount <= 1) throw ConflictException("Cannot remove the last remaining admin of an organization")
    }

    private fun toDetail(organization: Organization, callerId: UUID): OrganizationDetailResponse {
        val memberships = membershipRepository.findByOrganizationId(organization.id!!)
        val usersById = userRepository.findAllById(memberships.map { it.userId }).associateBy { it.id }
        val myRole = memberships.first { it.userId == callerId }.role
        val members = memberships.mapNotNull { membership ->
            val user = usersById[membership.userId] ?: return@mapNotNull null
            OrganizationMemberResponse(
                userId = membership.userId,
                nickname = user.nickname,
                email = user.email,
                role = membership.role,
                joinedAt = membership.createdAt,
            )
        }
        return OrganizationDetailResponse(id = organization.id!!, name = organization.name, myRole = myRole, members = members)
    }

    private fun toInvitationResponse(invitation: OrganizationInvitation) = OrganizationInvitationResponse(
        id = invitation.id!!,
        inviteeEmail = invitation.inviteeEmail,
        role = invitation.role,
        token = invitation.token,
        expiresAt = invitation.expiresAt,
        expired = invitation.expiresAt.isBefore(Instant.now()),
    )
}
