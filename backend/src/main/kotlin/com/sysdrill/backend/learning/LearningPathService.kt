package com.sysdrill.backend.learning

import com.sysdrill.backend.common.readIntMap
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.evaluation.RuleEvaluator
import com.sysdrill.backend.identity.SkillProfileRepository
import com.sysdrill.backend.identity.weakestCategory
import com.sysdrill.backend.scenario.ScenarioRepository
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §5.3 — 개인 학습 경로.
 *
 * 조직 커리큘럼([ADR-0030])의 개인판이고, 그 ADR 이 정한 세 성질을 그대로 따른다:
 * **advisory**(강제하지 않음), **전체 교체**(저장하지 않으므로 매번 다시 만들어진다),
 * **소급 완료 인정**(과거 세션에서 이미 해결했으면 완료로 친다).
 *
 * 새로 저장하는 것이 없다(ADR-0011) — 경로도 단계 상태도 전부 기존
 * `skill_profiles` + 세션/평가 이력에서 읽기 시점에 파생한다.
 *
 * 시작점은 [weakestCategory] 로 고른다. 프로필 화면의 "가장 약한 영역"과 **같은
 * 함수**를 쓰는 것이 중요하다 — 두 화면이 다른 역량을 가리키면 둘 다 신뢰를 잃는다.
 */
@Service
class LearningPathService(
    private val conceptRepository: LearningConceptRepository,
    private val skillProfileRepository: SkillProfileRepository,
    private val conceptMasteryService: ConceptMasteryService,
    private val scenarioRepository: ScenarioRepository,
    private val contentItemRepository: ContentItemRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${sysdrill.learning.path.max-steps:3}") private val maxSteps: Int,
) {

    fun forUser(userId: UUID): LearningPath {
        val weaknesses = objectMapper.readIntMap(skillProfileRepository.findByUserId(userId)?.weaknesses)
        val category = weakestCategory(weaknesses)
            ?: return emptyPath()

        // 이 카테고리에서 가장 자주 지적받은 개념부터. 동률이면 키 순서로 고정해
        // 새로고침마다 순서가 바뀌지 않게 한다.
        val targetKeys = weaknesses.entries
            .filter { RuleEvaluator.categoryByRiskKey[it.key] == category }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(maxSteps)
            .map { it.key }

        val concepts = conceptRepository.findAllById(targetKeys).associateBy { it.riskKey }
        val history = conceptMasteryService.history(userId)
        val titles = domainTitles()

        val steps = targetKeys.mapNotNull { riskKey ->
            val concept = concepts[riskKey] ?: return@mapNotNull null
            val mastery = conceptMasteryService.masteryOf(concept, history)
            val (status, evidence) = statusOf(concept, mastery, titles)
            LearningPathStep(
                riskKey = riskKey,
                label = concept.label,
                summary = concept.summary,
                status = status,
                weaknessCount = weaknesses[riskKey] ?: 0,
                evidence = evidence,
                relatedDomains = concept.relatedDomains,
                relatedChallenges = concept.relatedChallenges,
                mastery = mastery.level,
            )
        }

        val total = weaknesses.entries
            .filter { RuleEvaluator.categoryByRiskKey[it.key] == category }
            .sumOf { it.value }

        return LearningPath(
            recommendedCategory = category,
            categoryLabel = LearningService.categoryLabel(category),
            rationale = "평가에서 이 역량의 지적이 ${total}회로 가장 많습니다.",
            steps = steps,
        )
    }

    /**
     * 상태 판정 규칙(기획서 §5.3) — PLAN.md Round E18부터 [ConceptMasteryService] 의 4단계에서 파생한다:
     * - 미시작 → NOT_STARTED
     * - 약점(가장 최근 관련 세션에서 또 지적) → IN_PROGRESS
     * - 연습함·신뢰(가장 최근 관련 세션에서 미지적) → ADDRESSED
     *
     * 최근 1회만 보는 것은 의도다. 누적 카운트로 판정하면 한 번 지적받은 개념은
     * 영원히 "미해결"로 남아, 실제로 고친 사용자에게 경로가 갱신되지 않는다.
     */
    private fun statusOf(
        concept: LearningConcept,
        mastery: ConceptMastery,
        titles: Map<String, String>,
    ): Pair<LearningStepStatus, String> {
        fun title(domain: String) = titles[domain] ?: domain

        if (mastery.level == MasteryLevel.NOT_STARTED) {
            val domains = concept.relatedDomains.joinToString(" · ") { title(it) }.ifBlank { "관련 시나리오" }
            return LearningStepStatus.NOT_STARTED to "$domains 완료 이력이 없습니다."
        }
        val where = mastery.latestDomain?.let { "가장 최근 ${title(it)} 세션" } ?: "가장 최근 완료 세션"
        return if (mastery.level == MasteryLevel.WEAK) {
            LearningStepStatus.IN_PROGRESS to "$where 에서 다시 지적받았습니다."
        } else {
            LearningStepStatus.ADDRESSED to "$where 에서는 지적되지 않았습니다."
        }
    }

    /**
     * 도메인 슬러그를 사람이 읽는 시나리오 제목으로. 근거 문구에 `coupon` 같은
     * 슬러그가 그대로 나가면 바로 아래 "관련 시나리오: 선착순 쿠폰" 과 표기가
     * 어긋나 같은 것을 가리키는지 알 수 없게 된다.
     */
    private fun domainTitles(): Map<String, String> {
        val official = scenarioRepository.findByOrganizationIdIsNull().filter { it.creatorUserId == null }
        val titleByContentId = contentItemRepository.findAllById(official.map { it.contentId })
            .associate { it.id to it.title }
        return official.mapNotNull { scenario ->
            titleByContentId[scenario.contentId]?.let { scenario.domain to it }
        }.toMap()
    }

    private fun emptyPath() = LearningPath(
        recommendedCategory = null,
        categoryLabel = null,
        rationale = "아직 평가 이력이 없습니다. 세션을 한 번 완료하면 약한 역량을 기준으로 학습 경로를 만들어 드립니다.",
        steps = emptyList(),
    )

}
