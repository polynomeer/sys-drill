package com.sysdrill.backend.learning

import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * PLAN.md Round E18 (docs/LEARNING_EXPANSION_PLAN.md L7) — the four levels. Pure: built
 * from [CompletedRun]s, so the rules are pinned without running sessions.
 */
class ConceptMasteryTest {
    private val service = ConceptMasteryService(
        sessionRepository = org.mockito.Mockito.mock(com.sysdrill.backend.session.SessionRepository::class.java),
        sessionService = org.mockito.Mockito.mock(com.sysdrill.backend.session.SessionService::class.java),
        missionService = org.mockito.Mockito.mock(com.sysdrill.backend.mission.MissionService::class.java),
        submissionRepository = org.mockito.Mockito.mock(com.sysdrill.backend.submission.SubmissionRepository::class.java),
        evaluationRepository = org.mockito.Mockito.mock(com.sysdrill.backend.evaluation.EvaluationRepository::class.java),
        riskFlagRepository = org.mockito.Mockito.mock(com.sysdrill.backend.evaluation.EvaluationRiskFlagRepository::class.java),
    )
    private val idempotency = LearningConcept(riskKey = "MISSING_IDEMPOTENCY", relatedDomains = listOf("coupon", "payment"))

    private fun run(minutesAgo: Long, domain: String, variant: String?, vararg flagged: String) = CompletedRun(
        Session(id = UUID.randomUUID(), userId = UUID.randomUUID(), scenarioVersionId = UUID.randomUUID(), status = SessionStatus.COMPLETED)
            .apply { startedAt = Instant.now().minusSeconds(minutesAgo * 60) },
        domain,
        "$domain:${variant ?: "base"}",
        flagged.toSet(),
    )

    @Test
    fun `no related run is not started, and an unrelated domain doesn't count`() {
        assertThat(service.masteryOf(idempotency, listOf(run(1, "notification", "a"))).level).isEqualTo(MasteryLevel.NOT_STARTED)
    }

    @Test
    fun `flagged in the latest related run is weak even after clean runs before`() {
        val history = listOf(run(1, "coupon", "a", "MISSING_IDEMPOTENCY"), run(10, "coupon", "b"), run(20, "payment", null))
        val mastery = service.masteryOf(idempotency, history)
        assertThat(mastery.level).isEqualTo(MasteryLevel.WEAK)
        assertThat(mastery.cleanVariants).isEqualTo(2)
    }

    @Test
    fun `one clean variant is practiced however many times it is repeated`() {
        val history = listOf(run(1, "coupon", "a"), run(5, "coupon", "a"), run(9, "coupon", "a"))
        assertThat(service.masteryOf(idempotency, history).level).isEqualTo(MasteryLevel.PRACTICED)
    }

    @Test
    fun `clean on two different variants is confident — across domains too`() {
        assertThat(service.masteryOf(idempotency, listOf(run(1, "coupon", "a"), run(5, "coupon", "b"))).level).isEqualTo(MasteryLevel.CONFIDENT)
        assertThat(service.masteryOf(idempotency, listOf(run(1, "payment", null), run(5, "coupon", null))).level).isEqualTo(MasteryLevel.CONFIDENT)
    }
}
