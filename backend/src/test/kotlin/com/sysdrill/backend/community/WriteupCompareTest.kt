package com.sysdrill.backend.community

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.content.ContentItem
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.scenario.Scenario
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioStep
import com.sysdrill.backend.scenario.ScenarioStepRepository
import com.sysdrill.backend.scenario.ScenarioVersion
import com.sysdrill.backend.scenario.ScenarioVersionRepository
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * PLAN.md Round E15 (docs/COMMUNITY_EXPANSION_PLAN.md C8) — writeup summary, the author's
 * note, structural comparison and "different from mine" ordering. A one-step coupon scenario
 * of its own (with a creator, so it stays out of the official pool other tests count).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["sysdrill.evaluation.rate-limit-per-minute=100"])
class WriteupCompareTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val contentItemRepository: ContentItemRepository,
    @Autowired val scenarioRepository: ScenarioRepository,
    @Autowired val scenarioVersionRepository: ScenarioVersionRepository,
    @Autowired val scenarioStepRepository: ScenarioStepRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val objectMapper: tools.jackson.databind.ObjectMapper,
) {
    private fun newUser(name: String): UUID =
        userRepository.save(User(email = "cmp-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = name)).id!!

    private fun scenario(): UUID {
        val content = contentItemRepository.save(ContentItem(type = "SCENARIO", title = "비교 테스트 ${UUID.randomUUID()}"))
        val scenario = scenarioRepository.save(Scenario(contentId = content.id!!, domain = "coupon", creatorUserId = newUser("creator")))
        val version = scenarioVersionRepository.save(ScenarioVersion(scenarioId = scenario.id!!, versionNo = 1, status = "PUBLISHED"))
        scenarioStepRepository.save(ScenarioStep(scenarioVersionId = version.id!!, stepOrder = 1, stepType = "INITIAL", content = """{"prompt":"설계"}"""))
        return scenario.id!!
    }

    /** A graph where every listed node is wired to a gateway, so its trait values count (ADR-0037 edge rule). */
    private fun graph(vararg nodes: Pair<String, String>): String {
        val nodeJson = (listOf("gw" to """{"kind":"gateway"}""") + nodes.toList()).joinToString(",") { (id, data) ->
            """{"id":"$id","data":$data}"""
        }
        val edges = nodes.joinToString(",") { (id, _) -> """{"source":"gw","target":"$id"}""" }
        return """{"nodes":[$nodeJson],"edges":[$edges]}"""
    }

    private fun completeWith(user: UUID, scenarioId: UUID, graph: String, share: Boolean): UUID {
        val sessionId = mockMvc.startSession(user, scenarioId)
        mockMvc.perform(
            put("/sessions/$sessionId/topology").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user))
                .content(objectMapper.writeValueAsString(mapOf("graph" to graph)))
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user)).content("""{"rawText":"설계","clientRequestId":"${UUID.randomUUID()}"}""")
        ).andExpect(status().isCreated)
        awaitStatus(sessionId, SessionStatus.FEEDBACK_READY)
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
        awaitStatus(sessionId, SessionStatus.COMPLETED)
        if (share) {
            mockMvc.perform(
                put("/sessions/$sessionId/visibility").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerHeader(user)).content("""{"visibility":"PUBLIC","anonymous":false}""")
            ).andExpect(status().isOk)
        }
        return sessionId
    }

    private fun awaitStatus(sessionId: UUID, expected: SessionStatus) {
        val deadline = Instant.now().plus(Duration.ofSeconds(30))
        while (Instant.now().isBefore(deadline)) {
            if (sessionRepository.findById(sessionId).orElseThrow().status == expected) return
            Thread.sleep(100)
        }
        error("Session $sessionId did not reach $expected in time")
    }

    private fun getJson(path: String, user: UUID): String =
        mockMvc.perform(get(path).header("Authorization", bearerHeader(user))).andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `summary, note, comparison and different-from-mine ordering`() {
        val scenarioId = scenario()
        val viewer = newUser("viewer")
        val author = newUser("author")
        val lookalike = newUser("lookalike")
        completeWith(viewer, scenarioId, graph("c" to """{"kind":"cache","traitValues":{"cacheTtlSeconds":60}}""", "d" to """{"kind":"db"}"""), share = false)
        val authored = completeWith(
            author, scenarioId,
            graph(
                "c" to """{"kind":"cache","traitValues":{"cacheTtlSeconds":10}}""",
                "d" to """{"kind":"db","traitValues":{"dbPoolSize":200}}""",
                "q" to """{"kind":"queue"}""",
            ),
            share = true,
        )
        val similar = completeWith(lookalike, scenarioId, graph("c" to """{"kind":"cache","traitValues":{"cacheTtlSeconds":60}}""", "d" to """{"kind":"db"}"""), share = true)

        mockMvc.perform(
            put("/sessions/$authored/writeup-note").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(author)).content("""{"note":"큐로 발급을 비동기화했습니다."}""")
        ).andExpect(status().isOk)
        mockMvc.perform(
            put("/sessions/$authored/writeup-note").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(viewer)).content("""{"note":"남의 글"}""")
        ).andExpect(status().isNotFound)

        val detail = getJson("/writeups/$authored", viewer)
        assertThat(JsonPath.read<String>(detail, "$.note")).isEqualTo("큐로 발급을 비동기화했습니다.")
        assertThat(JsonPath.read<List<String>>(detail, "$.summary.changedTraits[*].key")).containsExactly("dbPoolSize")
        assertThat(JsonPath.read<Map<String, Int>>(detail, "$.summary.nodeKinds")).containsKeys("queue", "cache", "db", "gateway")

        val comparison = getJson("/writeups/$authored/compare", viewer)
        assertThat(JsonPath.read<List<String>>(comparison, "$.onlyTheirs")).containsExactly("queue")
        assertThat(JsonPath.read<List<String>>(comparison, "$.onlyMine")).isEmpty()
        assertThat(JsonPath.read<List<String>>(comparison, "$.traitDiffs[*].key")).containsExactlyInAnyOrder("cacheTtlSeconds", "dbPoolSize")
        assertThat(JsonPath.read<String>(comparison, "$.largestDifference")).isEqualTo("cacheTtlSeconds") // 60 vs 10 beats 50 vs 200

        val list = getJson("/scenarios/$scenarioId/writeups", viewer)
        assertThat(JsonPath.read<List<String>>(list, "$.writeups[*].sessionId")).containsExactly(authored.toString(), similar.toString())
        assertThat(JsonPath.read<Double>(list, "$.writeups[1].distance")).isEqualTo(0.0)
        val byRecent = getJson("/scenarios/$scenarioId/writeups?sort=recent", viewer)
        assertThat(JsonPath.read<List<String>>(byRecent, "$.writeups[*].sessionId")).containsExactly(similar.toString(), authored.toString())
    }
}
