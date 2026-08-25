package com.sysdrill.backend.identity

import com.sysdrill.backend.common.readIntList
import com.sysdrill.backend.common.readIntMap
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

// PLAN.md step 13: "장기 누적 + 최근 추세" — a much longer rolling window than the
// original 10, so the trend is a real long-term history; SkillProfileController
// computes the short-term "최근 추세" direction by comparing recent slices of it.
private const val TREND_HISTORY_LIMIT = 200

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
