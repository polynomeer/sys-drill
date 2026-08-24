package com.sysdrill.backend.session

/**
 * Valid transitions for [SessionStatus] per docs/ARCHITECTURE.md §5.
 * Pure domain logic, independent of persistence, so it can be unit tested
 * without a database.
 */
object SessionStateMachine {

    private val allowedTransitions: Map<SessionStatus, Set<SessionStatus>> = mapOf(
        SessionStatus.IN_PROGRESS to setOf(SessionStatus.SUBMITTED),
        SessionStatus.SUBMITTED to setOf(SessionStatus.EVALUATING),
        SessionStatus.EVALUATING to setOf(SessionStatus.FEEDBACK_READY, SessionStatus.EVALUATION_FAILED),
        SessionStatus.FEEDBACK_READY to setOf(SessionStatus.IN_PROGRESS, SessionStatus.COMPLETED),
        SessionStatus.EVALUATION_FAILED to setOf(SessionStatus.EVALUATING),
        SessionStatus.COMPLETED to emptySet(),
        SessionStatus.ABANDONED to setOf(SessionStatus.IN_PROGRESS),
    )

    fun canTransition(from: SessionStatus, to: SessionStatus): Boolean =
        to in allowedTransitions[from].orEmpty()

    /** Throws [IllegalStateException] (mapped to HTTP 409) when the transition isn't allowed. */
    fun requireTransition(from: SessionStatus, to: SessionStatus) {
        check(canTransition(from, to)) { "Cannot transition session from $from to $to" }
    }
}
