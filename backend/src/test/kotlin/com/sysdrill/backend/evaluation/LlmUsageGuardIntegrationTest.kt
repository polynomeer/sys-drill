package com.sysdrill.backend.evaluation

import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import java.util.UUID
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

/** docs/COMMERCIALIZATION.md — a low per-user daily limit via `@DynamicPropertySource`, same isolation reasoning as RateLimitInterceptorIntegrationTest. */
@SpringBootTest
@AutoConfigureMockMvc
class LlmUsageGuardIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun lowDailyLimit(registry: DynamicPropertyRegistry) {
            registry.add("sysdrill.evaluation.daily-limit-per-user") { "1" }
        }
    }

    @Test
    fun `a second submission past the daily limit is rejected, but a different user is unaffected`() {
        val user = userRepository.save(User(email = "quota-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "quota-user"))
        val otherUser = userRepository.save(User(email = "quota-other-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "quota-other"))

        val session1 = mockMvc.startSession(user.id!!)
        mockMvc.perform(
            post("/sessions/$session1/submissions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user.id!!))
                .content("""{"rawText":"첫 번째 제출"}""")
        ).andExpect(status().isCreated)

        val session2 = mockMvc.startSession(user.id!!)
        mockMvc.perform(
            post("/sessions/$session2/submissions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user.id!!))
                .content("""{"rawText":"한도 초과 제출"}""")
        ).andExpect(status().isConflict)

        val otherSession = mockMvc.startSession(otherUser.id!!)
        mockMvc.perform(
            post("/sessions/$otherSession/submissions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(otherUser.id!!))
                .content("""{"rawText":"다른 사용자 제출"}""")
        ).andExpect(status().isCreated)
    }
}
