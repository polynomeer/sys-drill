package com.sysdrill.backend.simulation.realinfra

import com.sysdrill.backend.simulation.DesignTraits
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.ceil

data class NotificationProbeSummary(
    val achievedProducerRate: Double,
    val consumerThroughput: Double,
    val p95LatencyMs: Double,
    val errorRate: Double,
    val queueLagAtEnd: Long,
    val processingDelayMs: Long,
)

/**
 * PLAN.md step 27 — the real load generator + measurement for the
 * notification real-infra pilot (ADR-0017: in-process kafka-clients calls,
 * not an external load-gen container like [CouponLoadRunner]'s k6). A real
 * producer floods this session's topic for
 * `sysdrill.simulation.realinfra.kafka.probe-duration-seconds`; real consumer
 * threads (one per [DesignTraits.consumerCount], sharing one consumer group so
 * Kafka itself partitions the work) each pay a real per-message processing
 * delay standing in for the "call the SMS/email provider" step docs/PRD.md
 * §8.2 describes — the SAME constants [RuleBasedSimulationEngine]'s
 * notification domain uses, just spent as a real `Thread.sleep` instead of a
 * formula term. A message that arrives (real, measured wall-clock time from
 * publish to consume) later than `expiry-ms` counts as failed — "the
 * notification arrived so late it's no longer useful" — instead of a
 * separate DLQ topic (deliberately cut for this pilot's first slice).
 *
 * Every call uses a brand-new consumer group starting from `latest` (not the
 * session's committed offsets) — found empirically necessary: an earlier
 * version reused one group per session so backlog would carry forward across
 * actions (mirroring [RealInfraCouponEngine]'s carried-forward inventory),
 * but Kafka delivers a group its oldest unread message first, so once a
 * severe incident backlogs a few thousand messages, every later probe's
 * consumers spend their whole window still working through *that* backlog —
 * p95/errorRate then measure how late years-old-by-comparison messages
 * arrived, not whether the just-applied action helped, and the numbers only
 * get worse action over action even after the right fix is applied. A fresh
 * `latest`-starting group each probe measures only "does this configuration
 * keep up with load produced during this window" — real, but deliberately
 * scoped to the probe, not the whole session's history.
 */
