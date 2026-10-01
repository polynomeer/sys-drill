package com.sysdrill.backend.build

import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * docs/COMMERCIALIZATION.md — a docker sandbox run per stage had no rate
 * limit of any kind before this. Kept separate from
 * [BuildControllerIntegrationTest] (same low-limit-override reasoning as
 * [com.sysdrill.backend.evaluation.LlmUsageGuardIntegrationTest]) because
 * that file already has a test submitting twice for the same user, which
 * a class-wide limit=1 override would otherwise break. The rate-limit check
 * runs before any sandbox work starts, so this only needs the submit
 * response's status, not a full docker run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BuildSubmissionRateLimitIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun lowRateLimit(registry: DynamicPropertyRegistry) {
            registry.add("sysdrill.build.rate-limit-per-minute") { "1" }
        }
    }

    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "build-ratelimit-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    @Test
    fun `a second build submission within the same minute is rejected with 429`() {
        val body = objectMapper.writeValueAsString(mapOf("sourceCode" to "x = 1"))

        mockMvc.perform(
            post("/build-challenges/rate-limiter/submissions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId)).content(body)
        ).andExpect(status().isCreated)
        mockMvc.perform(
            post("/build-challenges/rate-limiter/submissions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId)).content(body)
        ).andExpect(status().isTooManyRequests)
    }
}
