package com.sysdrill.backend.evaluation

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * docs/COMMERCIALIZATION.md — EvaluationWorker moved from a single background
 * thread to a configurable pool (a local traffic benchmark showed the old
 * single thread backing the queue up to 4,500+ items under load). Doesn't
 * assert on `Thread.getAllStackTraces()` -- under the full suite, Spring's
 * per-configuration context cache keeps several differently-configured
 * ApplicationContexts (and their own EvaluationWorker thread pools) alive
 * side by side in one JVM, so a global thread-name count doesn't isolate
 * this test's own context and isn't a sound assertion (confirmed: it flaked
 * at both 5 and 15 threads across two runs, from other cached contexts'
 * pools, not this one). Instead this proves the thing that actually matters:
 * several distinct submissions processed under a raised worker-concurrency
 * all still complete correctly (see EvaluationWorker's own kdoc for why
 * concurrent processing is safe by design -- this is the black-box proof).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EvaluationWorkerConcurrencyTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val evaluationRepository: EvaluationRepository,
) {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun workerConcurrency(registry: DynamicPropertyRegistry) {
            registry.add("sysdrill.evaluation.worker-concurrency") { "3" }
        }
    }

    @Test
    fun `several distinct submissions processed concurrently all reach FEEDBACK_READY`() {
        val userId = userRepository.save(
            User(email = "concurrency-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!

        val submissionIds = (1..5).map { i ->
            val sessionId = mockMvc.startSession(userId)
            val response = mockMvc.perform(
                post("/sessions/$sessionId/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerHeader(userId))
                    .content("""{"rawText":"Load Balancer -> API $i -> Redis -> Postgres"}""")
            ).andExpect(status().isCreated).andReturn().response.contentAsString
            sessionId to UUID.fromString(JsonPath.read(response, "$.id"))
        }

        val deadline = Instant.now().plus(Duration.ofSeconds(20))
        for ((sessionId, _) in submissionIds) {
            while (Instant.now().isBefore(deadline) && sessionRepository.findById(sessionId).orElseThrow().status != SessionStatus.FEEDBACK_READY) {
                Thread.sleep(100)
            }
        }

        for ((sessionId, submissionId) in submissionIds) {
            assertThat(sessionRepository.findById(sessionId).orElseThrow().status).isEqualTo(SessionStatus.FEEDBACK_READY)
            assertThat(evaluationRepository.existsBySubmissionIdAndIsActiveTrue(submissionId)).isTrue()
        }
    }
}
