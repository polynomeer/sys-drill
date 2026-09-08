package com.sysdrill.backend.organization

import com.sysdrill.backend.identity.TrendDirection
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
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

data class SetCurriculumRequest(
    @field:NotEmpty val scenarioIds: List<UUID>,
)

data class CurriculumStepResponse(
    val scenarioId: UUID,
    val title: String,
    val domain: String,
    val order: Int,
    val completed: Boolean,
)

data class CurriculumResponse(
    val steps: List<CurriculumStepResponse>,
)

/** Phase 5 — 채용/역량 평가 상품화 (docs/adr/0033). */
data class CreateAssessmentRequest(
    @field:NotBlank @field:Email val candidateEmail: String,
    val scenarioId: UUID,
)

enum class AssessmentStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }

data class AssessmentResponse(
    val id: UUID,
    val organizationId: UUID,
    val scenarioId: UUID,
    val scenarioTitle: String,
    val candidateEmail: String,
    val token: String,
    val status: AssessmentStatus,
    val resultSessionId: UUID?,
    val expiresAt: Instant,
    val createdAt: Instant?,
)

data class AssessmentPreviewResponse(
    val organizationName: String,
    val scenarioTitle: String,
    val scenarioDomain: String,
    val candidateEmail: String,
    val expired: Boolean,
    val alreadyStarted: Boolean,
)
