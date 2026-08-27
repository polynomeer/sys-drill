package com.sysdrill.backend.simulation.realinfra

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.UUID

/** Exercises the real Redis sorted set (not mocked) — see PLAN.md step 22 notes. */
@SpringBootTest
class RealInfraSessionTrackerTest(
    @Autowired val tracker: RealInfraSessionTracker,
    @Autowired val redisTemplate: StringRedisTemplate,
) {

    @Test
    fun `a freshly touched session is not expired`() {
        val sessionId = UUID.randomUUID()
        tracker.touch(sessionId)

        val expired = tracker.findExpired(Duration.ofMinutes(30))

        assertThat(expired).doesNotContain(sessionId)
        tracker.forget(sessionId)
    }

    @Test
    fun `a session touched long ago is found as expired`() {
        val sessionId = UUID.randomUUID()
        val oldTimestamp = (System.currentTimeMillis() - Duration.ofHours(7).toMillis()).toDouble()
        redisTemplate.opsForZSet().add(TRACKER_KEY, sessionId.toString(), oldTimestamp)

        val expired = tracker.findExpired(Duration.ofHours(6))

        assertThat(expired).contains(sessionId)
        tracker.forget(sessionId)
    }

    @Test
    fun `forget removes the session from tracking`() {
        val sessionId = UUID.randomUUID()
        tracker.touch(sessionId)

        tracker.forget(sessionId)

        val expired = tracker.findExpired(Duration.ZERO)
        assertThat(expired).doesNotContain(sessionId)
    }

    private companion object {
        const val TRACKER_KEY = "sysdrill:simulation:realinfra:sessions"
    }
}
