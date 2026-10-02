package com.sysdrill.backend.mission

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** PLAN.md Round E27 (docs/DRILLS_EXPANSION_PLAN.md M12) — my runbook against what I actually did in the next incident. */
@SpringBootTest
@AutoConfigureMockMvc
class RunbookIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private fun newUser(): UUID =
        userRepository.save(User(email = "rb-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "rb")).id!!

    private fun saveRunbook(user: UUID, domain: String, body: String) = mockMvc.perform(
        put("/me/runbooks/$domain").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user)).content(body)
    )

    @Test
    fun `each step is checked against looks and actions, notes are never matched`() {
        val user = newUser()
        saveRunbook(
            user, "coupon",
            """[{"type":"OPEN_PANEL","target":"logs","text":"로그부터 본다"},
                {"type":"INSPECT_NODE","target":"db","text":"DB 커넥션 풀 확인"},
                {"type":"ACTION","target":"INCREASE_DB_POOL","text":"풀이 원인이면 증설"},
                {"type":"NOTE","text":"고객 공지 먼저"}]""",
        ).andExpect(status().isOk)
        saveRunbook(user, "coupon", """[{"type":"PRAY"}]""").andExpect(status().isBadRequest)
        saveRunbook(user, "nope", "[]").andExpect(status().isNotFound)

        val sessionId = mockMvc.startSession(user)
        mockMvc.perform(post("/sessions/$sessionId/simulation/incident").header("Authorization", bearerHeader(user))).andExpect(status().isOk)
        mockMvc.perform(
            post("/sessions/$sessionId/simulation/investigations").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user))
                .content("""{"kind":"OPEN_PANEL","target":"logs"}""")
        ).andExpect(status().isNoContent)
        mockMvc.perform(
            post("/sessions/$sessionId/simulation/actions").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(user))
                .content("""{"actionType":"INCREASE_DB_POOL"}""")
        ).andExpect(status().isOk)

        val check = mockMvc.perform(get("/sessions/$sessionId/runbook-check").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<Any?>>(check, "$.steps[*].done")).containsExactly(true, false, true, null)
        mockMvc.perform(get("/sessions/$sessionId/runbook-check").header("Authorization", bearerHeader(newUser()))).andExpect(status().isNotFound)

        val mine = mockMvc.perform(get("/me/runbooks/coupon").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(mine, "$.steps[*].text")).containsExactly("로그부터 본다", "DB 커넥션 풀 확인", "풀이 원인이면 증설", "고객 공지 먼저")
    }
}
