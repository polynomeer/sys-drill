package com.sysdrill.backend.session

import com.sysdrill.backend.submission.Submission
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

/** PLAN.md step 30 — no userId field: the session owner comes from the verified Authorization token (see AuthenticatedUserId), not client-supplied input. */
data class StartSessionRequest(
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
    /** PLAN.md step 36 — the frontend keeps no local userId (removed in step 31), so it needs the server to say whether the caller is the owner or a Game Day spectator. */
    val isOwner: Boolean,
    /** docs/CODECRAFTERS_BENCHMARK.md §3.3 — this session's version step types in order (INITIAL, FOLLOWUP[, INCIDENT]), so the workspace stage list reflects the real stage count instead of a hard-coded one. Types only — later steps' prompts stay hidden. */
    val stepTypes: List<String> = emptyList(),
    /** PLAN.md Round E3 — the scenario (not just the version), so the report can show that scenario's discussion. */
    val scenarioId: UUID? = null,
) {
    companion object {
        fun from(
            session: Session,
            currentStepPrompt: String?,
            domain: String,
            phaseDeadlineAt: Instant?,
            callerId: UUID,
            stepTypes: List<String> = emptyList(),
            scenarioId: UUID? = null,
        ) = SessionResponse(
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
            isOwner = session.userId == callerId,
            stepTypes = stepTypes,
            scenarioId = scenarioId,
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
