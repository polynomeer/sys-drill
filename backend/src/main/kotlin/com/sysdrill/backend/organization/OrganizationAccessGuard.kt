package com.sysdrill.backend.organization

import com.sysdrill.backend.common.web.NotFoundException
import java.util.UUID
import org.springframework.stereotype.Component

/**
 * PLAN.md step 32 — mirrors [com.sysdrill.backend.session.SessionAccessGuard]'s
 * shape exactly: a plain injectable component, called explicitly from
 * controllers, reusing [NotFoundException] (404) for "not a member" and
 * "member but not ADMIN" alike — a caller can't distinguish "org doesn't
 * exist" from "you don't belong here" from the status code.
 */
@Component
class OrganizationAccessGuard(
    private val membershipRepository: OrganizationMembershipRepository,
    private val invitationRepository: OrganizationInvitationRepository,
) {

    fun requireMember(orgId: UUID, userId: UUID): OrganizationMembership =
        membershipRepository.findByOrganizationIdAndUserId(orgId, userId)
            ?: throw NotFoundException("Organization not found: $orgId")

    fun requireAdmin(orgId: UUID, userId: UUID): OrganizationMembership {
        val membership = requireMember(orgId, userId)
        if (membership.role != OrganizationRole.ADMIN) throw NotFoundException("Organization not found: $orgId")
        return membership
    }

    /**
     * Status/expiry aren't checked here (PENDING vs already-ACCEPTED/REVOKED,
     * or `expiresAt` in the past) — those are business-state problems, not
     * identity/ownership ones, so [OrganizationService] handles them as 409s.
     */
    fun requireInvitationRecipient(token: String, userEmail: String): OrganizationInvitation {
        val invitation = invitationRepository.findByToken(token)
            ?: throw NotFoundException("Invitation not found: $token")
        if (!invitation.inviteeEmail.equals(userEmail, ignoreCase = true)) {
            throw NotFoundException("Invitation not found: $token")
        }
        return invitation
    }
}
