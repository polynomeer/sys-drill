package com.sysdrill.backend.build

import com.sysdrill.backend.common.events.BuildSubmissionRequested
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Enqueues the build job only once the submission is durably committed
 * (see [BuildSubmissionService.submit]'s comment) — mirrors
 * EvaluationRequestPublisher's AFTER_COMMIT handling of EvaluationRequested.
 * No DB write happens here, so unlike EvaluationRequestPublisher this
 * doesn't need its own TransactionTemplate.
 */
@Component
class BuildJobPublisher(
    private val buildJobQueue: BuildJobQueue,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onBuildSubmissionRequested(event: BuildSubmissionRequested) {
        buildJobQueue.enqueue(event.submissionId)
    }
}
