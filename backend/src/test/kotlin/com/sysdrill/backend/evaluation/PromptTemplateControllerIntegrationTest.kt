package com.sysdrill.backend.evaluation

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.PlatformRole
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

/** PLAN.md step 35 — /admin/prompt-templates now requires the PLATFORM_ADMIN role (see PlatformAccessGuard); previously entirely unauthenticated. */
@SpringBootTest
@AutoConfigureMockMvc
class PromptTemplateControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {

    private fun createPlatformAdmin(): UUID =
        userRepository.save(
            User(
                email = "prompt-admin-${UUID.randomUUID()}@example.com",
                passwordHash = "hash",
                nickname = "prompt-admin",
                platformRole = PlatformRole.PLATFORM_ADMIN,
            )
        ).id!!

    private fun createRegularUser(): UUID =
        userRepository.save(
            User(email = "prompt-user-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "prompt-user")
        ).id!!

    @Test
    fun `creating a second version and activating it deactivates the first`() {
        val adminId = createPlatformAdmin()
        val purpose = "test-purpose-${UUID.randomUUID()}"

        val v1Response = mockMvc.perform(
            post("/admin/prompt-templates").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(adminId))
                .content("""{"purpose":"$purpose","templateBody":"v1 body"}""")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.active").value(false))
            .andReturn().response.contentAsString
        val v1Id = JsonPath.read<String>(v1Response, "$.id")

        val v2Response = mockMvc.perform(
            post("/admin/prompt-templates").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(adminId))
                .content("""{"purpose":"$purpose","templateBody":"v2 body"}""")
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.version").value(2))
            .andReturn().response.contentAsString
        val v2Id = JsonPath.read<String>(v2Response, "$.id")

        mockMvc.perform(post("/admin/prompt-templates/$v1Id/activate").header("Authorization", bearerHeader(adminId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.active").value(true))

        mockMvc.perform(post("/admin/prompt-templates/$v2Id/activate").header("Authorization", bearerHeader(adminId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.active").value(true))

        mockMvc.perform(get("/admin/prompt-templates").param("purpose", purpose).header("Authorization", bearerHeader(adminId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == '$v1Id')].active").value(false))
            .andExpect(jsonPath("$[?(@.id == '$v2Id')].active").value(true))
    }

    @Test
    fun `calling without a token is rejected as unauthorized`() {
        mockMvc.perform(
            post("/admin/prompt-templates").contentType(MediaType.APPLICATION_JSON)
                .content("""{"purpose":"x","templateBody":"y"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a regular user's token is rejected as forbidden, not unauthorized`() {
        val userId = createRegularUser()
        mockMvc.perform(
            post("/admin/prompt-templates").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(userId))
                .content("""{"purpose":"x","templateBody":"y"}""")
        ).andExpect(status().isForbidden)
    }
}
