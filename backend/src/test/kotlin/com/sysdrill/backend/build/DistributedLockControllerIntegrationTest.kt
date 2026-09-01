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
 * Drives the real async Build pipeline for the "distributed-lock" challenge
 * (PLAN.md step 15), mirroring the coverage established for
 * "rate-limiter"/"queue"/"circuit-breaker".
 */
@SpringBootTest
@AutoConfigureMockMvc
class DistributedLockControllerIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "distlock-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun submit(sourceCode: String): UUID =
        mockMvc.submitBuildChallenge(objectMapper, "distributed-lock", userId, sourceCode)

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
    fun `a correct distributed lock implementation passes all four stages`() {
        val submissionId = submit(CORRECT_DISTRIBUTED_LOCK)
        val response = awaitCompleted(submissionId)

        assertThat(JsonPath.read<String>(response, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(response, "$.score")).isEqualTo(4)
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).allMatch { it == "PASSED" }
    }

    @Test
    fun `an unimplemented stub fails every stage`() {
        val submissionId = submit(STUB_DISTRIBUTED_LOCK)
        val response = awaitCompleted(submissionId)

        assertThat(JsonPath.read<String>(response, "$.status")).isEqualTo("COMPLETED")
        assertThat(JsonPath.read<Int>(response, "$.score")).isEqualTo(0)
        assertThat(JsonPath.read<List<String>>(response, "$.stages[*].status")).allMatch { it == "FAILED" }
        assertThat(JsonPath.read<String>(response, "$.stages[0].feedback")).contains("not implemented")
    }

    private companion object {
        // Mirrors challenges/distributed-lock/distributed_lock.py's reference
        // solution — verified against the real sandbox before being written
        // into the seeded stage tests (see PLAN.md step 15 notes).
        val CORRECT_DISTRIBUTED_LOCK = """
            import threading
            import time

            class LockStore:
                def __init__(self):
                    self._lock = threading.Lock()
                    self._data = {}
                    self._next_token = 1

                def try_acquire(self, key, owner_id, lease_seconds):
                    with self._lock:
                        entry = self._data.get(key)
                        now = time.time()
                        if entry is not None and entry["expires_at"] > now:
                            return None
                        token = self._next_token
                        self._next_token += 1
                        self._data[key] = {"owner": owner_id, "expires_at": now + lease_seconds, "token": token}
                        return token

                def try_release(self, key, owner_id, token):
                    with self._lock:
                        entry = self._data.get(key)
                        if entry is None:
                            return False
                        if entry["owner"] != owner_id or entry["token"] != token:
                            return False
                        del self._data[key]
                        return True

                def is_locked(self, key):
                    with self._lock:
                        entry = self._data.get(key)
                        if entry is None:
                            return False
                        return entry["expires_at"] > time.time()

            class DistributedLock:
                def __init__(self, key, store=None, lease_seconds=5.0):
                    self.key = key
                    self.store = store if store is not None else LockStore()
                    self.lease_seconds = lease_seconds

                def acquire(self, owner_id):
                    return self.store.try_acquire(self.key, owner_id, self.lease_seconds)

                def release(self, owner_id, fencing_token):
                    return self.store.try_release(self.key, owner_id, fencing_token)

                def is_locked(self):
                    return self.store.is_locked(self.key)
        """.trimIndent()

        val STUB_DISTRIBUTED_LOCK = """
            class LockStore:
                def __init__(self):
                    raise NotImplementedError

            class DistributedLock:
                def __init__(self, key, store=None, lease_seconds=5.0):
                    raise NotImplementedError
                def acquire(self, owner_id):
                    raise NotImplementedError
                def release(self, owner_id, fencing_token):
                    raise NotImplementedError
                def is_locked(self):
                    raise NotImplementedError
        """.trimIndent()
    }
}
