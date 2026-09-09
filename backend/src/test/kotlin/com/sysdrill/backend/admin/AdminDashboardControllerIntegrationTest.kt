package com.sysdrill.backend.admin

import com.sysdrill.backend.identity.PlatformRole
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** docs/COMMERCIALIZATION.md — same PLATFORM_ADMIN gate as PromptTemplateController (PLAN.md step 35). */
@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private fun createPlatformAdmin(): UUID =
        userRepository.save(
            User(email = "dash-admin-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "dash-admin", platformRole = PlatformRole.PLATFORM_ADMIN)
        ).id!!

    private fun createRegularUser(): UUID =
        userRepository.save(User(email = "dash-user-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "dash-user")).id!!

    @Test
    fun `a platform admin sees dashboard stats, a regular user is forbidden`() {
        val adminId = createPlatformAdmin()
        val userId = createRegularUser()

        mockMvc.perform(get("/admin/dashboard/stats").header("Authorization", bearerHeader(adminId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalUsers").isNumber)
            .andExpect(jsonPath("$.newUsersToday").isNumber)
            .andExpect(jsonPath("$.totalOrganizations").isNumber)
            .andExpect(jsonPath("$.sessionsCompletedToday").isNumber)

        mockMvc.perform(get("/admin/dashboard/stats").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `an unauthenticated call is rejected`() {
        mockMvc.perform(get("/admin/dashboard/stats")).andExpect(status().isUnauthorized)
    }
}
