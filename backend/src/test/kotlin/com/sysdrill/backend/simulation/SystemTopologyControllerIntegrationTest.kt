package com.sysdrill.backend.simulation

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** ADR-0037 next slice (persistence-only) — the canvas graph, opaque JSON in/out. */
@SpringBootTest
@AutoConfigureMockMvc
class SystemTopologyControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "topology-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    @Test
    fun `an unsaved session returns an empty graph`() {
        val sessionId = mockMvc.startSession(userId)

        mockMvc.perform(get("/sessions/$sessionId/topology").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saved").value(false))
            .andExpect(jsonPath("$.graph").value("""{"nodes":[],"edges":[]}"""))
    }

    @Test
    fun `saving a graph persists it and a second save upserts the same row`() {
        val sessionId = mockMvc.startSession(userId)
        val firstGraph = """{"nodes":[{"id":"n1","data":{"kind":"db"}}],"edges":[]}"""
        val secondGraph = """{"nodes":[{"id":"n1","data":{"kind":"db"}},{"id":"n2","data":{"kind":"cache"}}],"edges":[]}"""

        mockMvc.perform(
            put("/sessions/$sessionId/topology").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"graph":${jsonEscape(firstGraph)}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saved").value(true))

        mockMvc.perform(
            put("/sessions/$sessionId/topology").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"graph":${jsonEscape(secondGraph)}}""")
        ).andExpect(status().isOk)

        val body = mockMvc.perform(get("/sessions/$sessionId/topology").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saved").value(true))
            .andReturn().response.contentAsString

        // Compared as parsed structures, not raw text — jsonb round-trips content, not exact formatting.
        val actualGraph = objectMapper.readValue(JsonPath.read<String>(body, "$.graph"), Map::class.java)
        val expectedGraph = objectMapper.readValue(secondGraph, Map::class.java)
        assertThat(actualGraph).isEqualTo(expectedGraph)
    }

    @Test
    fun `a caller who does not own the session gets not-found on both read and write`() {
        val ownerId = userId
        val strangerId = userRepository.save(
            User(email = "stranger-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "stranger")
        ).id!!
        val sessionId = mockMvc.startSession(ownerId)

        mockMvc.perform(get("/sessions/$sessionId/topology").header("Authorization", bearerHeader(strangerId)))
            .andExpect(status().isNotFound)
        mockMvc.perform(
            put("/sessions/$sessionId/topology").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(strangerId))
                .content("""{"graph":"{\"nodes\":[],\"edges\":[]}"}""")
        ).andExpect(status().isNotFound)
    }

    /** Embeds a JSON string as a JSON-string-literal value inside a hand-written request body (MockMvc content is raw text, not a serializer). */
    private fun jsonEscape(raw: String): String =
        "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
