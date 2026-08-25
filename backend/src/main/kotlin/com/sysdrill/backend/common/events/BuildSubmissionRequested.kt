package com.sysdrill.backend.common.events

import java.util.UUID

/**
 * Raised after a build submission is durably saved. Lives in `common.events`
 * for the same reason as [EvaluationRequested] — keeps `build` from needing
 * a compile-time dependency on whichever module publishes it.
 */
data class BuildSubmissionRequested(val submissionId: UUID)
