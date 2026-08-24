package com.sysdrill.backend.identity

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `creating a user returns its profile without needing an email or password`() {
        mockMvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname":"drill-user","experienceYears":3,"primaryStack":"Kotlin"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.nickname").value("drill-user"))
            .andExpect(jsonPath("$.experienceYears").value(3))
            .andExpect(jsonPath("$.primaryStack").value("Kotlin"))
            .andExpect(jsonPath("$.id").exists())
    }

    @Test
    fun `nickname is required`() {
        mockMvc.perform(
            post("/users").contentType(MediaType.APPLICATION_JSON).content("""{}""")
        ).andExpect(status().isBadRequest)
    }
}
