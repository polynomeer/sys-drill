package com.sysdrill.backend.learning

import com.sysdrill.backend.common.readIntMap
import com.sysdrill.backend.evaluation.EvaluationRepository
import com.sysdrill.backend.evaluation.EvaluationRiskFlagRepository
import com.sysdrill.backend.evaluation.RuleEvaluator
import com.sysdrill.backend.identity.SkillProfileRepository
import com.sysdrill.backend.identity.weakestCategory
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionService
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.submission.SubmissionRepository
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
    private val sessionRepository: SessionRepository,
    private val sessionService: SessionService,
    private val submissionRepository: SubmissionRepository,
    private val evaluationRepository: EvaluationRepository,
    private val riskFlagRepository: EvaluationRiskFlagRepository,
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
        val history = loadHistory(userId)

        val steps = targetKeys.mapNotNull { riskKey ->
            val concept = concepts[riskKey] ?: return@mapNotNull null
            val (status, evidence) = statusOf(concept, history)
            LearningPathStep(
                riskKey = riskKey,
                label = concept.label,
                summary = concept.summary,
                status = status,
                weaknessCount = weaknesses[riskKey] ?: 0,
                evidence = evidence,
                relatedDomains = concept.relatedDomains,
                relatedChallenges = concept.relatedChallenges,
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
     * 완료 세션을 도메인별로 최신순으로 모으고, 각 세션에서 지적받은 riskKey 집합을 붙인다.
     * 세션 단위 조회를 N번 하지 않도록 제출 → 평가 → 리스크 플래그를 한 번씩 일괄로 읽는다.
     */
    private fun loadHistory(userId: UUID): DomainHistory {
        val completed = sessionRepository.findByUserIdOrderByStartedAtDesc(userId)
            .filter { it.status == SessionStatus.COMPLETED }
        if (completed.isEmpty()) return DomainHistory(emptyMap())

        val sessionIds = completed.mapNotNull { it.id }
        val submissions = submissionRepository.findBySessionIdIn(sessionIds)
        val evaluations = evaluationRepository
            .findBySubmissionIdInAndIsActiveTrue(submissions.mapNotNull { it.id })
        val sessionIdBySubmissionId = submissions.mapNotNull { s -> s.id?.let { it to s.sessionId } }.toMap()
        val flags = riskFlagRepository.findByEvaluationIdIn(evaluations.mapNotNull { it.id })
        val sessionIdByEvaluationId = evaluations.mapNotNull { e ->
            e.id?.let { it to sessionIdBySubmissionId[e.submissionId] }
        }.mapNotNull { (evalId, sessionId) -> sessionId?.let { evalId to it } }.toMap()

        val flaggedKeysBySessionId = flags
            .mapNotNull { flag -> sessionIdByEvaluationId[flag.evaluationId]?.let { it to flag.riskKey } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, keys) -> keys.toSet() }

        // findByUserIdOrderByStartedAtDesc 가 최신순이므로 각 도메인의 첫 항목이 최신 완료 세션이다.
        val byDomain = mutableMapOf<String, MutableList<CompletedRun>>()
        completed.forEach { session ->
            val domain = runCatching { sessionService.getScenarioDomain(session) }.getOrNull() ?: return@forEach
            byDomain.getOrPut(domain) { mutableListOf() }
                .add(CompletedRun(session, flaggedKeysBySessionId[session.id] ?: emptySet()))
        }
        return DomainHistory(byDomain)
    }

    /**
     * 상태 판정 규칙(기획서 §5.3):
     * - 관련 도메인 완료 이력 없음 → NOT_STARTED
     * - 가장 최근 완료 세션에서 이 riskKey 를 또 지적받음 → IN_PROGRESS
     * - 지적이 없었음 → ADDRESSED
     *
     * 최근 1회만 보는 것은 의도다. 누적 카운트로 판정하면 한 번 지적받은 개념은
     * 영원히 "미해결"로 남아, 실제로 고친 사용자에게 경로가 갱신되지 않는다.
     */
    private fun statusOf(concept: LearningConcept, history: DomainHistory): Pair<LearningStepStatus, String> {
        val runs = concept.relatedDomains.flatMap { history.byDomain[it].orEmpty() }
        if (runs.isEmpty()) {
            val domains = concept.relatedDomains.joinToString(" · ").ifBlank { "관련 시나리오" }
            return LearningStepStatus.NOT_STARTED to "$domains 완료 이력이 없습니다."
        }
        val latest = runs.maxBy { it.session.startedAt }
        val domain = runCatching { sessionService.getScenarioDomain(latest.session) }.getOrNull() ?: "최근 세션"
        return if (concept.riskKey in latest.flaggedRiskKeys) {
            LearningStepStatus.IN_PROGRESS to "가장 최근 $domain 세션에서 다시 지적받았습니다."
        } else {
            LearningStepStatus.ADDRESSED to "가장 최근 $domain 세션에서는 지적되지 않았습니다."
        }
    }

    private fun emptyPath() = LearningPath(
        recommendedCategory = null,
        categoryLabel = null,
        rationale = "아직 평가 이력이 없습니다. 세션을 한 번 완료하면 약한 역량을 기준으로 학습 경로를 만들어 드립니다.",
        steps = emptyList(),
    )

    private data class CompletedRun(val session: Session, val flaggedRiskKeys: Set<String>)

    private class DomainHistory(val byDomain: Map<String, List<CompletedRun>>)
}
