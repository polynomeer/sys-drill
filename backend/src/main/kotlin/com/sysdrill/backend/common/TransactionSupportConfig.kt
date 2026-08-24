package com.sysdrill.backend.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/**
 * Exposes [TransactionTemplate] beans for components that need programmatic
 * transactions because @Transactional self-invocation doesn't go through the
 * Spring AOP proxy (e.g. EvaluationWorker).
 */
@Configuration
class TransactionSupportConfig {

    @Bean
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(transactionManager)

    /**
     * A fresh, independent transaction rather than one that tries to join
     * whatever (possibly stale) synchronization state is on the current
     * thread. Needed by EvaluationRequestPublisher: its @TransactionalEventListener
     * runs AFTER_COMMIT on the same thread as the transaction that just
     * completed, and starting a REQUIRED-propagation transaction from inside
     * that afterCommit callback fails with "No active transaction for update
     * or delete query" — the thread-local transaction state isn't fully torn
     * down yet even though no transaction is actually active anymore.
     */
    @Bean("requiresNewTransactionTemplate")
    fun requiresNewTransactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
}
