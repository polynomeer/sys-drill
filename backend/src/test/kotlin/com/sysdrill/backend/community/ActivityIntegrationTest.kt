package com.sysdrill.backend.community

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.organization.Organization
import com.sysdrill.backend.organization.OrganizationAssessment
import com.sysdrill.backend.organization.OrganizationAssessmentRepository
import com.sysdrill.backend.organization.OrganizationRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.support.COUPON_SCENARIO_ID
import com.sysdrill.backend.support.bearerHeader
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.8 — recent completers and the profile
 * timeline show completions only, and never for users who hid themselves
 * from rankings or for hiring-assessment sessions.
 *
 * Sessions are marked COMPLETED directly (not through three graded phases)
 * since grading isn't what's under test. Completion times are set slightly
 * in the future so these rows are the newest in a shared test database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActivityIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
    @Autowired val sessionRepository: SessionRepository,
    @Autowired val organizationRepository: OrganizationRepository,
    @Autowired val assessmentRepository: OrganizationAssessmentRepository,
) {

    private fun user(prefix: String, optOut: Boolean = false): Pair<UUID, String> {
        val nickname = "$prefix-${UUID.randomUUID().toString().take(8)}"
        val saved = userRepository.save(
            User(email = "$nickname@example.com", passwordHash = "hash", nickname = nickname).apply { rankingOptOut = optOut }
        )
        return saved.id!! to nickname
    }

    private fun completedSession(userId: UUID, completedAt: Instant): UUID {
        val sessionId = mockMvc.startSession(userId)
        val session = sessionRepository.findById(sessionId).orElseThrow()
        session.status = SessionStatus.COMPLETED
        session.completedAt = completedAt
        sessionRepository.save(session)
        return sessionId
    }

    private fun recentNicknames(viewerId: UUID): List<String> {
        val body = mockMvc.perform(
            get("/community/scenarios/$COUPON_SCENARIO_ID/recent-completions").header("Authorization", bearerHeader(viewerId))
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonPath.read(body, "$[*].nickname")
    }

    @Test
    fun `recent completions list visible completers only`() {
        val soon = Instant.now().plus(Duration.ofHours(1))
        val (visibleId, visible) = user("visible")
        val (hiddenId, hidden) = user("hidden", optOut = true)
        val (_, inProgress) = user("inprogress").also { mockMvc.startSession(it.first) }
        val (candidateId, candidate) = user("candidate")

        completedSession(visibleId, soon)
        completedSession(hiddenId, soon.plusSeconds(1))
        val assessmentSession = completedSession(candidateId, soon.plusSeconds(2))
        val org = organizationRepository.save(Organization(name = "hiring-${UUID.randomUUID()}", createdBy = visibleId))
        assessmentRepository.save(
            OrganizationAssessment(
                organizationId = org.id!!,
                scenarioId = COUPON_SCENARIO_ID,
                candidateEmail = "$candidate@example.com",
                token = UUID.randomUUID().toString(),
                invitedBy = visibleId,
                resultSessionId = assessmentSession,
                expiresAt = soon.plus(Duration.ofDays(7)),
            )
        )

        val names = recentNicknames(visibleId)
        assertThat(names).contains(visible)
        assertThat(names).doesNotContain(hidden, inProgress, candidate)
    }

    @Test
    fun `the activity timeline shows completions without scores and hides opted-out users`() {
        val (userId, _) = user("timeline")
        val (optedOutId, _) = user("timeline-hidden", optOut = true)
        completedSession(userId, Instant.now())
        completedSession(optedOutId, Instant.now())
        mockMvc.startSession(userId) // in progress — must not appear

        val body = mockMvc.perform(get("/community/users/$userId/activity").header("Authorization", bearerHeader(optedOutId)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<Boolean>(body, "$.hidden")).isFalse()
        assertThat(JsonPath.read<List<String>>(body, "$.entries[*].domain")).containsExactly("coupon")
        assertThat(body).doesNotContain("score")

        val hiddenBody = mockMvc.perform(get("/community/users/$optedOutId/activity").header("Authorization", bearerHeader(userId)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<Boolean>(hiddenBody, "$.hidden")).isTrue()
        assertThat(JsonPath.read<List<Any>>(hiddenBody, "$.entries")).isEmpty()
    }

    @Test
    fun `both endpoints require a signed-in viewer and 404 unknown ids`() {
        mockMvc.perform(get("/community/scenarios/$COUPON_SCENARIO_ID/recent-completions")).andExpect(status().isUnauthorized)
        val (viewerId, _) = user("viewer")
        mockMvc.perform(get("/community/scenarios/${UUID.randomUUID()}/recent-completions").header("Authorization", bearerHeader(viewerId)))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/community/users/${UUID.randomUUID()}/activity").header("Authorization", bearerHeader(viewerId)))
            .andExpect(status().isNotFound)
    }
}
