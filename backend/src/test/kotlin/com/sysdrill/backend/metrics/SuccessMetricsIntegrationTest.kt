package com.sysdrill.backend.metrics

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.PlatformRole
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * PLAN.md Round B17 — anonymous allow-listed counters plus an admin-only
 * aggregate view. Counts are asserted relative to "before", since other runs
 * may share the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SuccessMetricsIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {

    private fun event(name: String) =
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content("""{"name":"$name"}"""))

    private fun user(role: PlatformRole): UUID = userRepository.save(
        User(email = "metrics-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "metrics", platformRole = role)
    ).id!!

    private fun metrics(adminId: UUID): String =
        mockMvc.perform(get("/admin/dashboard/metrics").header("Authorization", bearerHeader(adminId)))
            .andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `events are anonymous allow-listed counters that feed the admin funnel`() {
        val admin = user(PlatformRole.PLATFORM_ADMIN)
        val before = metrics(admin)
        val viewsBefore = JsonPath.read<Number>(before, "$.events30d.drill_overview_view").toLong()
        val startsBefore = JsonPath.read<Number>(before, "$.events30d.drill_overview_start").toLong()

        // No token needed; an unknown name is refused rather than stored.
        repeat(2) { event("drill_overview_view").andExpect(status().isNoContent) }
        event("drill_overview_start").andExpect(status().isNoContent)
        event("anything_else").andExpect(status().isBadRequest)

        val after = metrics(admin)
        val views = JsonPath.read<Number>(after, "$.events30d.drill_overview_view").toLong()
        val starts = JsonPath.read<Number>(after, "$.events30d.drill_overview_start").toLong()
        assertThat(views).isEqualTo(viewsBefore + 2)
        assertThat(starts).isEqualTo(startsBefore + 1)
        assertThat(JsonPath.read<Int>(after, "$.overviewToStartPercent")).isEqualTo((starts * 100 / views).toInt())
        assertThat(after).doesNotContain("anything_else")
        // Aggregates only — no per-user rows in the response.
        assertThat(after).doesNotContain("@example.com")
    }

    @Test
    fun `metrics are for platform admins only`() {
        mockMvc.perform(get("/admin/dashboard/metrics").header("Authorization", bearerHeader(user(PlatformRole.USER))))
            .andExpect(status().isForbidden)
    }
}
