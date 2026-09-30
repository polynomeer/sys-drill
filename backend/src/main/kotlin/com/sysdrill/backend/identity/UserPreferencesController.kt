package com.sysdrill.backend.identity

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.common.web.NotFoundException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** docs/CODECRAFTERS_BENCHMARK.md §3.4 — the caller's own onboarding preferences. PUT replaces both fields (null clears one). */
@RestController
class UserPreferencesController(private val userRepository: UserRepository) {

    @GetMapping("/me/preferences")
    fun get(@AuthenticatedUserId userId: UUID): UserPreferencesResponse = UserPreferencesResponse.from(load(userId))

    @PutMapping("/me/preferences")
    fun put(@AuthenticatedUserId userId: UUID, @RequestBody request: UserPreferencesRequest): UserPreferencesResponse {
        val user = load(userId)
        user.preferredLanguage = request.preferredLanguage
        user.trainingGoal = request.trainingGoal
        return UserPreferencesResponse.from(userRepository.save(user))
    }

    private fun load(userId: UUID): User = userRepository.findById(userId).orElseThrow { NotFoundException("User not found: $userId") }
}
