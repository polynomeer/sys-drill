package com.sysdrill.backend.organization

import com.sysdrill.backend.auth.AuthenticatedUserId
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/organizations")
class OrganizationController(private val organizationService: OrganizationService) {

    @PostMapping
    fun create(
        @AuthenticatedUserId userId: UUID,
        @Valid @RequestBody request: CreateOrganizationRequest,
    ): ResponseEntity<OrganizationDetailResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(organizationService.createOrganization(userId, request.name))

    @GetMapping
    fun listMine(@AuthenticatedUserId userId: UUID): List<OrganizationSummaryResponse> =
        organizationService.listMyOrganizations(userId)

    @GetMapping("/{orgId}")
    fun get(@PathVariable orgId: UUID, @AuthenticatedUserId userId: UUID): OrganizationDetailResponse =
        organizationService.getOrganization(orgId, userId)

    @PostMapping("/{orgId}/invitations")
    fun invite(
        @PathVariable orgId: UUID,
        @AuthenticatedUserId userId: UUID,
        @Valid @RequestBody request: InviteMemberRequest,
    ): ResponseEntity<OrganizationInvitationResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(organizationService.inviteMember(orgId, userId, request.email, request.role))

    @GetMapping("/{orgId}/invitations")
    fun listInvitations(@PathVariable orgId: UUID, @AuthenticatedUserId userId: UUID): List<OrganizationInvitationResponse> =
        organizationService.listInvitations(orgId, userId)

    @DeleteMapping("/{orgId}/invitations/{invitationId}")
    fun revokeInvitation(
        @PathVariable orgId: UUID,
        @PathVariable invitationId: UUID,
        @AuthenticatedUserId userId: UUID,
    ): ResponseEntity<Void> {
        organizationService.revokeInvitation(orgId, userId, invitationId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/invitations/{token}")
    fun previewInvitation(@PathVariable token: String): InvitationPreviewResponse =
        organizationService.previewInvitation(token)

    @PostMapping("/invitations/{token}/accept")
    fun acceptInvitation(@PathVariable token: String, @AuthenticatedUserId userId: UUID): OrganizationDetailResponse =
        organizationService.acceptInvitation(token, userId)

    @DeleteMapping("/{orgId}/members/{targetUserId}")
    fun removeMember(
        @PathVariable orgId: UUID,
        @PathVariable targetUserId: UUID,
        @AuthenticatedUserId userId: UUID,
    ): ResponseEntity<Void> {
        organizationService.removeMember(orgId, userId, targetUserId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{orgId}/leave")
    fun leave(@PathVariable orgId: UUID, @AuthenticatedUserId userId: UUID): ResponseEntity<Void> {
        organizationService.leaveOrganization(orgId, userId)
        return ResponseEntity.noContent().build()
    }
}
