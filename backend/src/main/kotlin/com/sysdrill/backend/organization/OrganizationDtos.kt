package com.sysdrill.backend.organization

import com.sysdrill.backend.identity.TrendDirection
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

data class OrganizationDashboardMemberResponse(
    val userId: UUID,
    val nickname: String,
    val email: String,
    val role: OrganizationRole,
    val completedSessionCount: Long,
    val lastActiveAt: Instant?,
    val trendDirection: TrendDirection,
)

data class OrganizationDashboardResponse(
    val members: List<OrganizationDashboardMemberResponse>,
)

data class InvitationPreviewResponse(
    val organizationName: String,
    val inviteeEmail: String,
    val role: OrganizationRole,
    val expired: Boolean,
    val alreadyResolved: Boolean,
)

data class AuditLogEntryResponse(
    val id: UUID,
    val actorNickname: String,
    val actorEmail: String,
    val action: OrganizationAuditAction,
    val detail: Any?,
    val createdAt: Instant?,
)
