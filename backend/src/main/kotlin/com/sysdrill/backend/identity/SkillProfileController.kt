package com.sysdrill.backend.identity

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.common.readIntList
import com.sysdrill.backend.common.readIntMap
import com.sysdrill.backend.evaluation.RuleEvaluator
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

data class SkillProfileResponse(
    val userId: UUID,
    val weaknessesByDomain: Map<String, Map<String, Int>>,
    /** Skill Graph slice 1 — the same [weaknesses] grouped by cross-domain competency category instead of scenario domain, via [RuleEvaluator.categoryByRiskKey]. */
    val weaknessesByCategory: Map<String, Map<String, Int>>,
    val trend: List<Int>,
    val trendDirection: TrendDirection,
    /** The user's single weakest competency category (summed weakness count across every riskKey in it), if any. */
    val recommendedCategory: String?,
    /** The scenario domain worth recommending next — the domain of the most frequent riskKey *within [recommendedCategory]* (not just the single most frequent riskKey overall, which could belong to a less-weak category). */
    val recommendedDomain: String?,
)

/**
 * docs/PRD.md §11.3's "약점 프로필" — GET /skill-profile (no
 * ARCHITECTURE.md precedent; the dashboard's "약점 TOP 3"/"점수 추이" panels
 * need this). PLAN.md step 13 added domain grouping, a long-term trend with a
 * computed recent direction, and a recommended-next-domain signal — all
 * derived at read time from the same flat `weaknesses`/`trend` storage
 * ([SkillProfileService] didn't need to change how it records data). PLAN.md
 * step 31 dropped the `{userId}` path segment (was `GET
 * /users/{userId}/skill-profile`) — the caller's identity now comes from
 * their token, not a URL they could substitute anyone else's id into.
 */
@RestController
class SkillProfileController(
    private val repository: SkillProfileRepository,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping("/skill-profile")
    fun get(@AuthenticatedUserId userId: UUID): SkillProfileResponse {
        val profile = repository.findByUserId(userId)
        val weaknesses = objectMapper.readIntMap(profile?.weaknesses)
        val trend = objectMapper.readIntList(profile?.trend)

        // Skill Graph slice 1 — two-level pick: the weakest *category* (summed
        // across every riskKey in it) first, then the most frequent riskKey
        // within just that category decides recommendedDomain. Replaces the old
        // single-riskKey-max rule, which could recommend a domain whose one
        // riskKey happened to be the single most frequent even though another
        // domain's several medium-frequency riskKeys added up to a bigger
        // underlying weakness.
        val categoryTotals = weaknesses.entries
            .groupBy { RuleEvaluator.categoryByRiskKey[it.key] ?: "OTHER" }
            .mapValues { (_, entries) -> entries.sumOf { it.value } }
        val recommendedCategory = categoryTotals.maxByOrNull { it.value }?.key
        val recommendedDomain = weaknesses.entries
            .filter { RuleEvaluator.categoryByRiskKey[it.key] == recommendedCategory }
            .maxByOrNull { it.value }?.key
            ?.let { RuleEvaluator.domainByRiskKey[it] }

        return SkillProfileResponse(
            userId = userId,
            weaknessesByDomain = weaknesses.entries
                .groupBy { RuleEvaluator.domainByRiskKey[it.key] ?: "unknown" }
                .mapValues { (_, entries) -> entries.associate { it.key to it.value } },
            weaknessesByCategory = weaknesses.entries
                .groupBy { RuleEvaluator.categoryByRiskKey[it.key] ?: "OTHER" }
                .mapValues { (_, entries) -> entries.associate { it.key to it.value } },
            trend = trend,
            trendDirection = trendDirection(trend),
            recommendedCategory = recommendedCategory,
            recommendedDomain = recommendedDomain,
        )
    }
}
