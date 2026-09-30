package com.sysdrill.backend.identity

import com.jayway.jsonpath.JsonPath
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** docs/CODECRAFTERS_BENCHMARK.md §3.4 — optional onboarding answers, set at signup and editable later. */
@SpringBootTest
@AutoConfigureMockMvc
class UserPreferencesIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {

    private fun signup(extra: String): org.springframework.test.web.servlet.ResultActions {
        val email = "prefs-${UUID.randomUUID()}@example.com"
        return mockMvc.perform(
            post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                """{"email":"$email","password":"password123","nickname":"prefs","termsAccepted":true$extra}"""
            )
        )
    }

    @Test
    fun `signup stores the answers and the caller can read and replace them`() {
        val body = signup(""","preferredLanguage":"TYPESCRIPT","trainingGoal":"INTERVIEW"""")
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val userId = UUID.fromString(JsonPath.read(body, "$.user.id"))

        mockMvc.perform(get("/me/preferences").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.preferredLanguage").value("TYPESCRIPT"))
            .andExpect(jsonPath("$.trainingGoal").value("INTERVIEW"))

        mockMvc.perform(
            put("/me/preferences").header("Authorization", bearerHeader(userId))
                .contentType(MediaType.APPLICATION_JSON).content("""{"preferredLanguage":"PYTHON"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.preferredLanguage").value("PYTHON"))
            .andExpect(jsonPath("$.trainingGoal").value(org.hamcrest.Matchers.nullValue()))

        val user = userRepository.findById(userId).orElseThrow()
        assertThat(user.preferredLanguage).isEqualTo(PreferredLanguage.PYTHON)
        assertThat(user.trainingGoal).isNull()
    }

    @Test
    fun `answers are optional and unknown values are rejected`() {
        signup("").andExpect(status().isCreated)
        signup(""","trainingGoal":"GET_RICH"""").andExpect(status().isBadRequest)
    }

    @Test
    fun `preferences need a signed-in caller`() {
        mockMvc.perform(get("/me/preferences")).andExpect(status().isUnauthorized)
    }
}
