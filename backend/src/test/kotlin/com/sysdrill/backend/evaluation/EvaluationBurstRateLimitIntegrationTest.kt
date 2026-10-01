package com.sysdrill.backend.evaluation

import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
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
import java.util.UUID

/**
 * docs/COMMERCIALIZATION.md — [LlmUsageGuard]'s daily cap doesn't stop a
 * burst that spends the whole day's budget in seconds; this is the separate
 * per-minute check on top of it. Kept in its own file/context (same
 * low-limit-override reasoning as [LlmUsageGuardIntegrationTest]) rather
 * than added to it or to [EvaluationWorkerIntegrationTest]/
 * [EvaluationWorkerConcurrencyTest], both of which submit more than once per
 * user and would break under a class-wide rate-limit-per-minute=1 override.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EvaluationBurstRateLimitIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun lowRateLimit(registry: DynamicPropertyRegistry) {
            registry.add("sysdrill.evaluation.rate-limit-per-minute") { "1" }
        }
    }

    @Test
    fun `a second submission within the same minute is rejected with 429, below the daily cap`() {
        val userId = userRepository.save(
            User(email = "eval-burst-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!

        val session1 = mockMvc.startSession(userId)
        mockMvc.perform(
            post("/sessions/$session1/submissions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"rawText":"첫 번째 제출"}""")
        ).andExpect(status().isCreated)

        val session2 = mockMvc.startSession(userId)
        mockMvc.perform(
            post("/sessions/$session2/submissions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"rawText":"분당 한도 초과 제출"}""")
        ).andExpect(status().isTooManyRequests)
    }
}
