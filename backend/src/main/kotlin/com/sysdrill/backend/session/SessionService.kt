package com.sysdrill.backend.session

import com.sysdrill.backend.common.events.EvaluationRequested
import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioStepRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.submission.Submission
import com.sysdrill.backend.submission.SubmissionRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
) {

    @Transactional
    fun startSession(userId: UUID, scenarioId: UUID): Session {
        val scenario = scenarioRepository.findById(scenarioId)
            .orElseThrow { NotFoundException("Scenario not found: $scenarioId") }
        val version = scenarioVersionRepository
            .findFirstByScenarioIdAndStatusOrderByVersionNoDesc(scenario.id!!, "PUBLISHED")
            ?: throw NotFoundException("No published version for scenario: $scenarioId")
        val firstStep = scenarioStepRepository.findByScenarioVersionIdAndStepOrder(version.id!!, 1)
            ?: throw IllegalStateException("Scenario version has no steps: ${version.id}")

        val session = sessionRepository.save(
            Session(userId = userId, scenarioVersionId = version.id!!, currentPhase = firstStep.stepType)
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
        return sessionRepository.save(refreshed)
    }
}
