package com.sysdrill.backend.evaluation

import com.sysdrill.backend.common.events.EvaluationRequested
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate

/**
 * Turns a durably-committed submission into an enqueued evaluation job. Runs
 * AFTER_COMMIT so a session only ever reaches EVALUATING once the job it
 * corresponds to has actually been pushed to Redis (docs/ARCHITECTURE.md §8).
 */
@Component
class EvaluationRequestPublisher(
    private val sessionRepository: SessionRepository,
    private val evaluationQueue: EvaluationQueue,
    @Qualifier("requiresNewTransactionTemplate") private val transactionTemplate: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onEvaluationRequested(event: EvaluationRequested) {
        val updated = transactionTemplate.execute {
            sessionRepository.compareAndSetStatus(event.sessionId, SessionStatus.SUBMITTED, SessionStatus.EVALUATING)
        }

        if (updated == 0) {
            log.warn(
                "Session {} was not SUBMITTED when handling evaluation request for submission {}; skipping enqueue",
                event.sessionId,
                event.submissionId,
            )
            return
        }
        evaluationQueue.enqueue(event.submissionId)
    }
}
