package com.sysdrill.backend.session

import com.sysdrill.backend.build.BuildSubmissionRepository
import com.sysdrill.backend.build.BuildSubmissionStatus
import com.sysdrill.backend.common.events.EvaluationRequested
import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioStepRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.reporting.ReportService
import com.sysdrill.backend.scenario.ScenarioStep
import com.sysdrill.backend.submission.Submission
import com.sysdrill.backend.submission.SubmissionRepository
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
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun startSession(userId: UUID, scenarioId: UUID, buildSubmissionId: UUID? = null): Session {
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
                currentPhase = firstStep.stepType,
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
        return step?.let { extractPrompt(it) }
    }

    private fun extractPrompt(step: ScenarioStep): String? {
        val content = step.content ?: return null
        @Suppress("UNCHECKED_CAST")
        val map = objectMapper.readValue(content, Map::class.java) as Map<String, Any?>
        return map["prompt"] as? String
    }

    @Transactional
    fun submit(sessionId: UUID, rawText: String?, structuredJson: String?, clientRequestId: String?): Submission {
        if (clientRequestId != null) {
            submissionRepository.findBySessionIdAndClientRequestId(sessionId, clientRequestId)?.let { return it }
        }

        val session = getSession(sessionId)
        SessionStateMachine.requireTransition(session.status, SessionStatus.SUBMITTED)

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
