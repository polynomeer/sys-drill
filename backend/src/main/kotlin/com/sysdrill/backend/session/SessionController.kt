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
    /** Drill Map 난이도 선행 추천 슬라이스 — `resolveScenarioMeta`가 이미 조회하는 ContentItem에서 그대로 가져옴. */
    val difficulty: String? = null,
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
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(session, userId))
    }

    /** PLAN.md step 31 — dashboard's "최근 진행" panel; used to be `GET /users/{userId}/sessions`. */
    @GetMapping
    fun list(@AuthenticatedUserId userId: UUID): List<SessionSummaryResponse> =
        sessionRepository.findByUserIdOrderByStartedAtDesc(userId).map { session ->
            val meta = resolveScenarioMeta(session.scenarioVersionId)
            SessionSummaryResponse(
                id = session.id!!,
                status = session.status,
                scenarioTitle = meta.title,
                startedAt = session.startedAt,
                completedAt = session.completedAt,
                difficulty = meta.difficulty,
            )
        }

    /** PLAN.md step 36 — a Game Day spectator (member of the session's scenario's organization) may also view this. */
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID, @AuthenticatedUserId userId: UUID): SessionResponse {
        sessionAccessGuard.requireOwnerOrSpectator(id, userId)
        return toResponse(sessionService.getSession(id), userId)
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
        return toResponse(sessionService.advance(id), userId)
    }

    /** Drill Map 난이도 선행 추천 슬라이스 — title과 difficulty를 한 번의 조회 체인으로 함께 반환(추가 DB 호출 없음). */
    private data class ScenarioMeta(val title: String, val difficulty: String?)

    private fun resolveScenarioMeta(scenarioVersionId: UUID): ScenarioMeta {
        val version = scenarioVersionRepository.findById(scenarioVersionId).orElse(null)
            ?: return ScenarioMeta("알 수 없는 시나리오", null)
        val scenario = scenarioRepository.findById(version.scenarioId).orElse(null)
            ?: return ScenarioMeta("알 수 없는 시나리오", null)
        val content = contentItemRepository.findById(scenario.contentId).orElse(null)
        return ScenarioMeta(content?.title ?: scenario.domain, content?.difficulty)
    }

    private fun toResponse(session: Session, callerId: UUID): SessionResponse =
        SessionResponse.from(
            session,
            sessionService.getCurrentStepPrompt(session),
            sessionService.getScenarioDomain(session),
            sessionService.getPhaseDeadline(session),
            callerId,
        )
}
