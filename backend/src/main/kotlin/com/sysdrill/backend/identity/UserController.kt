package com.sysdrill.backend.identity

import com.sysdrill.backend.common.web.NotFoundException
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * PLAN.md step 30 — the nickname-only guest creation endpoint this used to
 * expose (`POST /users`) is gone; [com.sysdrill.backend.auth.AuthController]'s
 * `POST /auth/signup` replaced it entirely. `GET /{id}` stays — plenty of
 * other endpoints still resolve a `User` by id.
 */
@RestController
@RequestMapping("/users")
class UserController(private val userRepository: UserRepository) {

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): UserResponse =
        UserResponse.from(userRepository.findById(id).orElseThrow { NotFoundException("User not found: $id") })
}
