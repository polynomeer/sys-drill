package com.sysdrill.backend.learning

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.identity.SkillProfileRepository
import com.sysdrill.backend.common.readIntMap
import java.util.UUID
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §5.2 — 개념 라이브러리.
 *
 * 개념 콘텐츠는 DB 에서(ADR-0039), 개인화는 기존 `skill_profiles.weaknesses`
 * 에서 읽는다. 둘을 조인하는 것이 이 서비스의 전부이고, **새로 저장하는 것은
 * 없다**(ADR-0011) — "내가 3번 놓친 개념" 배지는 매번 계산된다.
 */
@Service
class LearningService(
    private val conceptRepository: LearningConceptRepository,
    private val skillProfileRepository: SkillProfileRepository,
    private val objectMapper: ObjectMapper,
) {

    fun categories(userId: UUID): List<LearningCategory> {
        val weaknesses = weaknessCounts(userId)
        return conceptRepository.findAllByOrderByCategoryAscDisplayOrderAscLabelAsc()
            .groupBy { it.category }
            .map { (category, concepts) ->
                LearningCategory(
                    category = category,
                    label = categoryLabel(category),
                    concepts = concepts.map {
                        LearningConceptSummary(
                            riskKey = it.riskKey,
                            label = it.label,
                            summary = it.summary,
                            myWeaknessCount = weaknesses[it.riskKey] ?: 0,
                            relatedDomains = it.relatedDomains,
                            readingMinutes = it.readingMinutes(),
                        )
                    },
                    myWeaknessCount = concepts.sumOf { weaknesses[it.riskKey] ?: 0 },
                )
            }
            // 내 약점이 많은 카테고리를 먼저. 동률이면 이름순으로 고정해 순서가 흔들리지 않게 한다.
            .sortedWith(compareByDescending<LearningCategory> { it.myWeaknessCount }.thenBy { it.label })
    }

    fun detail(riskKey: String, userId: UUID): LearningConceptDetail {
        val concept = conceptRepository.findById(riskKey)
            .orElseThrow { NotFoundException("No learning concept for $riskKey") }
        return LearningConceptDetail(
            riskKey = concept.riskKey,
            category = concept.category,
            categoryLabel = categoryLabel(concept.category),
            label = concept.label,
            summary = concept.summary,
            whyItMatters = concept.whyItMatters,
            symptoms = concept.symptoms,
            patterns = concept.patterns,
            tradeoffs = concept.tradeoffs,
            relatedDomains = concept.relatedDomains,
            relatedActions = concept.relatedActions,
            readingMinutes = concept.readingMinutes(),
            relatedChallenges = concept.relatedChallenges,
            myWeaknessCount = weaknessCounts(userId)[riskKey] ?: 0,
        )
    }

    private fun weaknessCounts(userId: UUID): Map<String, Int> =
        objectMapper.readIntMap(skillProfileRepository.findByUserId(userId)?.weaknesses)

    companion object {
        /**
         * 프론트의 `skillCategoryLabels.ts` 와 같은 값. 카테고리 슬러그는
         * `RuleEvaluator.categoryByRiskKey` 가 정하고, 사람이 읽을 이름만 여기서 붙인다.
         */
        private val CATEGORY_LABELS = mapOf(
            "CONCURRENCY_CONSISTENCY" to "동시성·정합성",
            "RESILIENCE" to "트래픽 보호·복원력",
            "CACHING_DATA_ACCESS" to "캐싱·데이터 접근 전략",
            "ASYNC_BATCH" to "비동기·배치 처리",
            "CAPACITY_TIMING" to "용량·시간 제약 설계",
            "OBSERVABILITY" to "관측 가능성",
        )

        fun categoryLabel(category: String): String = CATEGORY_LABELS[category] ?: category
    }
}
