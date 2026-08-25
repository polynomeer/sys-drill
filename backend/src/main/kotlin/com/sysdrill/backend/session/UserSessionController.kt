package com.sysdrill.backend.session

import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import java.time.Instant
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

data class SessionSummaryResponse(
    val id: UUID,
    val status: SessionStatus,
    val scenarioTitle: String,
    val startedAt: Instant,
    val completedAt: Instant?,
)

/** Dashboard's "최근 진행" panel (PLAN.md step 8) — no ARCHITECTURE.md precedent for this exact shape. */
@RestController
class UserSessionController(
    private val sessionRepository: SessionRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioRepository: ScenarioRepository,
    private val contentItemRepository: ContentItemRepository,
) {

    @GetMapping("/users/{userId}/sessions")
    fun list(@PathVariable userId: UUID): List<SessionSummaryResponse> =
        sessionRepository.findByUserIdOrderByStartedAtDesc(userId).map { session ->
            SessionSummaryResponse(
                id = session.id!!,
                status = session.status,
                scenarioTitle = resolveScenarioTitle(session.scenarioVersionId),
                startedAt = session.startedAt,
                completedAt = session.completedAt,
            )
        }

    private fun resolveScenarioTitle(scenarioVersionId: UUID): String {
        val version = scenarioVersionRepository.findById(scenarioVersionId).orElse(null) ?: return "알 수 없는 시나리오"
        val scenario = scenarioRepository.findById(version.scenarioId).orElse(null) ?: return "알 수 없는 시나리오"
        val content = contentItemRepository.findById(scenario.contentId).orElse(null)
        return content?.title ?: scenario.domain
    }
}
