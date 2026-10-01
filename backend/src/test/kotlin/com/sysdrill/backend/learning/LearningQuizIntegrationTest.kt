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
 * docs/CODECRAFTERS_BENCHMARK.md §3.6 — the concept quiz is assembled from
 * seeded data only: exactly one option is this concept's own pattern, the
 * others are other concepts' patterns, and each option names its source.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LearningQuizIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val conceptRepository: LearningConceptRepository,
) {

    private val userId: UUID by lazy {
        userRepository.save(User(email = "quiz-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "quiz")).id!!
    }

    @Test
    fun `every concept gets one correct option from its own patterns and distractors from others`() {
        val byKey = conceptRepository.findAll().associateBy { it.riskKey }
        for (concept in byKey.values) {
            val body = mockMvc.perform(get("/learning/concepts/${concept.riskKey}/quiz").header("Authorization", bearerHeader(userId)))
                .andExpect(status().isOk).andReturn().response.contentAsString

            val options = JsonPath.read<List<Map<String, Any>>>(body, "$.options")
            assertThat(options).describedAs(concept.riskKey).hasSize(3)
            val correct = options.filter { it["correct"] == true }
            assertThat(correct).describedAs(concept.riskKey).hasSize(1)
            assertThat(concept.patterns).contains(correct.single()["text"] as String)

            val wrong = options.filter { it["correct"] == false }
            assertThat(wrong.map { it["fromRiskKey"] }).describedAs("${concept.riskKey}: distractors from different concepts").doesNotHaveDuplicates()
            wrong.forEach { option ->
                val source = byKey.getValue(option["fromRiskKey"] as String)
                assertThat(source.riskKey).isNotEqualTo(concept.riskKey)
                assertThat(source.patterns).contains(option["text"] as String)
                assertThat(concept.patterns).doesNotContain(option["text"] as String)
            }
        }
    }

    @Test
    fun `unknown concept is a 404`() {
        mockMvc.perform(get("/learning/concepts/NOT_A_CONCEPT/quiz").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isNotFound)
    }
}
