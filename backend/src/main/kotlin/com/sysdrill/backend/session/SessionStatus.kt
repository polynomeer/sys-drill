package com.sysdrill.backend.session

/**
 * Session state machine per docs/ARCHITECTURE.md §5.
 * Valid transitions are enforced by [SessionStateMachine].
 */
enum class SessionStatus {
    IN_PROGRESS,
    SUBMITTED,
    EVALUATING,
    FEEDBACK_READY,
    EVALUATION_FAILED,
    COMPLETED,
    ABANDONED,
}
