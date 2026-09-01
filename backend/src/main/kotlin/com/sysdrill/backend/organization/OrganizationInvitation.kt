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
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "organization_invitations")
class OrganizationInvitation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "organization_id", nullable = false)
    var organizationId: UUID,

    @Column(name = "invitee_email", nullable = false)
    var inviteeEmail: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: OrganizationRole = OrganizationRole.MEMBER,

    @Column(nullable = false, unique = true)
    var token: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrganizationInvitationStatus = OrganizationInvitationStatus.PENDING,

    @Column(name = "invited_by", nullable = false)
    var invitedBy: UUID,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
