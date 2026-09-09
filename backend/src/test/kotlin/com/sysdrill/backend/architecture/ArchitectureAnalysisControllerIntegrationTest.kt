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

    // Mirrors flawedSpec's two operations, but each rule's condition is satisfied: error
    // responses defined, auth required (via a top-level `security:` requirement rather than
    // per-operation, to exercise that branch specifically), a pagination param on the list
    // endpoint, and a request body schema on the write endpoint.
    private val cleanSpec = """
        openapi: 3.0.0
        info:
          title: Clean Orders API
          version: "1.0"
        security:
          - bearerAuth: []
        components:
          securitySchemes:
            bearerAuth:
              type: http
              scheme: bearer
        paths:
          /orders:
            post:
              requestBody:
                content:
                  application/json:
                    schema:
                      type: object
              responses:
                '200':
                  description: OK
                '400':
                  description: Bad Request
            get:
              parameters:
                - name: page
                  in: query
                  schema:
                    type: integer
              responses:
                '200':
                  description: OK
                  content:
                    application/json:
                      schema:
                        type: array
                        items:
                          type: object
                '400':
                  description: Bad Request
    """.trimIndent()

    private fun analyzeRequestBody(spec: String): String = objectMapper.writeValueAsString(mapOf("openApiSpec" to spec))

    @Test
    fun `analyzing a well-formed OpenAPI spec finds nothing -- rules don't false-positive on satisfied requirements`() {
        val user = createUser("arch-clean-user")

        mockMvc.perform(
            post("/architecture-analysis").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user.id!!))
                .content(analyzeRequestBody(cleanSpec))
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.scenario.title").value("Clean Orders API"))
            .andExpect(jsonPath("$.findings").isEmpty)
    }

    @Test
    fun `analyzing an unparseable spec is rejected as a bad request`() {
        val user = createUser("arch-bad-user")

        mockMvc.perform(
            post("/architecture-analysis").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user.id!!))
                .content(analyzeRequestBody("this is not an OpenAPI document"))
        ).andExpect(status().isBadRequest)
    }

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

        // POST /orders is missing auth (HIGH) as well as error responses/request validation (MEDIUM) -- HIGH wins.
        // GET /orders is only missing error responses/pagination (both MEDIUM) -- no HIGH there.
        val diagram = JsonPath.read<String>(response, "$.diagram")
        assertThat(diagram).startsWith("flowchart TD").contains("POST /orders").contains("GET /orders")
        assertThat(diagram).contains("fill:#fca5a5") // HIGH-severity node styling present
        assertThat(diagram).contains("fill:#fde68a") // MEDIUM-severity node styling present
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
