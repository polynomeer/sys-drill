package com.sysdrill.backend.community

import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.scenario.ScenarioRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §6.2 / ADR-0041 — 풀이 공유.
 *
 * 공개된 풀이는 DB 에 쌓여 있는 다른 테스트의 세션과 섞이므로, 목록의 **개수**가
 * 아니라 **내 세션이 들어 있는가/빠졌는가**를 단언한다(벤치마크·랭킹 테스트와
 * 같은 이유 — docs/TESTING.md §1).
 */
@SpringBootTest
@AutoConfigureMockMvc
class WriteupIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val scenarioRepository: ScenarioRepository,
) {

    private val couponScenarioId: UUID by lazy {
        scenarioRepository.findAll()
            .first { it.domain == "coupon" && it.organizationId == null && it.creatorUserId == null }
            .id!!
    }

    private fun newUser(nickname: String = "writeup-user"): UUID = userRepository.save(
        User(email = "writeup-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = nickname)
    ).id!!

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus, timeout: Duration = Duration.ofSeconds(30)) {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    private fun completeSession(userId: UUID): UUID {
        val sessionId = mockMvc.startSession(userId)
        repeat(3) {
            mockMvc.perform(
                post("/sessions/$sessionId/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerHeader(userId))
                    .content("""{"rawText":"Redis 재고 카운터와 Kafka 비동기 발급으로 처리합니다."}""")
            ).andExpect(status().isCreated)
            awaitStatus(sessionId, SessionStatus.FEEDBACK_READY)
            mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(userId)))
                .andExpect(status().isOk)
        }
        awaitStatus(sessionId, SessionStatus.COMPLETED)
        return sessionId
    }

    private fun share(sessionId: UUID, userId: UUID, anonymous: Boolean = false) =
        mockMvc.perform(
            put("/sessions/$sessionId/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"visibility":"PUBLIC","anonymous":$anonymous}""")
        ).andExpect(status().isOk)

    private fun listWriteups(userId: UUID) =
        mockMvc.perform(
            get("/scenarios/$couponScenarioId/writeups").header("Authorization", bearerHeader(userId))
        )

    private fun sharedSessionIds(userId: UUID): List<String> {
        val json = listWriteups(userId).andReturn().response.contentAsString
        return Regex("\"sessionId\":\"([0-9a-f-]+)\"").findAll(json).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `완료해도 기본은 비공개다`() {
        val userId = newUser()
        val sessionId = completeSession(userId)

        mockMvc.perform(get("/sessions/$sessionId/visibility").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.visibility").value("PRIVATE"))
            .andExpect(jsonPath("$.sharedAt").doesNotExist())

        // 공개하지 않았으니 완료자의 목록에도 내 세션이 없다.
        assertThat(sharedSessionIds(userId)).doesNotContain(sessionId.toString())
    }

    @Test
    fun `끝내지 않은 세션은 공개할 수 없다`() {
        val userId = newUser()
        val sessionId = mockMvc.startSession(userId)

        mockMvc.perform(
            put("/sessions/$sessionId/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"visibility":"PUBLIC"}""")
        ).andExpect(status().isConflict)
    }

    @Test
    fun `공개하면 같은 시나리오를 완료한 사람이 본문을 본다`() {
        val author = newUser("writeup-author")
        val sessionId = completeSession(author)
        share(sessionId, author)

        val reader = newUser("writeup-reader")
        completeSession(reader)

        listWriteups(reader)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.locked").value(false))

        assertThat(sharedSessionIds(reader)).contains(sessionId.toString())

        mockMvc.perform(get("/writeups/$sessionId").header("Authorization", bearerHeader(reader)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authorNickname").value("writeup-author"))
            .andExpect(jsonPath("$.anonymous").value(false))
            .andExpect(jsonPath("$.mine").value(false))
            // 설계 답안 원문이 단계별로 들어 있어야 공개할 가치가 있다.
            .andExpect(jsonPath("$.phases.length()").value(3))
            .andExpect(jsonPath("$.phases[0].answer").isNotEmpty)
            .andExpect(jsonPath("$.phases[0].score").isNumber)
            .andExpect(jsonPath("$.averageScore").isNumber)
    }

    @Test
    fun `완료하지 않은 사람에게는 목록이 잠기고 상세는 403이다`() {
        val author = newUser()
        val sessionId = completeSession(author)
        share(sessionId, author)

        val stranger = newUser()

        listWriteups(stranger)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.locked").value(true))
            // 닉네임도 점수도 주지 않는다 — 목록 자체가 스포일러다.
            .andExpect(jsonPath("$.writeups").isEmpty)
            // 다만 몇 편이 기다리는지는 알려준다(ADR-0041 이 노린 동기).
            .andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))

        mockMvc.perform(get("/writeups/$sessionId").header("Authorization", bearerHeader(stranger)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `공개하지 않은 풀이는 세션 id 를 알아도 404다`() {
        val author = newUser()
        val sessionId = completeSession(author)

        val reader = newUser()
        completeSession(reader) // 열람 자격은 갖췄지만 공개된 글이 아니다

        mockMvc.perform(get("/writeups/$sessionId").header("Authorization", bearerHeader(reader)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `익명으로 공개하면 닉네임이 내려가지 않는다`() {
        val author = newUser("writeup-secret")
        val sessionId = completeSession(author)
        share(sessionId, author, anonymous = true)

        val reader = newUser()
        completeSession(reader)

        mockMvc.perform(get("/writeups/$sessionId").header("Authorization", bearerHeader(reader)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.anonymous").value(true))
            .andExpect(jsonPath("$.authorNickname").doesNotExist())
    }

    @Test
    fun `공개를 철회하면 목록에서 빠지고 공개 시각도 지워진다`() {
        val author = newUser()
        val sessionId = completeSession(author)
        share(sessionId, author)
        assertThat(sharedSessionIds(author)).contains(sessionId.toString())

        mockMvc.perform(
            put("/sessions/$sessionId/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(author))
                .content("""{"visibility":"PRIVATE"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.visibility").value("PRIVATE"))
            .andExpect(jsonPath("$.sharedAt").doesNotExist())

        assertThat(sharedSessionIds(author)).doesNotContain(sessionId.toString())
    }

    @Test
    fun `남의 세션은 공개할 수 없다`() {
        val author = newUser()
        val sessionId = completeSession(author)

        mockMvc.perform(
            put("/sessions/$sessionId/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(newUser()))
                .content("""{"visibility":"PUBLIC"}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `모르는 공개 값은 400 - 조용히 비공개로 처리하지 않는다`() {
        val userId = newUser()
        val sessionId = completeSession(userId)

        mockMvc.perform(
            put("/sessions/$sessionId/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"visibility":"UNLISTED"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `토큰 없이는 풀이 목록도 상세도 볼 수 없다`() {
        mockMvc.perform(get("/scenarios/$couponScenarioId/writeups")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/writeups/${UUID.randomUUID()}")).andExpect(status().isUnauthorized)
    }
}
