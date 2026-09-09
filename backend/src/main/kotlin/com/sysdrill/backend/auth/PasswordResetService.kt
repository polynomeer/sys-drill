package com.sysdrill.backend.auth

import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.mail.EmailSender
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** docs/COMMERCIALIZATION.md — self-service password reset, the one gap left by ADR-0028's Google-only alternative path. */
@Service
class PasswordResetService(
    private val userRepository: UserRepository,
    private val tokenRepository: PasswordResetTokenRepository,
    private val emailSender: EmailSender,
    @Value("\${sysdrill.frontend-origin}") private val frontendOrigin: String,
    @Value("\${sysdrill.auth.password-reset-ttl-minutes}") private val ttlMinutes: Long,
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    /** Always succeeds from the caller's point of view, whether or not the email exists — an error here would let an attacker enumerate registered emails. */
    @Transactional
    fun requestReset(email: String) {
        val user = userRepository.findByEmail(email.lowercase()) ?: return
        val token = tokenRepository.save(
            PasswordResetToken(userId = user.id!!, token = UUID.randomUUID().toString(), expiresAt = Instant.now().plusSeconds(ttlMinutes * 60))
        )
        emailSender.send(
            to = user.email,
            subject = "SysDrill 비밀번호 재설정",
            body = "아래 링크에서 비밀번호를 재설정하세요 (유효기간 ${ttlMinutes}분):\n$frontendOrigin/reset-password?token=${token.token}",
        )
    }

    @Transactional
    fun confirmReset(token: String, newPassword: String) {
        val resetToken = tokenRepository.findByToken(token) ?: throw BadRequestException("Invalid or expired reset token")
        if (resetToken.usedAt != null) throw BadRequestException("Invalid or expired reset token")
        if (resetToken.expiresAt.isBefore(Instant.now())) throw BadRequestException("Invalid or expired reset token")

        val user = userRepository.findById(resetToken.userId).orElseThrow { BadRequestException("Invalid or expired reset token") }
        user.passwordHash = passwordEncoder.encode(newPassword)!!
        userRepository.save(user)

        resetToken.usedAt = Instant.now()
        tokenRepository.save(resetToken)
    }
}
