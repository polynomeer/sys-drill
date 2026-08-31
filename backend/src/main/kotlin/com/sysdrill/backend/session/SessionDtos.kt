package com.sysdrill.backend.session

import com.sysdrill.backend.submission.Submission
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class StartSessionRequest(
    @field:NotNull val userId: UUID,
    @field:NotNull val scenarioId: UUID,
    /** Bridge Mode: links this session to a just-completed Build submission (PLAN.md step 10). */
    val buildSubmissionId: UUID? = null,
    /** Optional override for reproducible variant selection (PLAN.md step 12); server-generated if omitted. */
    val seed: String? = null,
    /** Opts into PLAN.md step 28's interview-timer mode — per-phase time limits, shown as a countdown. */
    val interviewMode: Boolean = false,
)

data class SubmitAnswerRequest(
    val rawText: String? = null,
    val structuredJson: String? = null,
    val clientRequestId: String? = null,
)

data class SessionResponse(
    val id: UUID,
    val status: SessionStatus,
    val currentPhase: String?,
    val currentStepPrompt: String?,
    val scenarioVersionId: UUID,
    val domain: String,
    val buildSubmissionId: UUID?,
    val interviewMode: Boolean,
    /** Null unless [interviewMode] — when the current phase must be submitted by, per PLAN.md step 28. */
    val phaseDeadlineAt: Instant?,
    val startedAt: Instant,
    val completedAt: Instant?,
) {
    companion object {
        fun from(session: Session, currentStepPrompt: String?, domain: String, phaseDeadlineAt: Instant?) = SessionResponse(
            id = session.id!!,
            status = session.status,
            currentPhase = session.currentPhase,
            currentStepPrompt = currentStepPrompt,
            scenarioVersionId = session.scenarioVersionId,
            domain = domain,
            buildSubmissionId = session.buildSubmissionId,
            interviewMode = session.interviewMode,
            phaseDeadlineAt = phaseDeadlineAt,
            startedAt = session.startedAt,
            completedAt = session.completedAt,
        )
    }
}

data class SubmissionResponse(
    val id: UUID,
    val sessionId: UUID,
    val phase: String,
    val revisionNo: Int,
    /** PLAN.md step 28 — null unless the session was in interview-timer mode. */
    val onTime: Boolean?,
) {
    companion object {
        fun from(submission: Submission) = SubmissionResponse(
            id = submission.id!!,
            sessionId = submission.sessionId,
            phase = submission.phase,
            revisionNo = submission.revisionNo,
            onTime = submission.onTime,
        )
    }
}
