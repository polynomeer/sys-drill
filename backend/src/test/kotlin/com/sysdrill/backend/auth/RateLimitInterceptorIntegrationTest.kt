package com.sysdrill.backend.auth

import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
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

/**
 * docs/COMMERCIALIZATION.md — a low limit set via `@DynamicPropertySource`
 * gives this class its own cached Spring context (a different property value
 * than every other test class using the production default). The underlying
 * Redis instance is still shared with every other test class in the same
 * run, though, so this doesn't assert an exact call count -- some other
 * class's /auth/login traffic against the same client IP may have already
 * incremented the counter -- only that a 429 eventually appears within a
 * budget well above the configured limit.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitInterceptorIntegrationTest(
    @Autowired val mockMvc: MockMvc,
) {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun lowRateLimit(registry: DynamicPropertyRegistry) {
            registry.add("sysdrill.auth.rate-limit.max-requests-per-minute") { "3" }
        }
    }

    @Test
    fun `enough requests from the same client eventually trip the rate limit`() {
        val sawRateLimited = (1..20).any { attempt ->
            val status = mockMvc.perform(
                post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"attempt-$attempt-${UUID.randomUUID()}@example.com","password":"whatever"}""")
            ).andReturn().response.status
            status == 429
        }
        assertThat(sawRateLimited).isTrue()
    }
}
