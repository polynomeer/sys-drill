package com.sysdrill.backend.session

import com.sysdrill.backend.build.BuildSubmissionRepository
import com.sysdrill.backend.build.BuildSubmissionStatus
import com.sysdrill.backend.common.events.EvaluationRequested
import com.sysdrill.backend.common.readIntMap
import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.identity.SkillProfileRepository
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioStepRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.reporting.ReportService
import com.sysdrill.backend.scenario.ScenarioStep
import com.sysdrill.backend.submission.Submission
import com.sysdrill.backend.submission.SubmissionRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Service
class SessionService(
    private val sessionRepository: SessionRepository,
    private val sessionPhaseRepository: SessionPhaseRepository,
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioStepRepository: ScenarioStepRepository,
    private val submissionRepository: SubmissionRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val reportService: ReportService,
    private val buildSubmissionRepository: BuildSubmissionRepository,
    private val skillProfileRepository: SkillProfileRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${sysdrill.session.interview-timer.initial-seconds}") private val initialTimerSeconds: Long,
    @Value("\${sysdrill.session.interview-timer.followup-seconds}") private val followupTimerSeconds: Long,
    @Value("\${sysdrill.session.interview-timer.incident-seconds}") private val incidentTimerSeconds: Long,
) {

    @Transactional
    fun startSession(
        userId: UUID,
        scenarioId: UUID,
        buildSubmissionId: UUID? = null,
        seed: String? = null,
        interviewMode: Boolean = false,
    ): Session {
        val scenario = scenarioRepository.findById(scenarioId)
            .orElseThrow { NotFoundException("Scenario not found: $scenarioId") }
        val version = scenarioVersionRepository
            .findFirstByScenarioIdAndStatusOrderByVersionNoDesc(scenario.id!!, "PUBLISHED")
            ?: throw NotFoundException("No published version for scenario: $scenarioId")
        val firstStep = scenarioStepRepository.findByScenarioVersionIdAndStepOrder(version.id!!, 1)
            ?: throw IllegalStateException("Scenario version has no steps: ${version.id}")

        if (buildSubmissionId != null) {
            val buildSubmission = buildSubmissionRepository.findById(buildSubmissionId)
                .orElseThrow { NotFoundException("Build submission not found: $buildSubmissionId") }
            if (buildSubmission.userId != userId) {
                throw ConflictException("Build submission $buildSubmissionId does not belong to user $userId")
            }
            if (buildSubmission.status != BuildSubmissionStatus.COMPLETED) {
                throw ConflictException("Build submission $buildSubmissionId has not completed yet")
            }
        }

        val session = sessionRepository.save(
            Session(
                userId = userId,
                scenarioVersionId = version.id!!,
                buildSubmissionId = buildSubmissionId,
                interviewMode = interviewMode,
                currentPhase = firstStep.stepType,
                // Drives deterministic variant selection for steps with multiple
                // authored versions (PLAN.md step 12) — PRD.md §7.3's "통제된
                // 랜덤성": same seed always picks the same variant. Callers may
                // supply their own (tests; future "replay this session" use
                // cases); otherwise one is generated.
                seed = seed ?: UUID.randomUUID().toString(),
            )
        )
        sessionPhaseRepository.save(
            SessionPhase(
                sessionId = session.id!!,
                phaseType = firstStep.stepType,
                phaseOrder = firstStep.stepOrder,
                status = "IN_PROGRESS",
                startedAt = Instant.now(),
            )
        )
        return session
    }

    fun getSession(sessionId: UUID): Session =
        sessionRepository.findById(sessionId).orElseThrow { NotFoundException("Session not found: $sessionId") }

    private fun phaseTimeLimitSeconds(phaseType: String): Long = when (phaseType) {
        "INITIAL" -> initialTimerSeconds
        "FOLLOWUP" -> followupTimerSeconds
        "INCIDENT" -> incidentTimerSeconds
        else -> incidentTimerSeconds
    }

    /** Null unless [Session.interviewMode] — the frontend's countdown is driven entirely by this one field, not by re-deriving the time limit itself. */
    fun getPhaseDeadline(session: Session): Instant? {
        if (!session.interviewMode) return null
        val phase = sessionPhaseRepository.findTopBySessionIdOrderByPhaseOrderDesc(session.id!!) ?: return null
        val startedAt = phase.startedAt ?: return null
        return startedAt.plusSeconds(phaseTimeLimitSeconds(phase.phaseType))
    }

    /** The scenario domain (e.g. "coupon") a session belongs to — drives which Wargame actions/guidance the frontend shows. */
    fun getScenarioDomain(session: Session): String {
        val version = scenarioVersionRepository.findById(session.scenarioVersionId)
            .orElseThrow { NotFoundException("Scenario version not found: ${session.scenarioVersionId}") }
        return scenarioRepository.findById(version.scenarioId)
            .orElseThrow { NotFoundException("Scenario not found: ${version.scenarioId}") }
            .domain
    }

    /** The "prompt" text of whichever ScenarioStep the session is currently on — what the frontend shows as the brief. */
    fun getCurrentStepPrompt(session: Session): String? {
        val phase = sessionPhaseRepository.findTopBySessionIdOrderByPhaseOrderDesc(session.id!!) ?: return null
        val step = scenarioStepRepository.findByScenarioVersionIdAndStepOrder(session.scenarioVersionId, phase.phaseOrder)
        return step?.let { extractPrompt(it, session) }
    }

    /**
     * A step's `content` is either a single `{"prompt": ...}` (INITIAL/INCIDENT,
     * and FOLLOWUP steps with only one authored version) or, for a FOLLOWUP step
     * with multiple authored tail-design twists, `{"variants": [{"key",
     * "targetRiskKey", "prompt"}, ...]}` — see [selectVariant] for how one is chosen.
     */
    private fun extractPrompt(step: ScenarioStep, session: Session): String? {
        val content = step.content ?: return null
        @Suppress("UNCHECKED_CAST")
        val map = objectMapper.readValue(content, Map::class.java) as Map<String, Any?>
        map["prompt"]?.let { return it as? String }

        @Suppress("UNCHECKED_CAST")
        val variants = map["variants"] as? List<Map<String, Any?>> ?: return null
        return selectVariant(variants, session)["prompt"] as? String
    }

    /**
     * Adaptive selection (PLAN.md step 12): prefer the variant whose
     * `targetRiskKey` matches the user's most frequent recorded weakness in
     * this scenario, so a recurring blind spot becomes the next tail-design's
     * twist. Falls back to a seed-derived deterministic pick — reproducible
     * per session, varied across sessions/users, per PRD.md §7.3's "통제된
     * 랜덤성" (controlled randomness, not arbitrary).
     */
    private fun selectVariant(variants: List<Map<String, Any?>>, session: Session): Map<String, Any?> {
        val weaknesses = objectMapper.readIntMap(skillProfileRepository.findByUserId(session.userId)?.weaknesses)
        val byWeakness = variants
            .filter { (it["targetRiskKey"] as? String) != null }
            .filter { (weaknesses[it["targetRiskKey"] as String] ?: 0) > 0 }
            .maxByOrNull { weaknesses[it["targetRiskKey"] as String] ?: 0 }
        if (byWeakness != null) return byWeakness

        val index = Math.floorMod(session.seed?.hashCode() ?: 0, variants.size)
        return variants[index]
    }

    @Transactional
    fun submit(sessionId: UUID, rawText: String?, structuredJson: String?, clientRequestId: String?): Submission {
        if (clientRequestId != null) {
            submissionRepository.findBySessionIdAndClientRequestId(sessionId, clientRequestId)?.let { return it }
        }

        val session = getSession(sessionId)
        SessionStateMachine.requireTransition(session.status, SessionStatus.SUBMITTED)

        // Read before compareAndSetStatus flips it, not after — the deadline is
        // computed from the phase row that's about to be marked complete, and
        // this is the last moment "now" still means "at submission time".
        val deadline = getPhaseDeadline(session)

        val updated = sessionRepository.compareAndSetStatus(sessionId, session.status, SessionStatus.SUBMITTED)
        if (updated == 0) {
            throw ConflictException("Session $sessionId is not accepting submissions right now")
        }

        val submission = submissionRepository.save(
            Submission(
                sessionId = sessionId,
                phase = session.currentPhase ?: "UNKNOWN",
                rawText = rawText,
                structuredJson = structuredJson,
                clientRequestId = clientRequestId,
                onTime = deadline?.let { !Instant.now().isAfter(it) },
            )
        )
        // Delivered after this transaction commits (EvaluationRequestPublisher), so the
        // SUBMITTED -> EVALUATING move and the queue enqueue only happen once the
        // submission is durably saved.
        eventPublisher.publishEvent(EvaluationRequested(submission.id!!, sessionId))
        return submission
    }

    /**
     * Moves a session on to the next scenario step once feedback is ready, or
     * completes it when there is no next step. SUBMITTED -> EVALUATING ->
     * FEEDBACK_READY is driven by EvaluationRequestPublisher/EvaluationWorker
     * (PLAN.md step 3); this only implements the FEEDBACK_READY -> * leg.
     */
    @Transactional
    fun advance(sessionId: UUID): Session {
        val session = getSession(sessionId)
        val currentPhaseRow = sessionPhaseRepository.findTopBySessionIdOrderByPhaseOrderDesc(sessionId)
            ?: throw IllegalStateException("Session $sessionId has no phase history")

        val nextStep = scenarioStepRepository.findByScenarioVersionIdAndStepOrder(
            session.scenarioVersionId,
            currentPhaseRow.phaseOrder + 1,
        )
        val nextStatus = if (nextStep != null) SessionStatus.IN_PROGRESS else SessionStatus.COMPLETED

        SessionStateMachine.requireTransition(session.status, nextStatus)
        val updated = sessionRepository.compareAndSetStatus(sessionId, session.status, nextStatus)
        if (updated == 0) {
            throw ConflictException("Session $sessionId state changed concurrently")
        }

        currentPhaseRow.status = "COMPLETED"
        currentPhaseRow.completedAt = Instant.now()
        sessionPhaseRepository.save(currentPhaseRow)

        val refreshed = getSession(sessionId)
        if (nextStep != null) {
            refreshed.currentPhase = nextStep.stepType
            sessionPhaseRepository.save(
                SessionPhase(
                    sessionId = sessionId,
                    phaseType = nextStep.stepType,
                    phaseOrder = nextStep.stepOrder,
                    status = "IN_PROGRESS",
                    startedAt = Instant.now(),
                )
            )
        } else {
            refreshed.completedAt = Instant.now()
        }
        val saved = sessionRepository.save(refreshed)
        if (nextStep == null) {
            reportService.generate(sessionId)
        }
        return saved
    }
}
