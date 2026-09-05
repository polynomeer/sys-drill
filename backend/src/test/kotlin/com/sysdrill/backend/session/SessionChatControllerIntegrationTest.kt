package com.sysdrill.backend.session

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import java.util.UUID
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

/** PLAN.md step 36 — Game Day's spectate-and-chat channel: open to a session's owner and any spectator, 404 for anyone else. */
@SpringBootTest
@AutoConfigureMockMvc
class SessionChatControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {

    private fun createUser(prefix: String): User =
        userRepository.save(User(email = "$prefix-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = prefix))

    private fun createOrg(adminId: UUID): UUID {
        val response = mockMvc.perform(
            post("/organizations").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(adminId))
                .content("""{"name":"chat-test-org"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return UUID.fromString(JsonPath.read(response, "$.id"))
    }

    private fun invite(orgId: UUID, adminId: UUID, email: String): String {
        val response = mockMvc.perform(
            post("/organizations/$orgId/invitations").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(adminId))
                .content("""{"email":"$email","role":"MEMBER"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read(response, "$.token")
    }

    private fun startCustomScenarioSession(orgId: UUID, admin: UUID, owner: UUID): UUID {
        val scenarioResponse = mockMvc.perform(
            post("/organizations/$orgId/scenarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin))
                .content("""{"title":"채팅 테스트","domain":"chat-test","initialPrompt":"a","followupPrompt":"b"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val scenarioId = JsonPath.read<String>(scenarioResponse, "$.id")

        val sessionResponse = mockMvc.perform(
            post("/sessions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(owner))
                .content("""{"scenarioId":"$scenarioId"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return UUID.fromString(JsonPath.read(sessionResponse, "$.id"))
    }

    @Test
    fun `the owner and a spectator can both post and read chat messages, with correct nicknames`() {
        val admin = createUser("chat-admin")
        val owner = createUser("chat-owner")
        val spectator = createUser("chat-spectator")
        val orgId = createOrg(admin.id!!)
        for (u in listOf(owner, spectator)) {
            val token = invite(orgId, admin.id!!, u.email)
            mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(u.id!!)))
        }
        val sessionId = startCustomScenarioSession(orgId, admin.id!!, owner.id!!)

        mockMvc.perform(
            post("/sessions/$sessionId/chat").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(owner.id!!))
                .content("""{"body":"오너 메시지"}""")
        ).andExpect(status().isCreated)
        mockMvc.perform(
            post("/sessions/$sessionId/chat").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(spectator.id!!))
                .content("""{"body":"관전자 훈수"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/sessions/$sessionId/chat").header("Authorization", bearerHeader(owner.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].body").value("오너 메시지"))
            .andExpect(jsonPath("$[0].authorNickname").value("chat-owner"))
            .andExpect(jsonPath("$[1].body").value("관전자 훈수"))
            .andExpect(jsonPath("$[1].authorNickname").value("chat-spectator"))
    }

    @Test
    fun `a non-member cannot post or read chat messages`() {
        val admin = createUser("chat-admin2")
        val owner = createUser("chat-owner2")
        val outsider = createUser("chat-outsider2")
        val orgId = createOrg(admin.id!!)
        val token = invite(orgId, admin.id!!, owner.email)
        mockMvc.perform(post("/organizations/invitations/$token/accept").header("Authorization", bearerHeader(owner.id!!)))
        val sessionId = startCustomScenarioSession(orgId, admin.id!!, owner.id!!)

        mockMvc.perform(get("/sessions/$sessionId/chat").header("Authorization", bearerHeader(outsider.id!!)))
            .andExpect(status().isNotFound)
        mockMvc.perform(
            post("/sessions/$sessionId/chat").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(outsider.id!!))
                .content("""{"body":"x"}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `posting a blank chat message is rejected`() {
        val admin = createUser("chat-admin3")
        val orgId = createOrg(admin.id!!)
        val sessionId = startCustomScenarioSession(orgId, admin.id!!, admin.id!!)

        mockMvc.perform(
            post("/sessions/$sessionId/chat").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(admin.id!!))
                .content("""{"body":""}""")
        ).andExpect(status().isBadRequest)
    }
}
