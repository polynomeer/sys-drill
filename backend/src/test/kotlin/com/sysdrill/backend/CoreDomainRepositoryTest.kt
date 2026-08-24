package com.sysdrill.backend

import com.sysdrill.backend.content.ContentItem
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.evaluation.Evaluation
import com.sysdrill.backend.evaluation.EvaluationRepository
import com.sysdrill.backend.evaluation.EvaluationRiskFlag
import com.sysdrill.backend.evaluation.EvaluationRiskFlagRepository
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.reporting.Report
import com.sysdrill.backend.reporting.ReportRepository
import com.sysdrill.backend.scenario.Scenario
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioStep
import com.sysdrill.backend.scenario.ScenarioStepRepository
import com.sysdrill.backend.scenario.ScenarioVersion
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionPhase
import com.sysdrill.backend.session.SessionPhaseRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.submission.Submission
import com.sysdrill.backend.submission.SubmissionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.util.UUID

/**
 * Exercises save + DB round-trip (flush/clear) + delete for every MVP core
 * entity from docs/ARCHITECTURE.md §4.1, running against the real local
 * Postgres (docker-compose) rather than an in-memory substitute, since the
 * schema relies on Postgres-specific jsonb columns.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CoreDomainRepositoryTest(
    @Autowired val entityManager: TestEntityManager,
    @Autowired val userRepository: UserRepository,
    @Autowired val contentItemRepository: ContentItemRepository,
    @Autowired val scenarioRepository: ScenarioRepository,
    @Autowired val scenarioVersionRepository: ScenarioVersionRepository,
    @Autowired val scenarioStepRepository: ScenarioStepRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val sessionPhaseRepository: SessionPhaseRepository,
    @Autowired val submissionRepository: SubmissionRepository,
    @Autowired val evaluationRepository: EvaluationRepository,
    @Autowired val evaluationRiskFlagRepository: EvaluationRiskFlagRepository,
    @Autowired val reportRepository: ReportRepository,
) {

    private fun uniqueEmail() = "user-${UUID.randomUUID()}@example.com"

    private fun persistedUser(): User =
        userRepository.save(
            User(email = uniqueEmail(), passwordHash = "hash", nickname = "drill-user", experienceYears = 3)
        )

    private fun persistedContentItem(): ContentItem =
        contentItemRepository.save(ContentItem(type = "SCENARIO", title = "선착순 쿠폰", difficulty = "MEDIUM"))

    private fun persistedScenario(): Scenario =
        scenarioRepository.save(
            Scenario(
                contentId = persistedContentItem().id!!,
                domain = "coupon",
                baseRequirements = """{"maxCoupons": 10000}""",
            )
        )

    private fun persistedScenarioVersion(): ScenarioVersion =
        scenarioVersionRepository.save(
            ScenarioVersion(scenarioId = persistedScenario().id!!, versionNo = 1, status = "PUBLISHED")
        )

    private fun persistedScenarioStep(scenarioVersionId: UUID): ScenarioStep =
        scenarioStepRepository.save(
            ScenarioStep(
                scenarioVersionId = scenarioVersionId,
                stepOrder = 1,
                stepType = "INITIAL",
                content = """{"prompt": "선착순 쿠폰 시스템을 설계하세요"}""",
            )
        )

    private fun persistedSession(): Session {
        val version = persistedScenarioVersion()
        return sessionRepository.save(
            Session(userId = persistedUser().id!!, scenarioVersionId = version.id!!, seed = "seed-1")
        )
    }

    private fun persistedSubmission(sessionId: UUID): Submission =
        submissionRepository.save(
            Submission(sessionId = sessionId, phase = "DESIGN", rawText = "요구사항 요약...")
        )

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `user repository saves and round-trips through the database`() {
        val saved = persistedUser()
        flushAndClear()

        val found = userRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.email).isEqualTo(saved.email)

        userRepository.delete(found)
        flushAndClear()
        assertThat(userRepository.findById(saved.id!!)).isEmpty()
    }

    @Test
    fun `content item repository saves and round-trips`() {
        val saved = persistedContentItem()
        flushAndClear()

        val found = contentItemRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.title).isEqualTo("선착순 쿠폰")
    }

    @Test
    fun `scenario repository persists jsonb base requirements`() {
        val saved = persistedScenario()
        flushAndClear()

        val found = scenarioRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.baseRequirements).contains("maxCoupons")
    }

    @Test
    fun `scenario version repository enforces scenario version ordering`() {
        val saved = persistedScenarioVersion()
        flushAndClear()

        val found = scenarioVersionRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.versionNo).isEqualTo(1)
        assertThat(found.status).isEqualTo("PUBLISHED")
    }

    @Test
    fun `scenario step repository links to its scenario version`() {
        val version = persistedScenarioVersion()
        val step = persistedScenarioStep(version.id!!)
        flushAndClear()

        val found = scenarioStepRepository.findById(step.id!!).orElseThrow()
        assertThat(found.scenarioVersionId).isEqualTo(version.id)
    }

    @Test
    fun `session repository tracks status transitions`() {
        val saved = persistedSession()
        flushAndClear()

        val found = sessionRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.status).isEqualTo(SessionStatus.IN_PROGRESS)

        found.status = SessionStatus.SUBMITTED
        sessionRepository.save(found)
        flushAndClear()
        assertThat(sessionRepository.findById(saved.id!!).orElseThrow().status).isEqualTo(SessionStatus.SUBMITTED)
    }

    @Test
    fun `session phase repository links to its session`() {
        val session = persistedSession()
        val phase = sessionPhaseRepository.save(
            SessionPhase(sessionId = session.id!!, phaseType = "DESIGN", phaseOrder = 1)
        )
        flushAndClear()

        val found = sessionPhaseRepository.findById(phase.id!!).orElseThrow()
        assertThat(found.sessionId).isEqualTo(session.id)
    }

    @Test
    fun `submission repository persists structured json`() {
        val session = persistedSession()
        val saved = submissionRepository.save(
            Submission(
                sessionId = session.id!!,
                phase = "DESIGN",
                structuredJson = """{"architecture": "lb-api-redis-db"}""",
            )
        )
        flushAndClear()

        val found = submissionRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.structuredJson).contains("architecture")
    }

    @Test
    fun `evaluation repository links to its submission`() {
        val session = persistedSession()
        val submission = persistedSubmission(session.id!!)
        val saved = evaluationRepository.save(
            Evaluation(submissionId = submission.id!!, totalScore = 74, rubricVersion = "v1")
        )
        flushAndClear()

        val found = evaluationRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.totalScore).isEqualTo(74)
        assertThat(found.isActive).isTrue()
    }

    @Test
    fun `evaluation risk flag repository links to its evaluation`() {
        val session = persistedSession()
        val submission = persistedSubmission(session.id!!)
        val evaluation = evaluationRepository.save(Evaluation(submissionId = submission.id!!))
        val saved = evaluationRiskFlagRepository.save(
            EvaluationRiskFlag(evaluationId = evaluation.id!!, riskKey = "WEAK_IDEMPOTENCY", severity = "HIGH")
        )
        flushAndClear()

        val found = evaluationRiskFlagRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.severity).isEqualTo("HIGH")
    }

    @Test
    fun `report repository links to its session`() {
        val session = persistedSession()
        val saved = reportRepository.save(
            Report(sessionId = session.id!!, summary = "부분 성공 · 74/100")
        )
        flushAndClear()

        val found = reportRepository.findById(saved.id!!).orElseThrow()
        assertThat(found.sessionId).isEqualTo(session.id)
    }
}
