package com.sysdrill.backend.architecture

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
import tools.jackson.databind.ObjectMapper

/** Phase 6 — Architecture Linter v1 (docs/adr/0034): OpenAPI-only input, no raw-spec retention, uploader-only visibility. */
@SpringBootTest
@AutoConfigureMockMvc
class ArchitectureAnalysisControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {
    private fun createUser(prefix: String): User =
        userRepository.save(User(email = "$prefix-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = prefix))

    // POST /orders: no error responses, no security, no request body schema.
    // GET /orders: no error responses, unbounded array response, no pagination param.
    private val flawedSpec = """
        openapi: 3.0.0
        info:
          title: Flawed Orders API
          version: "1.0"
        paths:
          /orders:
            post:
              responses:
                '200':
                  description: OK
            get:
              responses:
                '200':
                  description: OK
                  content:
                    application/json:
                      schema:
                        type: array
                        items:
                          type: object
    """.trimIndent()

    private fun analyzeRequestBody(spec: String): String = objectMapper.writeValueAsString(mapOf("openApiSpec" to spec))

    @Test
    fun `analyzing a flawed OpenAPI spec creates a private scenario with the expected findings`() {
        val user = createUser("arch-user")

        val response = mockMvc.perform(
            post("/architecture-analysis").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user.id!!))
                .content(analyzeRequestBody(flawedSpec))
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.scenario.title").value("Flawed Orders API"))
            .andExpect(jsonPath("$.scenario.organizationId").doesNotExist())
            .andReturn().response.contentAsString

        val findings = JsonPath.read<List<String>>(response, "$.findings")
        assertThat(findings).isNotEmpty()
        assertThat(findings.joinToString(" ")).contains("에러 응답").contains("인증").contains("페이지네이션").contains("요청 본문")
    }

    @Test
    fun `a generated scenario is private to its creator — invisible on public lists, unplayable by others, 404 on detail`() {
        val creator = createUser("arch-creator")
        val other = createUser("arch-other")

        val createResponse = mockMvc.perform(
            post("/architecture-analysis").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(creator.id!!))
                .content(analyzeRequestBody(flawedSpec))
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val scenarioId = JsonPath.read<String>(createResponse, "$.scenario.id")

        mockMvc.perform(get("/scenarios"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$scenarioId')]").isEmpty)

        mockMvc.perform(get("/marketplace/scenarios").header("Authorization", bearerHeader(creator.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$scenarioId')]").isEmpty)

        mockMvc.perform(get("/scenarios/$scenarioId"))
            .andExpect(status().isNotFound)

        mockMvc.perform(get("/architecture-analysis/scenarios").header("Authorization", bearerHeader(creator.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$scenarioId')]").isNotEmpty)
        mockMvc.perform(get("/architecture-analysis/scenarios").header("Authorization", bearerHeader(other.id!!)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$scenarioId')]").isEmpty)

        mockMvc.perform(
            post("/sessions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(other.id!!))
                .content("""{"scenarioId":"$scenarioId"}""")
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            post("/sessions").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(creator.id!!))
                .content("""{"scenarioId":"$scenarioId"}""")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.domain").value("architecture-analysis"))
    }

    @Test
    fun `analyzing requires auth`() {
        mockMvc.perform(post("/architecture-analysis").contentType(MediaType.APPLICATION_JSON).content("""{"openApiSpec":"x"}"""))
            .andExpect(status().isUnauthorized)
    }
}
