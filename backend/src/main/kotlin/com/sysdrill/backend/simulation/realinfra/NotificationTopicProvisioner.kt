package com.sysdrill.backend.simulation.realinfra

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * PLAN.md step 27 — provisions/drops one Kafka topic per real-infra
 * notification session, mirroring [CouponSchemaProvisioner]'s
 * schema-per-session shape (ADR-0013) applied to Kafka instead of Postgres.
 * Idempotent both ways: provisioning an already-existing topic and dropping
 * an already-gone one are both no-ops, not errors.
 */
@Component
class NotificationTopicProvisioner(
    @Value("\${sysdrill.simulation.realinfra.kafka.bootstrap-servers}") private val bootstrapServers: String,
    @Value("\${sysdrill.simulation.realinfra.kafka.partitions-per-session}") private val partitionsPerSession: Int,
) {
    /** Kafka topic names reject "-" inside a session UUID just fine, but stripping it keeps this consistent with [CouponSchemaProvisioner]'s schema-name style. */
    fun topicName(sessionId: UUID): String = "realinfra-notify-${sessionId.toString().replace("-", "")}"

    fun consumerGroupId(sessionId: UUID): String = "realinfra-notify-group-${sessionId.toString().replace("-", "")}"

    fun provision(sessionId: UUID) {
        adminClient().use { admin ->
            try {
                admin.createTopics(listOf(NewTopic(topicName(sessionId), partitionsPerSession, 1.toShort())))
                    .all().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (ex: ExecutionException) {
                if (ex.cause !is TopicExistsException) throw ex
            }
        }
    }

    fun drop(sessionId: UUID) {
        adminClient().use { admin ->
            try {
                admin.deleteTopics(listOf(topicName(sessionId))).all().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (ex: ExecutionException) {
                if (ex.cause !is UnknownTopicOrPartitionException) throw ex
            }
        }
    }

    private fun adminClient(): AdminClient =
        AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers))

    private companion object {
        const val ADMIN_TIMEOUT_SECONDS = 10L
    }
}
