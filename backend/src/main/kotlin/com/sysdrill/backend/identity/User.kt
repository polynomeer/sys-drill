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

    /** V51 — optional onboarding answer; see [PreferredLanguage]. */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language")
    var preferredLanguage: PreferredLanguage? = null,

    /** V51 — optional onboarding answer; see [TrainingGoal]. */
    @Enumerated(EnumType.STRING)
    @Column(name = "training_goal")
    var trainingGoal: TrainingGoal? = null,

    /** V53 — when the notification list was last opened; newer derived notifications count as unseen. */
    @Column(name = "notifications_seen_at")
    var notificationsSeenAt: Instant? = null,

    /** PLAN.md step 35 — platform-wide RBAC, distinct from a per-organization [com.sysdrill.backend.organization.OrganizationRole]. */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false)
    var platformRole: PlatformRole = PlatformRole.USER,

    /**
     * ADR-0042 — 랭킹 보드에서 빠진다. 기본은 참여(false): 보드가 비어 있으면
     * 의미가 없고, 노출되는 것은 닉네임·티어·점수뿐이다.
     */
    @Column(name = "ranking_opt_out", nullable = false)
    var rankingOptOut: Boolean = false,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    @Column(name = "terms_accepted_at")
    var termsAcceptedAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
