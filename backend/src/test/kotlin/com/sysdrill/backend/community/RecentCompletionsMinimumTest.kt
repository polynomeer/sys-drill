package com.sysdrill.backend.community

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.time.Instant
import java.util.UUID

/**
 * docs/CODECRAFTERS_BENCHMARK.md §7 — with fewer than three distinct visible
 * completers the "recent completions" list stays empty, so one or two names
 * can't single anyone out. Uses a freshly published community scenario so the
 * count isn't shared with other tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecentCompletionsMinimumTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
) {

    private fun user(prefix: String): UUID = userRepository.save(
        User(email = "$prefix-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "$prefix-${UUID.randomUUID().toString().take(6)}")
    ).id!!

    private fun complete(userId: UUID, scenarioId: UUID) {
        val session = sessionRepository.findById(mockMvc.startSession(userId, scenarioId = scenarioId)).orElseThrow()
        session.status = SessionStatus.COMPLETED
        session.completedAt = Instant.now()
        sessionRepository.save(session)
    }

    private fun recent(scenarioId: UUID, viewer: UUID): List<String> = JsonPath.read(
        mockMvc.perform(get("/community/scenarios/$scenarioId/recent-completions").header("Authorization", bearerHeader(viewer)))
            .andReturn().response.contentAsString,
        "$[*].nickname",
    )

    @Test
    fun `names appear only once three different people have completed it`() {
        val author = user("author")
        val published = mockMvc.perform(
            post("/marketplace/scenarios").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(author))
                .content("""{"title":"최소 인원 확인용","domain":"min-completers","initialPrompt":"a","followupPrompt":"b"}""")
        ).andReturn().response.contentAsString
        val scenarioId = UUID.fromString(JsonPath.read(published, "$.id"))

        complete(user("first"), scenarioId)
        complete(user("second"), scenarioId)
        assertThat(recent(scenarioId, author)).isEmpty()

        complete(user("third"), scenarioId)
        assertThat(recent(scenarioId, author)).hasSize(3)
    }
}
