package com.sysdrill.backend.session

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import jakarta.validation.Valid
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SessionSummaryResponse(
    val id: UUID,
    val status: SessionStatus,
    val scenarioTitle: String,
    val startedAt: Instant,
    val completedAt: Instant?,
)

@RestController
@RequestMapping("/sessions")
class SessionController(
    private val sessionService: SessionService,
    private val sessionAccessGuard: SessionAccessGuard,
    private val sessionRepository: SessionRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioRepository: ScenarioRepository,
    private val contentItemRepository: ContentItemRepository,
) {

    /** PLAN.md step 30 — @AuthenticatedUserId, not a client-supplied userId in the body: this is the one write endpoint that step protected first (see AuthWebConfig). */
    @PostMapping
    fun start(
        @AuthenticatedUserId userId: UUID,
        @Valid @RequestBody request: StartSessionRequest,
    ): ResponseEntity<SessionResponse> {
        val session = sessionService.startSession(
            userId, request.scenarioId, request.buildSubmissionId, request.seed, request.interviewMode,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(session))
    }

    /** PLAN.md step 31 — dashboard's "최근 진행" panel; used to be `GET /users/{userId}/sessions`. */
    @GetMapping
    fun list(@AuthenticatedUserId userId: UUID): List<SessionSummaryResponse> =
        sessionRepository.findByUserIdOrderByStartedAtDesc(userId).map { session ->
            SessionSummaryResponse(
                id = session.id!!,
                status = session.status,
                scenarioTitle = resolveScenarioTitle(session.scenarioVersionId),
                startedAt = session.startedAt,
                completedAt = session.completedAt,
            )
        }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID, @AuthenticatedUserId userId: UUID): SessionResponse {
        sessionAccessGuard.requireOwner(id, userId)
        return toResponse(sessionService.getSession(id))
    }

    @PostMapping("/{id}/submissions")
    fun submit(
        @PathVariable id: UUID,
        @AuthenticatedUserId userId: UUID,
        @RequestBody request: SubmitAnswerRequest,
    ): ResponseEntity<SubmissionResponse> {
        sessionAccessGuard.requireOwner(id, userId)
        val submission = sessionService.submit(id, request.rawText, request.structuredJson, request.clientRequestId)
        return ResponseEntity.status(HttpStatus.CREATED).body(SubmissionResponse.from(submission))
    }

    @PostMapping("/{id}/advance")
    fun advance(@PathVariable id: UUID, @AuthenticatedUserId userId: UUID): SessionResponse {
        sessionAccessGuard.requireOwner(id, userId)
        return toResponse(sessionService.advance(id))
    }

    private fun resolveScenarioTitle(scenarioVersionId: UUID): String {
        val version = scenarioVersionRepository.findById(scenarioVersionId).orElse(null) ?: return "알 수 없는 시나리오"
        val scenario = scenarioRepository.findById(version.scenarioId).orElse(null) ?: return "알 수 없는 시나리오"
        val content = contentItemRepository.findById(scenario.contentId).orElse(null)
        return content?.title ?: scenario.domain
    }

    private fun toResponse(session: Session): SessionResponse =
        SessionResponse.from(
            session,
            sessionService.getCurrentStepPrompt(session),
            sessionService.getScenarioDomain(session),
            sessionService.getPhaseDeadline(session),
        )
}
