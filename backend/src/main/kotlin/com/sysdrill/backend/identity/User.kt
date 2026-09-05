package com.sysdrill.backend.identity

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
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, unique = true)
    var email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(nullable = false)
    var nickname: String,

    @Column(name = "experience_years")
    var experienceYears: Int? = null,

    @Column(name = "primary_stack")
    var primaryStack: String? = null,

    /** PLAN.md step 35 — platform-wide RBAC, distinct from a per-organization [com.sysdrill.backend.organization.OrganizationRole]. */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false)
    var platformRole: PlatformRole = PlatformRole.USER,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
