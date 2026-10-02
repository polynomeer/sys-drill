package com.sysdrill.backend.mission

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.scenario.ScenarioStepRepository
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionAccessGuard
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.simulation.SimulationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** INCIDENT step `content.changeReview` — the release's change list, which one actually broke things, and why. */
data class ChangeReviewContent(val changes: List<Assumption> = emptyList(), val culpritId: String? = null, val explanation: String? = null)

data class ChangeReviewResponse(
    /** False when the scenario's incident has no change list — nothing is shown. */
    val available: Boolean,
    val changes: List<Assumption> = emptyList(),
    val pick: String? = null,
    /** The incident has started: no more picking. The real cause comes once recovery is declared or the session completes. */
    val locked: Boolean = false,
    val culpritId: String? = null,
    val explanation: String? = null,
)

data class ChangeReviewPick(val pick: String)

/**
 * docs/DRILLS_EXPANSION_PLAN.md M9 (PLAN.md Round E30) — "which change in this release is riskiest?",
 * asked right before the incident, then held against the real cause once it starts. Generic over
 * scenarios: any INCIDENT step with a `changeReview` gets it (the deployment Drill is the first).
 */
@Service
class ChangeReviewService(
    private val sessionRepository: SessionRepository,
    private val scenarioStepRepository: ScenarioStepRepository,
    private val missionService: MissionService,
    private val simulationService: SimulationService,
    private val mapper: ObjectMapper,
) {
    fun content(session: Session): ChangeReviewContent? {
        val step = scenarioStepRepository.findByScenarioVersionIdOrderByStepOrder(session.scenarioVersionId).firstOrNull { it.stepType == "INCIDENT" } ?: return null
        val tree = step.content?.let { runCatching { mapper.readTree(it).get("changeReview") }.getOrNull() } ?: return null
        return runCatching {
            mapper.readerFor(ChangeReviewContent::class.java).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue<ChangeReviewContent>(tree)
        }.getOrNull()?.takeIf { it.changes.isNotEmpty() }
    }

    fun get(sessionId: UUID): ChangeReviewResponse {
        val session = requireSession(sessionId)
        val content = content(session) ?: return ChangeReviewResponse(available = false)
        val locked = simulationService.getTimeline(sessionId).isNotEmpty()
        // The real cause stays hidden while the incident is being worked — it's what the learner has to find.
        val revealed = locked && (session.completedAt != null || simulationService.getSeries(sessionId).resolvedAt != null)
        return ChangeReviewResponse(
            available = true,
            changes = content.changes,
            pick = missionService.state(session).changeReviewPick,
            locked = locked,
            culpritId = content.culpritId.takeIf { revealed },
            explanation = content.explanation.takeIf { revealed },
        )
    }

    @Transactional
    fun pick(sessionId: UUID, pick: String): ChangeReviewResponse {
        val session = requireSession(sessionId)
        val content = content(session) ?: throw NotFoundException("No change review for this scenario")
        if (content.changes.none { it.id == pick }) throw BadRequestException("목록에 없는 변경입니다")
        if (simulationService.getTimeline(sessionId).isNotEmpty()) throw ConflictException("인시던트가 이미 시작되었습니다")
        missionService.save(session, missionService.state(session).copy(changeReviewPick = pick))
        return get(sessionId)
    }

    /** INCIDENT evaluation — whether the risk read before the incident matched what broke. */
    fun promptSection(session: Session): String? {
        val content = content(session) ?: return null
        val pick = missionService.state(session).changeReviewPick
        val culprit = content.changes.firstOrNull { it.id == content.culpritId } ?: return null
        val picked = content.changes.firstOrNull { it.id == pick }
        return buildString {
            appendLine("## 배포 전 변경 검토")
            appendLine("- 사용자가 가장 위험하다고 고른 변경: ${picked?.text ?: "(고르지 않음)"}")
            appendLine("- 실제 원인: ${culprit.text}" + if (picked?.id == culprit.id) " — 일치" else "")
            appendLine("회고에서 원인을 변경 목록과 연결했는지, 고른 변경이 틀렸다면 무엇을 놓쳤는지 짚어 주세요.")
        }
    }

    private fun requireSession(sessionId: UUID): Session =
        sessionRepository.findById(sessionId).orElseThrow { NotFoundException("Session not found: $sessionId") }
}

@RestController
class ChangeReviewController(
    private val service: ChangeReviewService,
    private val sessionAccessGuard: SessionAccessGuard,
) {
    @GetMapping("/sessions/{sessionId}/change-review")
    fun get(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): ChangeReviewResponse {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return service.get(sessionId)
    }

    @PutMapping("/sessions/{sessionId}/change-review")
    fun pick(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID, @RequestBody body: ChangeReviewPick): ChangeReviewResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return service.pick(sessionId, body.pick)
    }
}
