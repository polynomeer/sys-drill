package com.sysdrill.backend.auth

import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.FakeEmailConfig
import com.sysdrill.backend.support.FakeEmailSender
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** docs/COMMERCIALIZATION.md — password reset and email verification, both built on the same opaque-token shape as organization invitations (ADR-0022). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeEmailConfig::class)
class PasswordResetAndVerificationIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val emailSender: FakeEmailSender,
    @Autowired val passwordResetTokenRepository: PasswordResetTokenRepository,
    @Autowired val emailVerificationTokenRepository: EmailVerificationTokenRepository,
) {
    private fun uniqueEmail() = "reset-${UUID.randomUUID()}@example.com"

    private fun signUp(email: String, password: String = "original-password"): UUID {
        emailSender.sent.clear()
        val response = mockMvc.perform(
            post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"$password","nickname":"drill-user","termsAccepted":true}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.user.id"))
    }

    @Test
    fun `signing up sends a verification email and requires terms acceptance`() {
        val email = uniqueEmail()
        val userId = signUp(email)

        assertThat(userRepository.findById(userId).orElseThrow().termsAcceptedAt).isNotNull()
        assertThat(userRepository.findById(userId).orElseThrow().emailVerified).isFalse()
        assertThat(emailSender.sent).anyMatch { it.to == email && it.subject.contains("이메일 인증") }

        mockMvc.perform(
            post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${uniqueEmail()}","password":"password123","nickname":"no-consent","termsAccepted":false}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `verifying with the emailed token marks the account verified, and a second use is rejected`() {
        val email = uniqueEmail()
        val userId = signUp(email)
        val token = emailVerificationTokenRepository.findAll().first { it.userId == userId }.token

        mockMvc.perform(get("/auth/verify-email").param("token", token)).andExpect(status().isNoContent)
        assertThat(userRepository.findById(userId).orElseThrow().emailVerified).isTrue()

        mockMvc.perform(get("/auth/verify-email").param("token", token)).andExpect(status().isBadRequest)
    }

    @Test
    fun `requesting a password reset emails a token that resets the password, single use`() {
        val email = uniqueEmail()
        signUp(email, password = "original-password")
        emailSender.sent.clear()

        mockMvc.perform(
            post("/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content("""{"email":"$email"}""")
        ).andExpect(status().isNoContent)
        assertThat(emailSender.sent).anyMatch { it.to == email && it.subject.contains("비밀번호 재설정") }

        val token = passwordResetTokenRepository.findAll().first { it.token in emailSender.sent.last().body }.token

        mockMvc.perform(
            post("/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token","newPassword":"brand-new-password"}""")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"original-password"}""")
        ).andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"brand-new-password"}""")
        ).andExpect(status().isOk)

        // Reusing the same reset token a second time must fail.
        mockMvc.perform(
            post("/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$token","newPassword":"yet-another-password"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `requesting a reset for an email that doesn't exist still succeeds, revealing nothing`() {
        mockMvc.perform(
            post("/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${uniqueEmail()}"}""")
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `an expired reset token is rejected`() {
        val email = uniqueEmail()
        val userId = signUp(email)
        val expiredToken = passwordResetTokenRepository.save(
            PasswordResetToken(userId = userId, token = UUID.randomUUID().toString(), expiresAt = Instant.now().minusSeconds(60))
        )

        mockMvc.perform(
            post("/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"${expiredToken.token}","newPassword":"whatever12345"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `five failed logins lock the account even with the correct password on the sixth try`() {
        val email = uniqueEmail()
        signUp(email, password = "correct-password")

        repeat(5) {
            mockMvc.perform(
                post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","password":"wrong-password"}""")
            ).andExpect(status().isUnauthorized)
        }

        val lockedOutStatus = mockMvc.perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"correct-password"}""")
        ).andReturn().response.status
        assertThat(lockedOutStatus).isEqualTo(429)
    }
}
