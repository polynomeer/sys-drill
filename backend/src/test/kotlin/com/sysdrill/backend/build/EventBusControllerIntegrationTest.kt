package com.sysdrill.backend.build

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.submitBuildChallenge
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Drives the real async Build pipeline for the "event-bus" challenge
 * (PLAN.md step 17), mirroring the coverage established for
 * "rate-limiter"/"queue"/"circuit-breaker"/"distributed-lock"/"retry-backoff".
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventBusControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "eventbus-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun submit(sourceCode: String): UUID =
        mockMvc.submitBuildChallenge(objectMapper, "event-bus", userId, sourceCode)

    private fun awaitCompleted(submissionId: UUID, timeout: Duration = Duration.ofSeconds(60)): String {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            val response = mockMvc.perform(get("/build-submissions/$submissionId").header("Authorization", bearerHeader(userId))).andReturn().response.contentAsString
            val status = JsonPath.read<String>(response, "$.status")
            if (status == "COMPLETED" || status == "ERROR") return response
            Thread.sleep(300)
        }
        error("Build submission $submissionId did not complete within $timeout")
    }

    @Test
    fun `a correct event bus implementation passes all four stages`() {
        val submissionId = submit(CORRECT_EVENT_BUS)
        val response = awaitCompleted(submissionId)

        assertThat(JsonPath.read<String>(response, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(response, "$.score")).isEqualTo(4)
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).allMatch { it == "PASSED" }
    }

    @Test
    fun `an unimplemented stub fails every stage`() {
        val submissionId = submit(STUB_EVENT_BUS)
        val response = awaitCompleted(submissionId)

        assertThat(JsonPath.read<String>(response, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(response, "$.score")).isEqualTo(0)
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).allMatch { it == "FAILED" }
        assertThat(JsonPath.read<String>(response, "$.stages[0].feedback")).contains("not implemented")
    }

    private companion object {
        // Mirrors challenges/event-bus/event_bus.py's reference solution —
        // verified against the real sandbox before being written into the
        // seeded stage tests (see PLAN.md step 17 notes).
        val CORRECT_EVENT_BUS = """
            import threading
            import time
            import uuid
            from collections import deque

            class EventBus:
                def __init__(self, visibility_timeout=5.0, max_retries=3):
                    self._lock = threading.Lock()
                    self._subscribers = {}
                    self._ready = {}
                    self._in_flight = {}
                    self.visibility_timeout = visibility_timeout
                    self.max_retries = max_retries

                def subscribe(self, topic):
                    with self._lock:
                        subscriber_id = str(uuid.uuid4())
                        self._subscribers[subscriber_id] = topic
                        self._ready[subscriber_id] = deque()
                        self._in_flight[subscriber_id] = {}
                        return subscriber_id

                def publish(self, topic, payload):
                    with self._lock:
                        event_id = str(uuid.uuid4())
                        for sub_id, sub_topic in self._subscribers.items():
                            if sub_topic == topic:
                                self._ready[sub_id].append({"id": event_id, "payload": payload, "attempts": 0})
                        return event_id

                def _requeue_expired_locked(self, subscriber_id):
                    now = time.time()
                    in_flight = self._in_flight[subscriber_id]
                    expired_ids = [eid for eid, (msg, visible_at, attempts) in in_flight.items() if visible_at <= now]
                    for eid in expired_ids:
                        msg, _, attempts = in_flight.pop(eid)
                        if attempts < self.max_retries:
                            self._ready[subscriber_id].append(msg)

                def poll(self, subscriber_id):
                    with self._lock:
                        self._requeue_expired_locked(subscriber_id)
                        ready = self._ready[subscriber_id]
                        if not ready:
                            return None
                        msg = ready.popleft()
                        msg["attempts"] += 1
                        self._in_flight[subscriber_id][msg["id"]] = (msg, time.time() + self.visibility_timeout, msg["attempts"])
                        return {"id": msg["id"], "payload": msg["payload"]}

                def ack(self, subscriber_id, event_id):
                    with self._lock:
                        self._in_flight[subscriber_id].pop(event_id, None)
        """.trimIndent()

        val STUB_EVENT_BUS = """
            class EventBus:
                def __init__(self, visibility_timeout=5.0, max_retries=3):
                    raise NotImplementedError
                def subscribe(self, topic):
                    raise NotImplementedError
                def publish(self, topic, payload):
                    raise NotImplementedError
                def poll(self, subscriber_id):
                    raise NotImplementedError
                def ack(self, subscriber_id, event_id):
                    raise NotImplementedError
        """.trimIndent()
    }
}
