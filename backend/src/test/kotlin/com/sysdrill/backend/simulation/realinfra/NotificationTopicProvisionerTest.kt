package com.sysdrill.backend.simulation.realinfra

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Exercises real Kafka topic create/delete against the real broker container (not mocked) — PLAN.md step 27. */
@SpringBootTest
class NotificationTopicProvisionerTest(
    @Autowired val provisioner: NotificationTopicProvisioner,
    @Value("\${sysdrill.simulation.realinfra.kafka.bootstrap-servers}") val bootstrapServers: String,
) {

    /** Set by each test right after provisioning, so [cleanUp] can drop it even if an assertion above fails. */
    private var provisionedSessionId: UUID? = null

    @AfterEach
    fun cleanUp() {
        provisionedSessionId?.let(provisioner::drop)
    }

    private fun listTopics(): Set<String> =
        AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)).use {
            it.listTopics().names().get(10, TimeUnit.SECONDS)
        }

    /**
     * createTopics().all().get() completing only means the controller accepted the
     * request — a fresh AdminClient's listTopics() reads a metadata cache that can lag
     * behind briefly, more so as the shared broker accumulates topics across repeated
     * test runs. Poll instead of asserting on a single snapshot.
     */
    private fun awaitTopicsMatching(
        timeout: Duration = Duration.ofSeconds(10),
        predicate: (Set<String>) -> Boolean,
    ): Set<String> {
        val deadline = Instant.now().plus(timeout)
        var snapshot = listTopics()
        while (!predicate(snapshot) && Instant.now().isBefore(deadline)) {
            Thread.sleep(200)
            snapshot = listTopics()
        }
        return snapshot
    }

    @Test
    fun `provisions a real topic with the configured partition count`() {
        val sessionId = UUID.randomUUID().also { provisionedSessionId = it }

        provisioner.provision(sessionId)

        val topics = awaitTopicsMatching { it.contains(provisioner.topicName(sessionId)) }
        assertThat(topics).contains(provisioner.topicName(sessionId))
    }

    @Test
    fun `provisioning twice is idempotent, not an error`() {
        val sessionId = UUID.randomUUID().also { provisionedSessionId = it }
        provisioner.provision(sessionId)

        provisioner.provision(sessionId)

        val topics = awaitTopicsMatching { it.contains(provisioner.topicName(sessionId)) }
        assertThat(topics).contains(provisioner.topicName(sessionId))
    }

    @Test
    fun `drop removes the topic, and dropping again is a no-op`() {
        val sessionId = UUID.randomUUID().also { provisionedSessionId = it }
        provisioner.provision(sessionId)

        provisioner.drop(sessionId)
        provisioner.drop(sessionId)

        val topics = awaitTopicsMatching { !it.contains(provisioner.topicName(sessionId)) }
        assertThat(topics).doesNotContain(provisioner.topicName(sessionId))
    }
}
