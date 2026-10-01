package com.sysdrill.backend.build

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.submitBuildChallenge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * PLAN.md Round B12 — `GET /build-challenges/{slug}/submissions/latest` is how
 * /bridge picks up a submission made from `submit.sh`. Only the newest one, only
 * the caller's, only for that challenge. Doesn't wait for grading: the id is
 * what matters here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BuildLatestSubmissionTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {

    private fun user(): UUID =
        userRepository.save(User(email = "latest-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "latest")).id!!

    private fun latestId(slug: String, userId: UUID): String? {
        val result = mockMvc.perform(get("/build-challenges/$slug/submissions/latest").header("Authorization", bearerHeader(userId)))
            .andReturn().response
        return if (result.status == 200) JsonPath.read(result.contentAsString, "$.id") else null
    }

    @Test
    fun `latest is the caller's newest submission to that challenge`() {
        val me = user()
        val other = user()
        assertThat(latestId("rate-limiter", me)).isNull()

        mockMvc.submitBuildChallenge(objectMapper, "rate-limiter", me, "x = 1")
        val newest = mockMvc.submitBuildChallenge(objectMapper, "rate-limiter", me, "x = 2")
        mockMvc.submitBuildChallenge(objectMapper, "rate-limiter-ts", me, "export const x = 3;")
        mockMvc.submitBuildChallenge(objectMapper, "rate-limiter", other, "x = 4")

        assertThat(latestId("rate-limiter", me)).isEqualTo(newest.toString())
    }

    @Test
    fun `no submission yet is a 404 and the endpoint needs a token`() {
        mockMvc.perform(get("/build-challenges/rate-limiter/submissions/latest").header("Authorization", bearerHeader(user())))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/build-challenges/rate-limiter/submissions/latest")).andExpect(status().isUnauthorized)
    }
}
