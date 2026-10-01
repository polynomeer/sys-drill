package com.sysdrill.backend.learning

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
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
 * PLAN.md Round E9 (L6) — the seeded Capacity Lab problems (V58). Truths are worked
 * out by hand from the seed: feed = 10M DAU × 2 posts / 86400 = 231.48 writes/s;
 * peak reads = 231.48 × 100 × 3 = 69,444/s; 10M × 2 × 2MB = 40 TB/day; ×365 = 14.6 PB/year.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CapacityLabIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private val user: UUID by lazy {
        userRepository.save(User(email = "lab-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "lab")).id!!
    }

    private fun check(slug: String, body: String): String = mockMvc.perform(
        post("/learning/labs/$slug/capacity/check")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(user))
            .content(body)
    ).andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `the three seeded problems are listed and formulas stay hidden until checked`() {
        val list = mockMvc.perform(get("/learning/labs").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(list, "$[?(@.kind == 'CAPACITY')].slug"))
            .containsExactly("capacity-feed", "capacity-shortener", "capacity-chat")

        val view = mockMvc.perform(get("/learning/labs/capacity-feed/capacity").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(view).doesNotContain("formula").doesNotContain("86400")
        assertThat(JsonPath.read<Int>(view, "$.variants[1].inputs[0].value")).isEqualTo(100_000_000)
    }

    @Test
    fun `answers are judged on order of magnitude against the computed truth`() {
        val json = check(
            "capacity-feed",
            """{"variant":0,"answers":{"writeRps":200,"peakReadRps":7000,"storagePerDayTB":40}}""",
        )
        val results = JsonPath.read<List<Map<String, Any>>>(json, "$.results[*].result")
        assertThat(results.map { it["direction"] }).containsExactly("ON_TARGET", "UNDER", "ON_TARGET", "MISSING")
        assertThat((results[0]["truth"] as Number).toDouble()).isCloseTo(231.481, Offset.offset(0.001))
        assertThat((results[1]["truth"] as Number).toDouble()).isCloseTo(69_444.4, Offset.offset(0.1))
        assertThat((results[3]["truth"] as Number).toDouble()).isCloseTo(14.6, Offset.offset(0.001))
        assertThat(JsonPath.read<String>(json, "$.results[0].formula")).isEqualTo("dau * postsPerUser / 86400")
    }

    @Test
    fun `a variant overrides its inputs before computing`() {
        val json = check("capacity-feed", """{"variant":1,"answers":{"writeRps":2300}}""")
        assertThat(JsonPath.read<Double>(json, "$.results[0].result.truth")).isCloseTo(2314.81, Offset.offset(0.01))
        assertThat(JsonPath.read<String>(json, "$.results[0].result.direction")).isEqualTo("ON_TARGET")
        mockMvc.perform(
            post("/learning/labs/capacity-feed/capacity/check").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user)).content("""{"variant":9}""")
        ).andExpect(status().isBadRequest)
    }
}
