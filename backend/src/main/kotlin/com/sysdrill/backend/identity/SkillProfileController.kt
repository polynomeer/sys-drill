package com.sysdrill.backend.identity

import com.sysdrill.backend.common.readIntList
import com.sysdrill.backend.common.readIntMap
import com.sysdrill.backend.evaluation.RuleEvaluator
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

/** "최근 추세" direction, from comparing the last [RECENT_TREND_WINDOW] scores against the ones before them. */
enum class TrendDirection { IMPROVING, DECLINING, STABLE, INSUFFICIENT_DATA }

data class SkillProfileResponse(
    val userId: UUID,
    val weaknessesByDomain: Map<String, Map<String, Int>>,
    val trend: List<Int>,
    val trendDirection: TrendDirection,
    /** The scenario domain worth recommending next — the domain of the user's single most frequent weakness, if any. */
    val recommendedDomain: String?,
)

private const val RECENT_TREND_WINDOW = 3
private const val STABLE_THRESHOLD = 3.0

/**
 * docs/PRD.md §11.3's "약점 프로필" — GET /users/{id}/skill-profile (no
 * ARCHITECTURE.md precedent; the dashboard's "약점 TOP 3"/"점수 추이" panels
 * need this). PLAN.md step 13 added domain grouping, a long-term trend with a
 * computed recent direction, and a recommended-next-domain signal — all
 * derived at read time from the same flat `weaknesses`/`trend` storage
 * ([SkillProfileService] didn't need to change how it records data.
 */
@RestController
class SkillProfileController(
    private val repository: SkillProfileRepository,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping("/users/{userId}/skill-profile")
    fun get(@PathVariable userId: UUID): SkillProfileResponse {
        val profile = repository.findByUserId(userId)
        val weaknesses = objectMapper.readIntMap(profile?.weaknesses)
        val trend = objectMapper.readIntList(profile?.trend)

        return SkillProfileResponse(
            userId = userId,
            weaknessesByDomain = weaknesses.entries
                .groupBy { RuleEvaluator.domainByRiskKey[it.key] ?: "unknown" }
                .mapValues { (_, entries) -> entries.associate { it.key to it.value } },
            trend = trend,
            trendDirection = trendDirection(trend),
            recommendedDomain = weaknesses.maxByOrNull { it.value }?.key?.let { RuleEvaluator.domainByRiskKey[it] },
        )
    }

    private fun trendDirection(trend: List<Int>): TrendDirection {
        if (trend.size < RECENT_TREND_WINDOW * 2) return TrendDirection.INSUFFICIENT_DATA
        val recent = trend.takeLast(RECENT_TREND_WINDOW).average()
        val prior = trend.dropLast(RECENT_TREND_WINDOW).takeLast(RECENT_TREND_WINDOW).average()
        val delta = recent - prior
        return when {
            delta > STABLE_THRESHOLD -> TrendDirection.IMPROVING
            delta < -STABLE_THRESHOLD -> TrendDirection.DECLINING
            else -> TrendDirection.STABLE
        }
    }
}
