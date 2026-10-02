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

/** PLAN.md Round E23 (docs/COMMUNITY_EXPANSION_PLAN.md C11) — the Community home's three feeds. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["sysdrill.evaluation.rate-limit-per-minute=100"])
class CommunityHomeTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val contentItemRepository: ContentItemRepository,
    @Autowired val scenarioRepository: ScenarioRepository,
    @Autowired val scenarioVersionRepository: ScenarioVersionRepository,
    @Autowired val scenarioStepRepository: ScenarioStepRepository,
    @Autowired val sessionRepository: SessionRepository,
) {
    private fun newUser(): UUID =
        userRepository.save(User(email = "home-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "home")).id!!

    private fun scenario(): UUID {
        val content = contentItemRepository.save(ContentItem(type = "SCENARIO", title = "홈 테스트 ${UUID.randomUUID()}"))
        val scenario = scenarioRepository.save(Scenario(contentId = content.id!!, domain = "coupon", creatorUserId = newUser()))
        val version = scenarioVersionRepository.save(ScenarioVersion(scenarioId = scenario.id!!, versionNo = 1, status = "PUBLISHED"))
        scenarioStepRepository.save(ScenarioStep(scenarioVersionId = version.id!!, stepOrder = 1, stepType = "INITIAL", content = """{"prompt":"설계"}"""))
        return scenario.id!!
    }

    private fun complete(user: UUID, scenarioId: UUID, share: Boolean): UUID {
        val sessionId = mockMvc.startSession(user, scenarioId)
        mockMvc.perform(
            post("/sessions/$sessionId/submissions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user)).content("""{"rawText":"설계","clientRequestId":"${UUID.randomUUID()}"}""")
        ).andExpect(status().isCreated)
        val deadline = Instant.now().plus(Duration.ofSeconds(30))
        while (sessionRepository.findById(sessionId).orElseThrow().status != SessionStatus.FEEDBACK_READY) {
            check(Instant.now().isBefore(deadline)) { "no feedback" }
            Thread.sleep(100)
        }
        mockMvc.perform(post("/sessions/$sessionId/advance").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
        if (share) {
            mockMvc.perform(
                put("/sessions/$sessionId/visibility").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerHeader(user)).content("""{"visibility":"PUBLIC","anonymous":false}""")
            ).andExpect(status().isOk)
        }
        return sessionId
    }

    private fun home(user: UUID, since: Instant? = null): String =
        mockMvc.perform(get("/community/home" + (since?.let { "?since=$it" } ?: "")).header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `what's new in my drills, busy threads and the most reviewed writeups`() {
        val scenarioId = scenario()
        val viewer = newUser()
        val author = newUser()
        val stranger = newUser()
        val before = Instant.now().minusSeconds(1)
        complete(viewer, scenarioId, share = false)
        val writeup = complete(author, scenarioId, share = true)
        mockMvc.perform(
            post("/scenarios/$scenarioId/discussion").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(author))
                .content("""{"body":"Redis로 재고를 원자적으로 줄였습니다.\n자세히는...","kind":"DESIGN","containsSpoiler":true}""")
        ).andExpect(status().isCreated)
        repeat(4) {
            mockMvc.perform(
                post("/writeups/$writeup/comments").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(viewer))
                    .content("""{"kind":"QUESTION","body":"질문 $it"}""")
            ).andExpect(status().isCreated)
        }

        val mine = home(viewer, since = before)
        val drill = JsonPath.read<List<Map<String, Any?>>>(mine, "$.myDrills").single { it["scenarioId"] == scenarioId.toString() }
        assertThat(drill["writeups"]).isEqualTo(1)
        assertThat(drill["newWriteups"]).isEqualTo(1)
        assertThat(drill["newDiscussions"]).isEqualTo(1)
        assertThat(home(viewer, since = Instant.now().plusSeconds(5)).let { JsonPath.read<List<Int>>(it, "$.myDrills[?(@.scenarioId == '$scenarioId')].newWriteups") })
            .containsExactly(0)

        val thread = JsonPath.read<List<Map<String, Any?>>>(mine, "$.activeDiscussions").firstOrNull { it["scenarioId"] == scenarioId.toString() }
        @Suppress("UNCHECKED_CAST")
        if (thread != null) assertThat((thread["latest"] as List<Map<String, Any?>>).first()["excerpt"]).isEqualTo("Redis로 재고를 원자적으로 줄였습니다.")

        val notableForViewer = JsonPath.read<List<Map<String, Any?>>>(mine, "$.notableWriteups").single { it["sessionId"] == writeup.toString() }
        assertThat(notableForViewer["reviewCount"]).isEqualTo(4)
        assertThat(notableForViewer["locked"]).isEqualTo(false)

        val forStranger = home(stranger)
        assertThat(JsonPath.read<List<Any>>(forStranger, "$.myDrills")).isEmpty()
        JsonPath.read<List<Map<String, Any?>>>(forStranger, "$.notableWriteups").firstOrNull { it["sessionId"] == writeup.toString() }?.let {
            assertThat(it["locked"]).isEqualTo(true)
            assertThat(it["authorNickname"]).isNull()
        }
        JsonPath.read<List<Map<String, Any?>>>(forStranger, "$.activeDiscussions").firstOrNull { it["scenarioId"] == scenarioId.toString() }?.let {
            @Suppress("UNCHECKED_CAST")
            val post = (it["latest"] as List<Map<String, Any?>>).first()
            assertThat(post["spoilerLocked"]).isEqualTo(true)
            assertThat(post["excerpt"]).isNull()
        }
    }
}
