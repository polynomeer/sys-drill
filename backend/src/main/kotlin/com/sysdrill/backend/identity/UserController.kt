package com.sysdrill.backend.identity

import com.sysdrill.backend.common.web.NotFoundException
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Creates a lightweight identity for onboarding (PLAN.md step 6's "온보딩"
 * screen) — nickname/stack/experience only, no password. This is NOT the
 * real signup/login flow docs/ARCHITECTURE.md §10 sketches
 * (POST /auth/signup, /auth/login) — that remains a gap flagged after step 2.
 * The frontend stores the returned id in localStorage and sends it as
 * `userId` on POST /sessions.
 */
@RestController
@RequestMapping("/users")
class UserController(private val userRepository: UserRepository) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateUserRequest): ResponseEntity<UserResponse> {
        val user = userRepository.save(
            User(
                email = "guest-${UUID.randomUUID()}@sysdrill.local",
                passwordHash = "guest",
                nickname = request.nickname,
                experienceYears = request.experienceYears,
                primaryStack = request.primaryStack,
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): UserResponse =
        UserResponse.from(userRepository.findById(id).orElseThrow { NotFoundException("User not found: $id") })
}
