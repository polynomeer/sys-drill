package com.sysdrill.backend.identity

import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** PLAN.md step 30 — POST /users (nickname-only guest creation) is gone; see AuthControllerIntegrationTest for signup/login. GET /{id} stays and is covered here. */
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {

    @Test
    fun `fetching a user returns its profile`() {
        val user = userRepository.save(
            User(
                email = "profile-${UUID.randomUUID()}@example.com",
                passwordHash = "hash",
                nickname = "drill-user",
                experienceYears = 3,
                primaryStack = "Kotlin",
            )
        )

        mockMvc.perform(get("/users/${user.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nickname").value("drill-user"))
            .andExpect(jsonPath("$.experienceYears").value(3))
            .andExpect(jsonPath("$.primaryStack").value("Kotlin"))
    }

    @Test
    fun `fetching an unknown user is a 404`() {
        mockMvc.perform(get("/users/${UUID.randomUUID()}"))
            .andExpect(status().isNotFound)
    }
}
