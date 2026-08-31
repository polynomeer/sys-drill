package com.sysdrill.backend.simulation.realinfra

import org.springframework.stereotype.Component
import java.util.UUID

/** PLAN.md step 27 — extracted from [RealInfraSessionSweepWorker] verbatim so the sweep worker can generalize to a second real-infra domain via [RealInfraResourceCleaner] without knowing which domains exist. */
@Component
class CouponRealInfraCleaner(
    private val schemaProvisioner: CouponSchemaProvisioner,
    private val dataSourceRegistry: SessionDataSourceRegistry,
    private val toxiproxy: ToxiproxySessionProxy,
    private val stats: RealInfraCouponStats,
) : RealInfraResourceCleaner {

    override fun cleanup(sessionId: UUID) {
        dataSourceRegistry.evict(sessionId)
        toxiproxy.evict(sessionId)
        schemaProvisioner.drop(sessionId)
        stats.evict(sessionId)
    }
}
