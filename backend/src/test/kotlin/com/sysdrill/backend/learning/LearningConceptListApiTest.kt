package com.sysdrill.backend.learning

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.7 — the domain track page groups concepts
 * by domain from the list endpoint alone, so the summary must carry the same
 * relatedDomains as the detail.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LearningConceptListApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val conceptRepository: LearningConceptRepository,
) {

    @Test
    fun `concept summaries carry their related domains`() {
        val userId = userRepository.save(
            User(email = "concepts-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "concepts")
        ).id!!

        val response = mockMvc.perform(get("/learning/concepts").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk).andReturn().response.contentAsString

        val expected = conceptRepository.findAll().first { it.relatedDomains.isNotEmpty() }
        val actual = JsonPath.read<List<List<String>>>(response, "$[*].concepts[?(@.riskKey == '${expected.riskKey}')].relatedDomains")
        assertThat(actual.single()).containsExactlyElementsOf(expected.relatedDomains)

        // docs/CODECRAFTERS_BENCHMARK.md §3.6 — reading time comes from the server, same figure as the detail page.
        val minutes = JsonPath.read<List<Int>>(response, "$[*].concepts[?(@.riskKey == '${expected.riskKey}')].readingMinutes")
        assertThat(minutes.single()).isEqualTo(expected.readingMinutes()).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `the knowledge map has every concept with mastery, and a concept lists its neighbours`() {
        val userId = userRepository.save(
            User(email = "map-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "map")
        ).id!!

        val map = mockMvc.perform(get("/learning/map").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(map, "$.nodes[*].riskKey")).hasSize(conceptRepository.count().toInt())
        assertThat(JsonPath.read<List<String>>(map, "$.nodes[*].mastery").toSet()).containsExactly("NOT_STARTED")
        assertThat(JsonPath.read<List<String>>(map, "$.edges[?(@.source == 'MISSING_IDEMPOTENCY')].target")).contains("MISSING_PAYMENT_IDEMPOTENCY")

        val detail = mockMvc.perform(get("/learning/concepts/MISSING_PAYMENT_IDEMPOTENCY").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(detail, "$.relatedConcepts[?(@.riskKey == 'MISSING_IDEMPOTENCY')].relation")).containsExactly("PREREQUISITE")
        assertThat(JsonPath.read<String>(detail, "$.mastery")).isEqualTo("NOT_STARTED")
    }
}
