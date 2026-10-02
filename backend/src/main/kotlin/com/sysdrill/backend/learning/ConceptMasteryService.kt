package com.sysdrill.backend.learning

import com.sysdrill.backend.evaluation.EvaluationRepository
import com.sysdrill.backend.evaluation.EvaluationRiskFlagRepository
import com.sysdrill.backend.mission.MissionService
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionService
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.submission.SubmissionRepository
import org.springframework.stereotype.Service
import java.util.UUID

/** docs/LEARNING_EXPANSION_PLAN.md L7 — four levels, derived at read time (ADR-0011). */
enum class MasteryLevel {
    /** No completed session in a related domain. */
    NOT_STARTED,

    /** Flagged again in the most recent related session. */
    WEAK,

    /** Clean in the most recent related session, but on one variant only. */
    PRACTICED,

    /** Clean on two or more different variants — "한 번 맞혔다고 숙련으로 치지 않는다". */
    CONFIDENT,
}

data class ConceptMastery(
    val level: MasteryLevel,
    /** Different variants (domain + FOLLOWUP variant) where this concept wasn't flagged. */
    val cleanVariants: Int,
    val latestSessionId: UUID?,
    val latestDomain: String?,
)

/**
 * One completed session as the mastery rules see it. [variant] is `domain:followupVariantKey`
 * — the key pinned at advance into FOLLOWUP (Round E10). Sessions from before the pin (or
 * single-variant scenarios) count as one `domain:base` variant: conservative, never inflating.
 */
data class CompletedRun(
    val session: Session,
    val domain: String,
    val variant: String,
    val flaggedRiskKeys: Set<String>,
)

/**
 * PLAN.md Round E18 — the one place mastery is decided. The learning path's three states,
 * the concept list/map badges and (Round E21, DRILLS_EXPANSION_PLAN M10) the per-domain
 * variant confidence all read [history] and the counting here, so two screens can never
 * disagree about the same concept.
 *
 * "Recent" is the single latest related session, on purpose — the path's original rule
 * (§5.3): with a longer window, a learner who actually fixed something stays "weak" for
 * several more runs. The guard against one lucky run is the variant count, not the window.
 */
@Service
class ConceptMasteryService(
    private val sessionRepository: SessionRepository,
    private val sessionService: SessionService,
    private val missionService: MissionService,
    private val submissionRepository: SubmissionRepository,
    private val evaluationRepository: EvaluationRepository,
    private val riskFlagRepository: EvaluationRiskFlagRepository,
) {
    /** Completed sessions, newest first, with what each was flagged for. Batched — no per-session queries. */
    fun history(userId: UUID): List<CompletedRun> {
        val completed = sessionRepository.findByUserIdOrderByStartedAtDesc(userId).filter { it.status == SessionStatus.COMPLETED }
        if (completed.isEmpty()) return emptyList()

        val submissions = submissionRepository.findBySessionIdIn(completed.mapNotNull { it.id })
        val sessionBySubmission = submissions.mapNotNull { s -> s.id?.let { it to s.sessionId } }.toMap()
        val evaluations = evaluationRepository.findBySubmissionIdInAndIsActiveTrue(submissions.mapNotNull { it.id })
        val sessionByEvaluation = evaluations.mapNotNull { e -> e.id?.let { id -> sessionBySubmission[e.submissionId]?.let { id to it } } }.toMap()
        val flagged = riskFlagRepository.findByEvaluationIdIn(evaluations.mapNotNull { it.id })
            .mapNotNull { flag -> sessionByEvaluation[flag.evaluationId]?.let { it to flag.riskKey } }
            .groupBy({ it.first }, { it.second })

        return completed.mapNotNull { session ->
            val domain = runCatching { sessionService.getScenarioDomain(session) }.getOrNull() ?: return@mapNotNull null
            val variantKey = runCatching { missionService.state(session).followupVariantKey }.getOrNull()
            CompletedRun(session, domain, "$domain:${variantKey ?: "base"}", flagged[session.id].orEmpty().toSet())
        }
    }

    fun masteryOf(concept: LearningConcept, history: List<CompletedRun>): ConceptMastery {
        val runs = history.filter { it.domain in concept.relatedDomains }
        val latest = runs.maxByOrNull { it.session.startedAt }
            ?: return ConceptMastery(MasteryLevel.NOT_STARTED, 0, null, null)
        val clean = cleanVariants(runs, concept.riskKey)
        val level = when {
            concept.riskKey in latest.flaggedRiskKeys -> MasteryLevel.WEAK
            clean >= CONFIDENT_VARIANTS -> MasteryLevel.CONFIDENT
            else -> MasteryLevel.PRACTICED
        }
        return ConceptMastery(level, clean, latest.session.id, latest.domain)
    }

    fun masteryByRiskKey(userId: UUID, concepts: List<LearningConcept>): Map<String, ConceptMastery> {
        val history = history(userId)
        return concepts.associate { it.riskKey to masteryOf(it, history) }
    }

    companion object {
        const val CONFIDENT_VARIANTS = 2

        /** Shared with M10 (Round E21): different variants among [runs] where [riskKey] wasn't flagged. */
        fun cleanVariants(runs: List<CompletedRun>, riskKey: String): Int =
            runs.filter { riskKey !in it.flaggedRiskKeys }.map { it.variant }.distinct().size
    }
}
