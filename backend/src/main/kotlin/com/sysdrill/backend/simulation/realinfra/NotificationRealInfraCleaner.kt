package com.sysdrill.backend.simulation.realinfra

import org.springframework.stereotype.Component
import java.util.UUID

/** PLAN.md step 27 — drops an abandoned real-infra notification session's Kafka topic, mirroring [CouponRealInfraCleaner]. */
@Component
class NotificationRealInfraCleaner(
    private val topicProvisioner: NotificationTopicProvisioner,
) : RealInfraResourceCleaner {

    override fun cleanup(sessionId: UUID) {
        topicProvisioner.drop(sessionId)
    }
}
