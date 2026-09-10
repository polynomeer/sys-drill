package com.sysdrill.backend.identity

import org.assertj.core.api.Assertions.assertThat
import com.sysdrill.backend.support.bearerHeader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * PLAN.md step 13: weaknesses grouped by scenario domain, a long-term score
 * trend with a computed recent direction, and a recommended-next-domain
 * signal — all derived at read time in SkillProfileController from the same
 * flat storage SkillProfileService writes ([SkillProfileService] itself is
 * unit-tested implicitly by driving it directly here rather than through the
 * full evaluation pipeline, which ReportAndSkillProfileIntegrationTest already covers).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SkillProfileControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val skillProfileService: SkillProfileService,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "skillprofile-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    @Test
    fun `weaknesses from different scenario domains are grouped separately`() {
        skillProfileService.recordEvaluation(userId, listOf("MISSING_RATE_LIMIT"), totalScore = 60)
        skillProfileService.recordEvaluation(userId, listOf("MISSING_DLQ", "MISSING_DLQ"), totalScore = 55)
        skillProfileService.recordEvaluation(userId, listOf("MISSING_READ_REPLICA"), totalScore = 65)

        mockMvc.perform(get("/skill-profile").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.weaknessesByDomain.coupon.MISSING_RATE_LIMIT").value(1))
            .andExpect(jsonPath("$.weaknessesByDomain.notification.MISSING_DLQ").value(2))
            .andExpect(jsonPath("$.weaknessesByDomain['product-browsing'].MISSING_READ_REPLICA").value(1))
            .andExpect(jsonPath("$.recommendedDomain").value("notification"))
    }

    /** Skill Graph slice 1 — the same flat weaknesses grouped by cross-domain competency category (`RuleEvaluator.categoryByRiskKey`), not scenario domain. */
    @Test
    fun `weaknesses from different scenario domains are grouped by cross-domain category`() {
        skillProfileService.recordEvaluation(userId, listOf("MISSING_CONCURRENCY_CONTROL"), totalScore = 60) // coupon -> CONCURRENCY_CONSISTENCY
        skillProfileService.recordEvaluation(userId, listOf("MISSING_RESERVATION_LOCKING"), totalScore = 55) // reservation -> CONCURRENCY_CONSISTENCY
        skillProfileService.recordEvaluation(userId, listOf("MISSING_READ_REPLICA"), totalScore = 65) // product-browsing -> CACHING_DATA_ACCESS

        mockMvc.perform(get("/skill-profile").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.weaknessesByCategory.CONCURRENCY_CONSISTENCY.MISSING_CONCURRENCY_CONTROL").value(1))
            .andExpect(jsonPath("$.weaknessesByCategory.CONCURRENCY_CONSISTENCY.MISSING_RESERVATION_LOCKING").value(1))
            .andExpect(jsonPath("$.weaknessesByCategory.CACHING_DATA_ACCESS.MISSING_READ_REPLICA").value(1))
    }

    /**
     * Skill Graph slice 1 — the case the old single-riskKey-max rule got
     * wrong: MISSING_RATE_LIMIT and MISSING_READ_REPLICA tie on raw count (3
     * each), but MISSING_RATE_LIMIT's category (RESILIENCE) also contains
     * MISSING_DLQ(1), giving RESILIENCE a higher *category* total (4) than
     * CACHING_DATA_ACCESS's (3) — so the recommendation should follow the
     * category with more underlying weakness, not just whichever single
     * riskKey happens to tie for the top individual count.
     */
    @Test
    fun `recommendedDomain follows the category with the highest summed weakness, not just the single most frequent riskKey`() {
        skillProfileService.recordEvaluation(userId, listOf("MISSING_RATE_LIMIT", "MISSING_RATE_LIMIT", "MISSING_RATE_LIMIT"), totalScore = 50)
        skillProfileService.recordEvaluation(userId, listOf("MISSING_DLQ"), totalScore = 50)
        skillProfileService.recordEvaluation(userId, listOf("MISSING_READ_REPLICA", "MISSING_READ_REPLICA", "MISSING_READ_REPLICA"), totalScore = 50)

        mockMvc.perform(get("/skill-profile").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.recommendedCategory").value("RESILIENCE"))
            .andExpect(jsonPath("$.recommendedDomain").value("coupon"))
    }

    @Test
    fun `trend direction is IMPROVING when recent scores are clearly higher`() {
        listOf(40, 42, 41, 70, 75, 72).forEach { score ->
            skillProfileService.recordEvaluation(userId, emptyList(), totalScore = score)
        }

        mockMvc.perform(get("/skill-profile").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.trend.length()").value(6))
            .andExpect(jsonPath("$.trendDirection").value("IMPROVING"))
    }

    @Test
    fun `trend direction is DECLINING when recent scores are clearly lower`() {
        listOf(80, 82, 81, 40, 42, 41).forEach { score ->
            skillProfileService.recordEvaluation(userId, emptyList(), totalScore = score)
        }

        mockMvc.perform(get("/skill-profile").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.trendDirection").value("DECLINING"))
    }

    @Test
    fun `trend direction is STABLE when recent scores are close to prior scores`() {
        listOf(70, 71, 69, 70, 72, 68).forEach { score ->
            skillProfileService.recordEvaluation(userId, emptyList(), totalScore = score)
        }

        mockMvc.perform(get("/skill-profile").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.trendDirection").value("STABLE"))
    }

    @Test
    fun `trend direction is INSUFFICIENT_DATA with too few scores`() {
        listOf(70, 71, 69, 70).forEach { score ->
            skillProfileService.recordEvaluation(userId, emptyList(), totalScore = score)
        }

        mockMvc.perform(get("/skill-profile").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.trendDirection").value("INSUFFICIENT_DATA"))
    }

    @Test
    fun `long-term trend keeps far more than the old 10-entry window`() {
        repeat(150) { i ->
            skillProfileService.recordEvaluation(userId, emptyList(), totalScore = 50 + (i % 10))
        }

        val response = mockMvc.perform(get("/skill-profile").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val trendLength = com.jayway.jsonpath.JsonPath.read<List<Int>>(response, "$.trend").size
        assertThat(trendLength).isGreaterThan(100)
    }
}
