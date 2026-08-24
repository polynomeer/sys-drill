package com.sysdrill.backend.session

import com.sysdrill.backend.submission.Submission
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class StartSessionRequest(
    @field:NotNull val userId: UUID,
    @field:NotNull val scenarioId: UUID,
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
    val scenarioVersionId: UUID,
    val startedAt: Instant,
    val completedAt: Instant?,
) {
    companion object {
        fun from(session: Session) = SessionResponse(
            id = session.id!!,
            status = session.status,
            currentPhase = session.currentPhase,
            scenarioVersionId = session.scenarioVersionId,
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
) {
    companion object {
        fun from(submission: Submission) = SubmissionResponse(
            id = submission.id!!,
            sessionId = submission.sessionId,
            phase = submission.phase,
            revisionNo = submission.revisionNo,
        )
    }
}
