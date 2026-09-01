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
    val trend: List<Int>,
    val trendDirection: TrendDirection,
    /** The scenario domain worth recommending next — the domain of the user's single most frequent weakness, if any. */
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
}
