package com.sysdrill.backend.organization

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreateOrganizationRequest(
    @field:NotBlank val name: String,
)

data class InviteMemberRequest(
    @field:NotBlank @field:Email val email: String,
    val role: OrganizationRole = OrganizationRole.MEMBER,
)

data class OrganizationSummaryResponse(
    val id: UUID,
    val name: String,
    val myRole: OrganizationRole,
)

data class OrganizationMemberResponse(
    val userId: UUID,
    val nickname: String,
    val email: String,
    val role: OrganizationRole,
    val joinedAt: Instant?,
)

data class OrganizationDetailResponse(
    val id: UUID,
    val name: String,
    val myRole: OrganizationRole,
    val members: List<OrganizationMemberResponse>,
)

data class OrganizationInvitationResponse(
    val id: UUID,
    val inviteeEmail: String,
    val role: OrganizationRole,
    val token: String,
    val expiresAt: Instant,
    val expired: Boolean,
)

data class InvitationPreviewResponse(
    val organizationName: String,
    val inviteeEmail: String,
    val role: OrganizationRole,
    val expired: Boolean,
    val alreadyResolved: Boolean,
)
