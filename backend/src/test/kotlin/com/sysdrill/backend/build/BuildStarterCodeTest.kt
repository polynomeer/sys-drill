package com.sysdrill.backend.build

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.File
import java.util.UUID

/**
 * PLAN.md Round E2 (docs/LEARNING_EXPANSION_PLAN.md L4-b) — `/bridge` now starts
 * every challenge from the DB stub instead of two frontend constants. The stub a
 * learner sees in the browser and the one they `git clone` from `challenges/`
 * must be the same file, so this pins every one byte-for-byte: editing one
 * without a new migration for the other fails here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BuildStarterCodeTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {

    private fun user(): UUID =
        userRepository.save(User(email = "stub-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "stub")).id!!

    @Test
    fun `every challenge's DB stub is the file in challenges`() {
        val token = bearerHeader(user())
        val list = mockMvc.perform(get("/build-challenges").header("Authorization", token))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val slugs: List<String> = JsonPath.read(list, "$[*].slug")
        assertThat(slugs).containsExactlyInAnyOrder(
            "rate-limiter", "rate-limiter-ts", "rate-limiter-java", "rate-limiter-kotlin", "rate-limiter-go", "queue", "circuit-breaker", "distributed-lock", "retry-backoff", "event-bus",
        )
        val stageCounts: List<Int> = JsonPath.read(list, "$[*].stageCount")
        assertThat(stageCounts).allMatch { it > 0 }

        slugs.forEach { slug ->
            val detail = mockMvc.perform(get("/build-challenges/$slug").header("Authorization", token))
                .andExpect(status().isOk).andReturn().response.contentAsString
            val fileName: String = JsonPath.read(detail, "$.sourceFileName")
            val starter: String = JsonPath.read(detail, "$.starterCode")
            assertThat(starter)
                .describedAs("challenges/$slug/$fileName")
                .isEqualTo(File("../challenges/$slug/$fileName").readText())
        }
    }

    @Test
    fun `the challenge list needs a token like the rest of build-challenges`() {
        mockMvc.perform(get("/build-challenges")).andExpect(status().isUnauthorized)
    }
}
