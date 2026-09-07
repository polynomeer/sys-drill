package com.sysdrill.backend.scenario

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Phase 5 — Scenario Marketplace (docs/adr/0031): any authenticated user publishes a scenario with no organization, no payment, no approval queue. */
@SpringBootTest
@AutoConfigureMockMvc
class MarketplaceControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private fun createUser(prefix: String): User =
        userRepository.save(User(email = "$prefix-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = prefix))

    private val publishBody = """
        {"title":"레이트 리미터 장애","difficulty":"MEDIUM","domain":"community-rate-limit",
         "initialPrompt":"초기 설계 프롬프트","followupPrompt":"꼬리설계 프롬프트"}
    """.trimIndent()

    @Test
    fun `publishing requires auth, and any authenticated user can publish without an organization`() {
        mockMvc.perform(post("/marketplace/scenarios").contentType(MediaType.APPLICATION_JSON).content(publishBody))
            .andExpect(status().isUnauthorized)

        val creator = createUser("mp-creator")
        mockMvc.perform(
            post("/marketplace/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(creator.id!!))
                .content(publishBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("레이트 리미터 장애"))
            .andExpect(jsonPath("$.organizationId").doesNotExist())
            .andExpect(jsonPath("$.creatorNickname").value("mp-creator"))
    }

    @Test
    fun `a published scenario appears on both the marketplace list and the public scenario list, and anyone can start a session against it`() {
        val creator = createUser("mp-creator2")
        val response = mockMvc.perform(
            post("/marketplace/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(creator.id!!))
                .content(publishBody)
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val scenarioId = UUID.fromString(JsonPath.read(response, "$.id"))

        mockMvc.perform(get("/marketplace/scenarios").header("Authorization", bearerHeader(creator.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$scenarioId')].creatorNickname").value("mp-creator2"))

        mockMvc.perform(get("/scenarios"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$scenarioId')].creatorNickname").value("mp-creator2"))

        mockMvc.perform(get("/scenarios/$scenarioId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.creatorNickname").value("mp-creator2"))

        // An unrelated user, in no organization at all, can still start a session — no membership needed.
        val player = createUser("mp-player")
        val sessionResponse = mockMvc.perform(
            post("/sessions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(player.id!!))
                .content("""{"scenarioId":"$scenarioId"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        assertThat(JsonPath.read<String>(sessionResponse, "$.domain")).isEqualTo("community-rate-limit")
    }

    @Test
    fun `GET marketplace scenarios mine only returns the caller's own published scenarios`() {
        val creator = createUser("mp-creator3")
        val other = createUser("mp-other")
        mockMvc.perform(
            post("/marketplace/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(creator.id!!))
                .content(publishBody)
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/marketplace/scenarios/mine").header("Authorization", bearerHeader(creator.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))

        mockMvc.perform(get("/marketplace/scenarios/mine").header("Authorization", bearerHeader(other.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
