package com.sysdrill.backend.simulation.realinfra

import java.util.UUID

/**
 * PLAN.md step 27 — one implementation per real-infra domain (coupon,
 * notification, ...), each dropping that domain's own dedicated resources
 * for one abandoned session. [RealInfraSessionSweepWorker] calls every
 * registered cleaner for every expired session id, tolerating the ones that
 * have nothing to clean up for that domain (each domain's provisioner/evict
 * calls are already idempotent no-ops when nothing was ever provisioned) —
 * simpler than teaching the sweep worker which domain a session belongs to.
 */
interface RealInfraResourceCleaner {
    fun cleanup(sessionId: UUID)
}
