package com.sysdrill.backend.auth

import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserResponse
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 8, message = "password must be at least 8 characters") val password: String,
    @field:NotBlank val nickname: String,
    val experienceYears: Int? = null,
    val primaryStack: String? = null,
    // docs/COMMERCIALIZATION.md — the /terms, /privacy page content is still a
    // placeholder pending legal review, but the consent record (this flag ->
    // User.termsAcceptedAt) is real from day one.
    @field:AssertTrue(message = "must accept the terms of service and privacy policy") val termsAccepted: Boolean = false,
)

data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)

data class PasswordResetRequestRequest(
    @field:NotBlank @field:Email val email: String,
)

data class PasswordResetConfirmRequest(
    @field:NotBlank val token: String,
    @field:NotBlank @field:Size(min = 8, message = "password must be at least 8 characters") val newPassword: String,
)

data class AuthResponse(
    val token: String,
    val user: UserResponse,
) {
    companion object {
        fun of(token: String, user: User) = AuthResponse(token, UserResponse.from(user))
    }
}
