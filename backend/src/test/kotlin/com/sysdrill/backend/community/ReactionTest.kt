package com.sysdrill.backend.community

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
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

/** PLAN.md Round E32 (docs/COMMUNITY_EXPANSION_PLAN.md C14) — typed reactions and per-domain reputation. */
@SpringBootTest
@AutoConfigureMockMvc
class ReactionTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val discussionRepository: ScenarioDiscussionRepository,
) {
    private val coupon = UUID.fromString("a0000000-0000-0000-0000-000000000002")

    private fun user(): UUID = userRepository.save(User(email = "re-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "re")).id!!

    private fun react(user: UUID, target: UUID, kind: String) = mockMvc.perform(
        post("/community/reactions").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user))
            .content("""{"targetType":"DISCUSSION","targetId":"$target","kind":"$kind"}""")
    )

    @Test
    fun `reactions toggle, never on your own post, and add up per domain without hidden posts`() {
        val author = user()
        val reader = user()
        val other = user()
        val posted = mockMvc.perform(
            post("/scenarios/$coupon/discussion").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(author))
                .content("""{"body":"Redis DECR로 재고를 원자적으로","kind":"INSIGHT"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val postId = UUID.fromString(JsonPath.read(posted, "$.id"))

        react(author, postId, "HELPFUL").andExpect(status().isBadRequest)
        react(reader, postId, "HELPFUL").andExpect(status().isOk)
        react(other, postId, "HELPFUL").andExpect(status().isOk)
        val toggled = react(reader, postId, "INSIGHT").andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<Int>(toggled, "$.counts.HELPFUL")).isEqualTo(2)
        assertThat(JsonPath.read<List<String>>(toggled, "$.mine")).containsExactlyInAnyOrder("HELPFUL", "INSIGHT")
        val undone = react(other, postId, "HELPFUL").andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<Int>(undone, "$.counts.HELPFUL")).isEqualTo(1)

        val thread = mockMvc.perform(get("/scenarios/$coupon/discussion").header("Authorization", bearerHeader(reader)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<List<String>>>(thread, "$.messages[?(@.id == '$postId')].reactions.mine").single()).containsExactlyInAnyOrder("HELPFUL", "INSIGHT")

        val reputation = mockMvc.perform(get("/community/users/$author/reputation").header("Authorization", bearerHeader(other)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<String>(reputation, "$[0].domain")).isEqualTo("coupon")
        assertThat(JsonPath.read<Int>(reputation, "$[0].total")).isEqualTo(2)

        discussionRepository.findById(postId).orElseThrow().also { it.hiddenAt = java.time.Instant.now() }.let(discussionRepository::save)
        val afterHide = mockMvc.perform(get("/community/users/$author/reputation").header("Authorization", bearerHeader(other)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<Any>>(afterHide, "$")).isEmpty()
    }
}
