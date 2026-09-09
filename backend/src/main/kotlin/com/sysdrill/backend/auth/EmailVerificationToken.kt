package com.sysdrill.backend.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(nullable = false, unique = true)
    var token: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)

interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, UUID> {
    fun findByToken(token: String): EmailVerificationToken?
}
