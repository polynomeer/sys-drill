package com.sysdrill.backend.certification

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.evaluation.EvaluationRepository
import com.sysdrill.backend.identity.UserRepository
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
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val contentItemRepository: ContentItemRepository,
    private val sessionRepository: SessionRepository,
    private val submissionRepository: SubmissionRepository,
    private val evaluationRepository: EvaluationRepository,
    @Value("\${sysdrill.certification.passing-score}") private val passingScore: Int,
) {

    fun status(userId: UUID): CertificationStatusResponse {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found: $userId") }

        val officialScenarios = scenarioRepository.findByOrganizationIdIsNull().filter { it.creatorUserId == null }
        val officialScenarioIds = officialScenarios.mapNotNull { it.id }.toSet()
        val titleByScenarioId = contentItemRepository.findAllById(officialScenarios.map { it.contentId }).associateBy { it.id }
            .let { byContent -> officialScenarios.associate { it.id to (byContent[it.contentId]?.title ?: it.domain) } }

        val completedSessions = sessionRepository.findByUserIdOrderByStartedAtDesc(userId).filter { it.status == SessionStatus.COMPLETED }
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
        completedSessions.forEach { session ->
            val scenarioId = versionsById[session.scenarioVersionId]?.scenarioId ?: return@forEach
            if (scenarioId !in officialScenarioIds) return@forEach
            val domain = scenarioById[scenarioId]?.domain ?: return@forEach
            val avg = averageScore(session.id!!) ?: return@forEach
            bestScoreByDomain[domain] = maxOf(bestScoreByDomain[domain] ?: 0, avg)
        }

        val domainStatuses = officialScenarios.distinctBy { it.domain }.map { scenario ->
            val best = bestScoreByDomain[scenario.domain]
            DomainCertificationStatus(
                domain = scenario.domain,
                title = titleByScenarioId[scenario.id] ?: scenario.domain,
                passed = (best ?: 0) >= passingScore,
                bestScore = best,
            )
        }

        return CertificationStatusResponse(
            userId = userId,
            nickname = user.nickname,
            certified = domainStatuses.isNotEmpty() && domainStatuses.all { it.passed },
            domains = domainStatuses,
        )
    }
}
