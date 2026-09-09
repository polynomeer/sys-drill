package com.sysdrill.backend.auth

import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.mail.EmailSender
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * docs/COMMERCIALIZATION.md — proves email ownership, but doesn't gate
 * anything: existing sessions/scenarios/etc. work identically whether or not
 * a user has verified, matching the "no new UX friction" call in PLAN.md.
 * `User.emailVerified` is just a badge the frontend can choose to display.
 */
@Service
class EmailVerificationService(
    private val tokenRepository: EmailVerificationTokenRepository,
    private val userRepository: UserRepository,
    private val emailSender: EmailSender,
    @Value("\${sysdrill.frontend-origin}") private val frontendOrigin: String,
    @Value("\${sysdrill.auth.email-verification-ttl-hours}") private val ttlHours: Long,
) {

    fun sendVerification(user: User) {
        val token = tokenRepository.save(
            EmailVerificationToken(userId = user.id!!, token = UUID.randomUUID().toString(), expiresAt = Instant.now().plusSeconds(ttlHours * 3600))
        )
        emailSender.send(
            to = user.email,
            subject = "SysDrill 이메일 인증",
            body = "아래 링크에서 이메일을 인증하세요 (유효기간 ${ttlHours}시간):\n$frontendOrigin/verify-email?token=${token.token}",
        )
    }

    @Transactional
    fun verify(token: String) {
        val verificationToken = tokenRepository.findByToken(token) ?: throw BadRequestException("Invalid or expired verification token")
        if (verificationToken.usedAt != null) throw BadRequestException("Invalid or expired verification token")
        if (verificationToken.expiresAt.isBefore(Instant.now())) throw BadRequestException("Invalid or expired verification token")

        val user = userRepository.findById(verificationToken.userId).orElseThrow { BadRequestException("Invalid or expired verification token") }
        user.emailVerified = true
        userRepository.save(user)

        verificationToken.usedAt = Instant.now()
        tokenRepository.save(verificationToken)
    }
}
