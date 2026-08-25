package com.sysdrill.backend.build

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Drives the real async Build pipeline (submit -> BuildJobQueue ->
 * BuildRunnerWorker -> 4x real `docker run` sandbox executions ->
 * BuildStageResult rows) for the "queue" challenge (PLAN.md step 11),
 * mirroring BuildControllerIntegrationTest's coverage of "rate-limiter".
 */
@SpringBootTest
@AutoConfigureMockMvc
class QueueControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "queue-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun submit(sourceCode: String): UUID {
        val body = objectMapper.writeValueAsString(
            mapOf("userId" to userId.toString(), "sourceCode" to sourceCode, "commitRef" to "test")
        )
        val response = mockMvc.perform(
            post("/build-challenges/queue/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andReturn().response.contentAsString
        return UUID.fromString(JsonPath.read(response, "$.id"))
    }

    private fun awaitCompleted(submissionId: UUID, timeout: Duration = Duration.ofSeconds(60)): String {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            val response = mockMvc.perform(get("/build-submissions/$submissionId")).andReturn().response.contentAsString
            val status = JsonPath.read<String>(response, "$.status")
            if (status == "COMPLETED" || status == "ERROR") return response
            Thread.sleep(300)
        }
        error("Build submission $submissionId did not complete within $timeout")
    }

    @Test
    fun `a correct queue implementation passes all four stages`() {
        val submissionId = submit(CORRECT_QUEUE)
        val response = awaitCompleted(submissionId)

        assertThat(JsonPath.read<String>(response, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(response, "$.score")).isEqualTo(4)
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).allMatch { it == "PASSED" }
    }

    @Test
    fun `an unimplemented stub fails every stage`() {
        val submissionId = submit(STUB_QUEUE)
        val response = awaitCompleted(submissionId)

        assertThat(JsonPath.read<String>(response, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(response, "$.score")).isEqualTo(0)
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).allMatch { it == "FAILED" }
        assertThat(JsonPath.read<String>(response, "$.stages[0].feedback")).contains("not implemented")
    }

    private companion object {
        // Mirrors challenges/queue/queue_impl.py's reference solution — verified
        // against the real sandbox before being written into the seeded stage
        // tests (see PLAN.md step 11 notes).
        val CORRECT_QUEUE = """
            import threading
            import time
            import uuid
            from collections import deque

            class Queue:
                def __init__(self, visibility_timeout=5.0, max_retries=3):
                    self.visibility_timeout = visibility_timeout
                    self.max_retries = max_retries
                    self._lock = threading.Lock()
                    self._ready = deque()
                    self._in_flight = {}
                    self._dlq = []

                def enqueue(self, payload):
                    with self._lock:
                        message_id = str(uuid.uuid4())
                        self._ready.append({"id": message_id, "payload": payload, "attempts": 0})
                        return message_id

                def _requeue_expired_locked(self):
                    now = time.time()
                    expired_ids = [mid for mid, (msg, visible_at) in self._in_flight.items() if visible_at <= now]
                    for mid in expired_ids:
                        msg, _ = self._in_flight.pop(mid)
                        if msg["attempts"] >= self.max_retries:
                            self._dlq.append(msg)
                        else:
                            self._ready.append(msg)

                def dequeue(self):
                    with self._lock:
                        self._requeue_expired_locked()
                        if not self._ready:
                            return None
                        message = self._ready.popleft()
                        message["attempts"] += 1
                        self._in_flight[message["id"]] = (message, time.time() + self.visibility_timeout)
                        return {"id": message["id"], "payload": message["payload"]}

                def ack(self, message_id):
                    with self._lock:
                        self._in_flight.pop(message_id, None)

                @property
                def dead_letter_queue(self):
                    with self._lock:
                        self._requeue_expired_locked()
                        return list(self._dlq)
        """.trimIndent()

        val STUB_QUEUE = """
            class Queue:
                def __init__(self, visibility_timeout=5.0, max_retries=3):
                    raise NotImplementedError
                def enqueue(self, payload):
                    raise NotImplementedError
                def dequeue(self):
                    raise NotImplementedError
                def ack(self, message_id):
                    raise NotImplementedError
                @property
                def dead_letter_queue(self):
                    raise NotImplementedError
        """.trimIndent()
    }
}
