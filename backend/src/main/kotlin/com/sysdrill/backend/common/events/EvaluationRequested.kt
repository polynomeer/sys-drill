package com.sysdrill.backend.common.events

import java.util.UUID

/**
 * Raised after a submission is durably saved and the owning session has
 * moved to SUBMITTED. Lives in `common.events` (rather than `session` or
 * `evaluation`) so the two modules don't need to depend on each other just
 * to exchange this event.
 */
data class EvaluationRequested(val submissionId: UUID, val sessionId: UUID)
