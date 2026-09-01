package com.sysdrill.backend.auth

import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.UnauthorizedException
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * PLAN.md step 30 — docs/ARCHITECTURE.md §10's sketched `POST /auth/signup,
 * /auth/login`, finally built. Replaces `UserController`'s nickname-only
 * guest flow (`POST /users`, removed) entirely rather than adding this
 * alongside it — no real users existed yet to need an account-linking path
 * (docs/adr/0003-no-real-authentication-in-mvp.md's own framing: revisit
 * before exposing this outside a trusted local/demo environment).
 */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<AuthResponse> {
        if (userRepository.findByEmail(request.email) != null) {
            throw ConflictException("Email already registered: ${request.email}")
        }
        val user = userRepository.save(
            User(
                email = request.email,
                passwordHash = passwordEncoder.encode(request.password)!!,
                nickname = request.nickname,
                experienceYears = request.experienceYears,
                primaryStack = request.primaryStack,
            )
        )
        val token = jwtService.issue(user.id!!)
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.of(token, user))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            ?.takeIf { passwordEncoder.matches(request.password, it.passwordHash) }
            ?: throw UnauthorizedException("Invalid email or password")
        return AuthResponse.of(jwtService.issue(user.id!!), user)
    }
}
