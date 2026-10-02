package com.sysdrill.backend.community

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.content.ContentItem
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.scenario.Scenario
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersion
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.bearerHeader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * PLAN.md Round E3 (docs/COMMUNITY_EXPANSION_PLAN.md C7) — 답글 한 단계, 스포일러 잠금,
 * 이전 버전 토론(읽기 전용).
 *
 * 공식 시나리오에 v2를 끼워 넣으면 다른 테스트의 세션이 v2를 고르게 되므로, 이 테스트는
 * 자기만의 시나리오를 저장소로 직접 만들어 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiscussionThreadingTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val contentItemRepository: ContentItemRepository,
    @Autowired val scenarioRepository: ScenarioRepository,
    @Autowired val scenarioVersionRepository: ScenarioVersionRepository,
    @Autowired val sessionRepository: SessionRepository,
) {

    private fun newUser(): UUID =
        userRepository.save(User(email = "thread-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "thread")).id!!

    private fun newScenario(): Pair<UUID, UUID> {
        val content = contentItemRepository.save(ContentItem(type = "SCENARIO", title = "스레드 테스트 ${UUID.randomUUID()}"))
        // A creator keeps it out of the official pool (Drill Score, certification) — other tests count that pool.
        val scenario = scenarioRepository.save(Scenario(contentId = content.id!!, domain = "coupon", creatorUserId = newUser()))
        val v1 = scenarioVersionRepository.save(ScenarioVersion(scenarioId = scenario.id!!, versionNo = 1, status = "PUBLISHED"))
        return scenario.id!! to v1.id!!
    }

    private fun post(scenarioId: UUID, userId: UUID, json: String) = mockMvc.perform(
        post("/scenarios/$scenarioId/discussion")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(userId))
            .content(json)
    )

    private fun postId(scenarioId: UUID, userId: UUID, json: String): UUID = UUID.fromString(
        JsonPath.read(post(scenarioId, userId, json).andExpect(status().isCreated).andReturn().response.contentAsString, "$.id")
    )

    private fun thread(scenarioId: UUID, userId: UUID): String = mockMvc.perform(
        get("/scenarios/$scenarioId/discussion").header("Authorization", bearerHeader(userId))
    ).andExpect(status().isOk).andReturn().response.contentAsString

    @Suppress("UNCHECKED_CAST")
    private fun message(json: String, id: UUID): Map<String, Any?> =
        JsonPath.read<List<Map<String, Any?>>>(json, "$.messages").single { it["id"] == id.toString() }

    @Test
    fun `답글은 한 단계만 달 수 있다`() {
        val (scenarioId, _) = newScenario()
        val asker = newUser()
        val answerer = newUser()
        val question = postId(scenarioId, asker, """{"body":"DB 유니크 제약만으로 충분한가요?","kind":"QUESTION"}""")
        val reply = postId(scenarioId, answerer, """{"body":"재시도 경합을 생각해 보세요.","parentId":"$question","kind":"INSIGHT"}""")

        val replyJson = message(thread(scenarioId, asker), reply)
        assertThat(replyJson["parentId"]).isEqualTo(question.toString())
        assertThat(replyJson["kind"]).isEqualTo("INSIGHT")

        post(scenarioId, asker, """{"body":"답글의 답글","parentId":"$reply"}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `스포일러 글은 미완료자에게 본문이 내려가지 않는다`() {
        val (scenarioId, versionId) = newScenario()
        val author = newUser()
        val stranger = newUser()
        val completer = newUser()
        sessionRepository.save(Session(userId = completer, scenarioVersionId = versionId, status = SessionStatus.COMPLETED))

        val spoiler = postId(scenarioId, author, """{"body":"Lua 스크립트로 원자적으로 처리했습니다.","containsSpoiler":true}""")

        val forStranger = message(thread(scenarioId, stranger), spoiler)
        assertThat(forStranger["spoilerLocked"]).isEqualTo(true)
        assertThat(forStranger["body"]).isEqualTo("")

        val forCompleter = message(thread(scenarioId, completer), spoiler)
        assertThat(forCompleter["spoilerLocked"]).isEqualTo(false)
        assertThat(forCompleter["body"]).isEqualTo("Lua 스크립트로 원자적으로 처리했습니다.")

        // 작성자 본인은 언제나 자기 글을 본다.
        assertThat(message(thread(scenarioId, author), spoiler)["body"]).isEqualTo("Lua 스크립트로 원자적으로 처리했습니다.")
    }

    @Test
    fun `새 버전이 나와도 이전 버전 글은 읽기 전용으로 남는다`() {
        val (scenarioId, _) = newScenario()
        val user = newUser()
        val oldPost = postId(scenarioId, user, """{"body":"v1 질문"}""")

        scenarioVersionRepository.save(ScenarioVersion(scenarioId = scenarioId, versionNo = 2, status = "PUBLISHED"))
        val json = thread(scenarioId, user)

        assertThat(JsonPath.read<Int>(json, "$.currentVersionNo")).isEqualTo(2)
        assertThat(JsonPath.read<List<Any>>(json, "$.messages")).isEmpty()
        assertThat(JsonPath.read<Int>(json, "$.previousVersions[0].versionNo")).isEqualTo(1)
        assertThat(JsonPath.read<List<String>>(json, "$.previousVersions[0].messages[*].id")).containsExactly(oldPost.toString())

        // 이전 버전 글에는 답글을 달 수 없다 — 쓰기는 최신 버전에만.
        post(scenarioId, user, """{"body":"늦은 답","parentId":"$oldPost"}""").andExpect(status().isBadRequest)
    }
}
