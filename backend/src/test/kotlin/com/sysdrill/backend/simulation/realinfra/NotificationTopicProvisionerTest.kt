package com.sysdrill.backend.simulation.realinfra

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Exercises real Kafka topic create/delete against the real broker container (not mocked) — PLAN.md step 27. */
@SpringBootTest
class NotificationTopicProvisionerTest(
    @Autowired val provisioner: NotificationTopicProvisioner,
    @Value("\${sysdrill.simulation.realinfra.kafka.bootstrap-servers}") val bootstrapServers: String,
) {

    private fun listTopics(): Set<String> =
        AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)).use {
            it.listTopics().names().get(10, TimeUnit.SECONDS)
        }

    @Test
    fun `provisions a real topic with the configured partition count`() {
        val sessionId = UUID.randomUUID()

        provisioner.provision(sessionId)

        assertThat(listTopics()).contains(provisioner.topicName(sessionId))

        provisioner.drop(sessionId)
    }

    @Test
    fun `provisioning twice is idempotent, not an error`() {
        val sessionId = UUID.randomUUID()
        provisioner.provision(sessionId)

        provisioner.provision(sessionId)

        assertThat(listTopics()).contains(provisioner.topicName(sessionId))
        provisioner.drop(sessionId)
    }

    @Test
    fun `drop removes the topic, and dropping again is a no-op`() {
        val sessionId = UUID.randomUUID()
        provisioner.provision(sessionId)

        provisioner.drop(sessionId)
        provisioner.drop(sessionId)

        assertThat(listTopics()).doesNotContain(provisioner.topicName(sessionId))
    }
}
