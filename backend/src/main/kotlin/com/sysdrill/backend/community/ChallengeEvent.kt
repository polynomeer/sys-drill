package com.sysdrill.backend.community

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.auth.PlatformAccessGuard
import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.organization.AssessmentSessions
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.simulation.SimulationService
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "challenge_events")
class ChallengeEvent(
    @Id @GeneratedValue(strategy = GenerationType.UUID) var id: UUID? = null,
    @Column(nullable = false) var title: String,
    @Column(name = "scenario_id", nullable = false) var scenarioId: UUID,
    @Column(name = "starts_at", nullable = false) var startsAt: Instant,
    @Column(name = "ends_at", nullable = false) var endsAt: Instant,
    @Column(name = "created_by", nullable = false) var createdBy: UUID,
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) var createdAt: Instant? = null,
)

interface ChallengeEventRepository : JpaRepository<ChallengeEvent, UUID> {
    fun findAllByOrderByStartsAtDesc(): List<ChallengeEvent>
}

data class CreateChallengeEvent(
    @field:NotBlank @field:Size(max = 120) val title: String,
    val scenarioId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class ChallengeEventSummary(
    val id: UUID,
    val title: String,
    val scenarioId: UUID,
    val scenarioTitle: String,
    val domain: String,
    val startsAt: Instant,
    val endsAt: Instant,
    /** UPCOMING / LIVE / ENDED at read time. */
    val phase: String,
    val participants: Int,
)

data class BoardEntry(
    val rank: Int,
    val nickname: String,
    val score: Int?,
    /** Incident start → "복구 선언" (M5); null when not declared or not fully recovered. */
    val resolvedSeconds: Long?,
    val mine: Boolean,
    /** Debrief only (after the end), and only when that run was shared publicly. */
    val writeupSessionId: UUID?,
)

data class ChallengeBoard(val event: ChallengeEventSummary, val entries: List<BoardEntry>, val debriefOpen: Boolean)

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C13 (PLAN.md Round E31, ADR-0050) — a time-boxed run of one official
 * scenario. Everyone's best run inside the window, ranked by score then time to a declared recovery
 * (M5 — a definition you can't game by clicking actions fast). Writeups open only after the end.
 */
@Service
class ChallengeEventService(
    private val repository: ChallengeEventRepository,
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val contentItemRepository: ContentItemRepository,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val assessmentSessions: AssessmentSessions,
    private val simulationService: SimulationService,
    private val writeupService: WriteupService,
) {
    fun create(adminId: UUID, request: CreateChallengeEvent): ChallengeEventSummary {
        val scenario = scenarioRepository.findById(request.scenarioId).orElseThrow { NotFoundException("Scenario not found: ${request.scenarioId}") }
        if (scenario.creatorUserId != null || scenario.organizationId != null) throw BadRequestException("챌린지는 공식 시나리오로만 열 수 있습니다")
        if (!request.endsAt.isAfter(request.startsAt)) throw BadRequestException("종료 시각은 시작 시각 뒤여야 합니다")
        val saved = repository.save(ChallengeEvent(title = request.title.trim(), scenarioId = scenario.id!!, startsAt = request.startsAt, endsAt = request.endsAt, createdBy = adminId))
        return summary(saved, Instant.now())
    }

    fun list(now: Instant = Instant.now()): List<ChallengeEventSummary> = repository.findAllByOrderByStartsAtDesc().map { summary(it, now) }

    fun board(eventId: UUID, viewerId: UUID, now: Instant = Instant.now()): ChallengeBoard {
        val event = repository.findById(eventId).orElseThrow { NotFoundException("Challenge not found: $eventId") }
        val ended = !now.isBefore(event.endsAt)
        val runs = participantRuns(event)
        val scores = writeupService.averageScores(runs.mapNotNull { it.id })
        // Best run per person: higher score first, then the faster declared full recovery.
        val best = runs.groupBy { it.userId }.mapValues { (_, sessions) ->
            sessions.map { s ->
                val recovery = simulationService.recovery(s.id!!, now)
                Triple(s, scores[s.id], recovery.resolvedSeconds?.takeIf { recovery.status == "RECOVERED" })
            }.sortedWith(compareByDescending<Triple<com.sysdrill.backend.session.Session, Int?, Long?>> { it.second ?: -1 }.thenBy { it.third ?: Long.MAX_VALUE }).first()
        }.values.sortedWith(compareByDescending<Triple<com.sysdrill.backend.session.Session, Int?, Long?>> { it.second ?: -1 }.thenBy { it.third ?: Long.MAX_VALUE })
        val nicknames = userRepository.findAllById(best.map { it.first.userId }).associate { it.id to it.nickname }
        return ChallengeBoard(
            event = summary(event, now),
            entries = best.mapIndexed { i, (session, score, resolved) ->
                BoardEntry(
                    rank = i + 1,
                    nickname = nicknames[session.userId] ?: "(알 수 없음)",
                    score = score,
                    resolvedSeconds = resolved,
                    mine = session.userId == viewerId,
                    writeupSessionId = session.id.takeIf { ended && session.visibility == "PUBLIC" && !session.sharedAnonymously },
                )
            },
            debriefOpen = ended,
        )
    }

    /** Completed runs started and finished inside the window — hiring assessments excluded (ADR-0043). */
    private fun participantRuns(event: ChallengeEvent): List<com.sysdrill.backend.session.Session> {
        val versionIds = scenarioVersionRepository.findByScenarioIdIn(listOf(event.scenarioId)).mapNotNull { it.id }
        val inWindow = sessionRepository.findByScenarioVersionIdInAndStatus(versionIds, SessionStatus.COMPLETED).filter {
            !it.startedAt.isBefore(event.startsAt) && it.completedAt != null && !it.completedAt!!.isAfter(event.endsAt)
        }
        val assessments = assessmentSessions.among(inWindow.mapNotNull { it.id })
        return inWindow.filterNot { it.id in assessments }
    }

    private fun summary(event: ChallengeEvent, now: Instant): ChallengeEventSummary {
        val scenario = scenarioRepository.findById(event.scenarioId).orElseThrow()
        val title = contentItemRepository.findById(scenario.contentId).map { it.title }.orElse(scenario.domain)
        return ChallengeEventSummary(
            id = event.id!!,
            title = event.title,
            scenarioId = scenario.id!!,
            scenarioTitle = title,
            domain = scenario.domain,
            startsAt = event.startsAt,
            endsAt = event.endsAt,
            phase = when {
                now.isBefore(event.startsAt) -> "UPCOMING"
                now.isBefore(event.endsAt) -> "LIVE"
                else -> "ENDED"
            },
            participants = participantRuns(event).map { it.userId }.distinct().size,
        )
    }
}

@RestController
class ChallengeEventController(
    private val service: ChallengeEventService,
    private val accessGuard: PlatformAccessGuard,
) {
    @GetMapping("/community/events")
    fun list(): List<ChallengeEventSummary> = service.list()

    @GetMapping("/community/events/{eventId}")
    fun board(@PathVariable eventId: UUID, @AuthenticatedUserId userId: UUID): ChallengeBoard = service.board(eventId, userId)

    @PostMapping("/admin/events")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticatedUserId userId: UUID, @Valid @RequestBody request: CreateChallengeEvent): ChallengeEventSummary {
        accessGuard.requirePlatformAdmin(userId)
        return service.create(userId, request)
    }
}
