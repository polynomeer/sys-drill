package com.sysdrill.backend.identity

import com.sysdrill.backend.common.readIntList
import com.sysdrill.backend.common.readIntMap
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

// PLAN.md step 13: "장기 누적 + 최근 추세" — a much longer rolling window than the
// original 10, so the trend is a real long-term history; SkillProfileService
// computes the short-term "최근 추세" direction by comparing recent slices of it.
private const val TREND_HISTORY_LIMIT = 200

/** "최근 추세" direction, from comparing the last [RECENT_TREND_WINDOW] scores against the ones before them. */
enum class TrendDirection { IMPROVING, DECLINING, STABLE, INSUFFICIENT_DATA }

private const val RECENT_TREND_WINDOW = 3
private const val STABLE_THRESHOLD = 3.0

/**
 * PLAN.md step 33 — extracted from SkillProfileController so
 * [com.sysdrill.backend.organization.OrganizationService]'s team dashboard can
 * derive the same trend direction for each member without duplicating the
 * window/threshold logic.
 */
fun trendDirection(trend: List<Int>): TrendDirection {
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

/**
 * Tags "반복되는 사고 패턴" (docs/PRD.md §11.3) by counting how often each
 * RuleEvaluator finding recurs for a user, plus a rolling score trend. Called
 * once per evaluation (EvaluationWorker), not per session, so it accumulates
 * across every step of every scenario the user attempts.
 */
@Service
class SkillProfileService(
    private val repository: SkillProfileRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun recordEvaluation(userId: UUID, ruleRiskKeys: List<String>, totalScore: Int?) {
        val profile = repository.findByUserId(userId) ?: SkillProfile(userId = userId)

        val weaknessCounts = objectMapper.readIntMap(profile.weaknesses).toMutableMap()
        ruleRiskKeys.forEach { key -> weaknessCounts[key] = (weaknessCounts[key] ?: 0) + 1 }
        profile.weaknesses = objectMapper.writeValueAsString(weaknessCounts)

        if (totalScore != null) {
            val trend = objectMapper.readIntList(profile.trend) + totalScore
            profile.trend = objectMapper.writeValueAsString(trend.takeLast(TREND_HISTORY_LIMIT))
        }

        // saveAndFlush, not save: this is called from EvaluationWorker before its
        // compareAndSetStatus bulk update, which clears the persistence context
        // without flushing first (see EvaluationWorker's comments on the same pitfall).
        repository.saveAndFlush(profile)
    }
}
