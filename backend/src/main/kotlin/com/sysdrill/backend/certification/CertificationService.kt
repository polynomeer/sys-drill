package com.sysdrill.backend.certification

import com.sysdrill.backend.organization.AssessmentSessions
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.evaluation.EvaluationRepository
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.learning.ConceptMasteryService
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.submission.SubmissionRepository
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Phase 5 — "SysDrill Certified Incident Responder" (docs/adr/0032). Live
 * derived judgement, never issued or persisted — recomputed on every read
 * from the same Session/Submission/Evaluation data
 * [com.sysdrill.backend.reporting.ReportService] already aggregates per
 * session, just rolled up across a user's whole history instead of one
 * session. Certification only counts official Flyway-seeded domains
 * (organizationId == null && creatorUserId == null) — marketplace and
 * org-custom scenarios don't count toward it.
 */
@Service
class CertificationService(
    private val userRepository: UserRepository,
    private val assessmentSessions: AssessmentSessions,
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val contentItemRepository: ContentItemRepository,
    private val sessionRepository: SessionRepository,
    private val submissionRepository: SubmissionRepository,
    private val evaluationRepository: EvaluationRepository,
    @Value("\${sysdrill.certification.passing-score}") private val passingScore: Int,
    private val missionService: com.sysdrill.backend.mission.MissionService,
    private val scenarioStepRepository: com.sysdrill.backend.scenario.ScenarioStepRepository,
    private val objectMapper: tools.jackson.databind.ObjectMapper,
) {

    fun status(userId: UUID): CertificationStatusResponse {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found: $userId") }

        val officialScenarios = scenarioRepository.findByOrganizationIdIsNull().filter { it.creatorUserId == null }
        val officialScenarioIds = officialScenarios.mapNotNull { it.id }.toSet()
        val titleByScenarioId = contentItemRepository.findAllById(officialScenarios.map { it.contentId }).associateBy { it.id }
            .let { byContent -> officialScenarios.associate { it.id to (byContent[it.contentId]?.title ?: it.domain) } }

        val allCompleted = sessionRepository.findByUserIdOrderByStartedAtDesc(userId).filter { it.status == SessionStatus.COMPLETED }
        // ADR-0043 — an assessment taken for an employer doesn't count toward the public certification.
        val assessmentIds = assessmentSessions.among(allCompleted.mapNotNull { it.id })
        val completedSessions = allCompleted.filterNot { it.id in assessmentIds }
        val versionsById = scenarioVersionRepository.findAllById(completedSessions.map { it.scenarioVersionId }.distinct()).associateBy { it.id }

        val submissions = submissionRepository.findBySessionIdIn(completedSessions.mapNotNull { it.id })
        val evaluations = evaluationRepository.findBySubmissionIdInAndIsActiveTrue(submissions.mapNotNull { it.id })
        val scoreBySubmissionId = evaluations.associate { it.submissionId to it.totalScore }
        val submissionsBySessionId = submissions.groupBy { it.sessionId }

        fun averageScore(sessionId: UUID): Int? {
            val scores = submissionsBySessionId[sessionId].orEmpty().mapNotNull { scoreBySubmissionId[it.id] }
            return if (scores.isEmpty()) null else scores.sum() / scores.size
        }

        val scenarioById = officialScenarios.associateBy { it.id }
        val bestScoreByDomain = mutableMapOf<String, Int>()
        data class ScoredRun(val domain: String, val variant: String, val score: Int)
        val runs = mutableListOf<ScoredRun>()
        completedSessions.forEach { session ->
            val scenarioId = versionsById[session.scenarioVersionId]?.scenarioId ?: return@forEach
            if (scenarioId !in officialScenarioIds) return@forEach
            val domain = scenarioById[scenarioId]?.domain ?: return@forEach
            val avg = averageScore(session.id!!) ?: return@forEach
            bestScoreByDomain[domain] = maxOf(bestScoreByDomain[domain] ?: 0, avg)
            runs += ScoredRun(domain, ConceptMasteryService.variantOf(domain, missionService.state(session).followupVariantKey), avg)
        }
        // M10 — the same distinct-variant count as concept mastery (L7), with "passed" = the passing score.
        val passedVariantsByDomain = runs.groupBy { it.domain }.mapValues { (_, domainRuns) ->
            ConceptMasteryService.distinctVariants(domainRuns, { it.variant }) { it.score >= passingScore }
        }

        val domainStatuses = officialScenarios.distinctBy { it.domain }.map { scenario ->
            val best = bestScoreByDomain[scenario.domain]
            DomainCertificationStatus(
                domain = scenario.domain,
                title = titleByScenarioId[scenario.id] ?: scenario.domain,
                passed = (best ?: 0) >= passingScore,
                bestScore = best,
                passedVariants = passedVariantsByDomain[scenario.domain] ?: 0,
                totalVariants = totalVariants(scenario.id!!),
            )
        }

        return CertificationStatusResponse(
            userId = userId,
            nickname = user.nickname,
            certified = domainStatuses.isNotEmpty() && domainStatuses.all { it.passed },
            domains = domainStatuses,
        )
    }

    /** Tail-design variants in the scenario's latest published version — `variants.size`, or 1 for a single prompt. */
    private fun totalVariants(scenarioId: UUID): Int {
        val version = scenarioVersionRepository.findFirstByScenarioIdAndStatusOrderByVersionNoDesc(scenarioId, "PUBLISHED") ?: return 1
        val followup = scenarioStepRepository.findByScenarioVersionIdOrderByStepOrder(version.id!!).firstOrNull { it.stepType == "FOLLOWUP" }
            ?: return 1
        val variants = followup.content?.let { runCatching { objectMapper.readTree(it).get("variants") }.getOrNull() }
        return if (variants != null && variants.isArray && variants.size() > 0) variants.size() else 1
    }
}