@Component
class NotificationLoadRunner(
    private val topicProvisioner: NotificationTopicProvisioner,
    @Value("\${sysdrill.simulation.realinfra.kafka.bootstrap-servers}") private val bootstrapServers: String,
    @Value("\${sysdrill.simulation.realinfra.kafka.probe-duration-seconds}") private val probeDurationSeconds: Int,
    @Value("\${sysdrill.simulation.realinfra.kafka.drain-buffer-ms}") private val drainBufferMs: Long,
    @Value("\${sysdrill.simulation.realinfra.kafka.baseline-event-rate}") private val baselineEventRate: Int,
    @Value("\${sysdrill.simulation.realinfra.kafka.incident-event-rate}") private val incidentEventRate: Int,
    @Value("\${sysdrill.simulation.realinfra.kafka.expiry-ms}") private val expiryMs: Long,
) {
    fun run(sessionId: UUID, traits: DesignTraits, incidentActive: Boolean): NotificationProbeSummary {
        val topic = topicProvisioner.topicName(sessionId)
        // A fresh group id every call — see the class doc for why this can't
        // be one stable per-session group.
        val groupId = "${topicProvisioner.consumerGroupId(sessionId)}-${UUID.randomUUID().toString().take(8)}"

        val processingDelayMs = when {
            traits.circuitBreakerEnabled -> FAST_FAIL_LATENCY_MS
            incidentActive -> (BASE_PROVIDER_LATENCY_MS * PROVIDER_DEGRADATION_FACTOR).toLong()
            else -> BASE_PROVIDER_LATENCY_MS
        }
        val baseRate = if (incidentActive) incidentEventRate else baselineEventRate
        val retryAmplification = if (incidentActive) RETRY_STORM_FACTOR / traits.retryBackoffMultiplier else 0.0
        val effectiveRate = baseRate * (1 + retryAmplification)

        val consumedCount = AtomicLong(0)
        val failedCount = AtomicLong(0)
        val latencies = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val stopping = java.util.concurrent.atomic.AtomicBoolean(false)

        val consumerThreads = (1..traits.consumerCount).map { idx ->
            thread(name = "notify-consumer-$sessionId-$idx", isDaemon = true) {
                val consumer = KafkaConsumer<String, String>(consumerProps(groupId))
                try {
                    consumer.subscribe(listOf(topic))
                    while (!stopping.get()) {
                        val records = consumer.poll(Duration.ofMillis(POLL_TIMEOUT_MILLIS))
                        for (record in records) {
                            Thread.sleep(processingDelayMs)
                            val sentAt = record.value().toLong()
                            val latency = System.currentTimeMillis() - sentAt
                            latencies.add(latency)
                            if (latency > expiryMs) failedCount.incrementAndGet() else consumedCount.incrementAndGet()
                        }
                        if (records.count() > 0) consumer.commitSync()
                    }
                } catch (_: InterruptedException) {
                    // Stopped mid-sleep/poll — fall through to close(); whatever was
                    // already committed this loop stands, the rest stays real backlog.
                } finally {
                    runCatching { consumer.close(Duration.ofSeconds(2)) }
                }
            }
        }

        // Give the group a moment to finish its initial join/rebalance before the
        // producer starts, so the first wave of messages isn't consumed by nobody.
        Thread.sleep(CONSUMER_WARMUP_MILLIS)

        val producedCount = AtomicLong(0)
        val intervalMs = (1000.0 / effectiveRate).toLong().coerceAtLeast(1)
        KafkaProducer<String, String>(producerProps()).use { producer ->
            val deadline = System.currentTimeMillis() + probeDurationSeconds * 1000L
            while (System.currentTimeMillis() < deadline) {
                producer.send(ProducerRecord(topic, System.currentTimeMillis().toString()))
                producedCount.incrementAndGet()
                Thread.sleep(intervalMs)
            }
            producer.flush()
        }

        // Let consumers keep draining the tail backlog before cutting them off.
        Thread.sleep(drainBufferMs)
        stopping.set(true)
        consumerThreads.forEach { it.interrupt() }
        consumerThreads.forEach { it.join(JOIN_TIMEOUT_MILLIS) }

        val queueLag = measureLag(topic, groupId)
        val totalProcessed = consumedCount.get() + failedCount.get()
        val errorRate = if (totalProcessed == 0L) 0.0 else failedCount.get().toDouble() / totalProcessed

        return NotificationProbeSummary(
            achievedProducerRate = producedCount.get().toDouble() / probeDurationSeconds,
            consumerThroughput = consumedCount.get().toDouble() / probeDurationSeconds,
            p95LatencyMs = percentile(latencies, 0.95),
            errorRate = errorRate,
            queueLagAtEnd = queueLag,
            processingDelayMs = processingDelayMs,
        )
    }

    private fun measureLag(topic: String, groupId: String): Long {
        AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)).use { admin ->
            val partitions = admin.describeTopics(listOf(topic)).topicNameValues()[topic]!!
                .get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .partitions().map { TopicPartition(topic, it.partition()) }

            val committed = admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata()
                .get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val endOffsets = admin.listOffsets(partitions.associateWith { OffsetSpec.latest() })
                .all().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            return partitions.sumOf { tp ->
                val end = endOffsets[tp]?.offset() ?: 0L
                val committedOffset = committed[tp]?.offset() ?: 0L
                (end - committedOffset).coerceAtLeast(0L)
            }
        }
    }

    private fun consumerProps(groupId: String) = mapOf(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
        ConsumerConfig.GROUP_ID_CONFIG to groupId,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
    )

    private fun producerProps() = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
        ProducerConfig.ACKS_CONFIG to "1",
    )

    private fun percentile(values: List<Long>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = (ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)
        return sorted[index].toDouble()
    }

    private companion object {
        // Same constants as RuleBasedSimulationEngine's private Notification
        // object (docs/PRD.md §8.2) — here they're real Thread.sleep durations,
        // not formula terms.
        const val BASE_PROVIDER_LATENCY_MS = 20L
        const val PROVIDER_DEGRADATION_FACTOR = 15.0
        const val FAST_FAIL_LATENCY_MS = 10L
        const val RETRY_STORM_FACTOR = 2.0

        const val POLL_TIMEOUT_MILLIS = 200L
        const val CONSUMER_WARMUP_MILLIS = 500L
        const val JOIN_TIMEOUT_MILLIS = 3000L
        const val ADMIN_TIMEOUT_SECONDS = 10L
    }
}
