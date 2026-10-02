package com.sysdrill.backend.mission

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.session.SessionAccessGuard
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.simulation.InvestigationService
import com.sysdrill.backend.simulation.RuleBasedSimulationEngine
import com.sysdrill.backend.simulation.SimulationService
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * docs/DRILLS_EXPANSION_PLAN.md M12 (PLAN.md Round E27) — one step of my runbook.
 * [type] OPEN_PANEL / INSPECT_NODE / QUERY_LOGS / OPEN_TRACE (the O0-b investigation kinds),
 * ACTION (a [com.sysdrill.backend.simulation.SimulationActionType] name) or NOTE (never matched).
 */
data class RunbookStep(val type: String, val target: String? = null, val text: String = "")

@Entity
@Table(name = "user_runbooks")
class UserRunbook(
    @Id @GeneratedValue(strategy = GenerationType.UUID) var id: UUID? = null,
    @Column(name = "user_id", nullable = false) var userId: UUID,
    @Column(nullable = false) var domain: String,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") var steps: List<Map<String, String?>> = emptyList(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
)

interface UserRunbookRepository : JpaRepository<UserRunbook, UUID> {
    fun findByUserIdAndDomain(userId: UUID, domain: String): UserRunbook?
}

data class RunbookResponse(val domain: String, val steps: List<RunbookStep>, val updatedAt: Instant?)

data class RunbookStepCheck(
    val index: Int,
    val step: RunbookStep,
    /** Null for a NOTE — free text isn't matched against anything. */
    val done: Boolean?,
    /** Seconds after the incident start when it was first done. */
    val atSeconds: Long?,
)

data class RunbookCheckResponse(val available: Boolean, val domain: String?, val steps: List<RunbookStepCheck>)

@Service
class RunbookService(
    private val repository: UserRunbookRepository,
    private val sessionRepository: SessionRepository,
    private val missionService: MissionService,
    private val simulationService: SimulationService,
    private val investigationService: InvestigationService,
) {
    fun get(userId: UUID, domain: String): RunbookResponse {
        requireDomain(domain)
        val runbook = repository.findByUserIdAndDomain(userId, domain)
        return RunbookResponse(domain, runbook?.steps.orEmpty().map(::toStep), runbook?.updatedAt)
    }

    @Transactional
    fun save(userId: UUID, domain: String, steps: List<RunbookStep>): RunbookResponse {
        requireDomain(domain)
        if (steps.size > MAX_STEPS) throw BadRequestException("Runbook은 최대 $MAX_STEPS 단계입니다")
        steps.forEach {
            if (it.type !in TYPES) throw BadRequestException("알 수 없는 단계 종류: ${it.type}")
            if (it.text.length > 300 || (it.target?.length ?: 0) > 200) throw BadRequestException("단계 내용이 너무 깁니다")
        }
        val runbook = repository.findByUserIdAndDomain(userId, domain) ?: UserRunbook(userId = userId, domain = domain)
        runbook.steps = steps.map { mapOf("type" to it.type, "target" to it.target?.trim()?.ifBlank { null }, "text" to it.text.trim()) }
        runbook.updatedAt = Instant.now()
        repository.save(runbook)
        return get(userId, domain)
    }

    /**
     * The session owner's runbook for this session's domain against what was actually looked at and
     * done during the incident (sandbox actions excluded, like everywhere since ADR-0046).
     */
    fun check(sessionId: UUID): RunbookCheckResponse {
        val session = sessionRepository.findById(sessionId).orElseThrow { NotFoundException("Session not found: $sessionId") }
        val domain = missionService.domainOf(session)
        val steps = repository.findByUserIdAndDomain(session.userId, domain)?.steps.orEmpty().map(::toStep)
        if (steps.isEmpty()) return RunbookCheckResponse(available = false, domain = domain, steps = emptyList())
        val timeline = simulationService.getTimeline(sessionId)
        val start = timeline.firstOrNull()?.appliedAt
            ?: return RunbookCheckResponse(true, domain, steps.mapIndexed { i, s -> RunbookStepCheck(i, s, if (s.type == "NOTE") null else false, null) })
        val looks = investigationService.list(sessionId).filter { !it.createdAt!!.isBefore(start) }
        val actions = timeline.drop(1)
        fun secs(at: Instant) = Duration.between(start, at).seconds
        return RunbookCheckResponse(true, domain, steps.mapIndexed { i, step ->
            val target = step.target?.lowercase().orEmpty()
            val at: Instant? = when (step.type) {
                "NOTE" -> null
                "ACTION" -> actions.firstOrNull { it.actionType == step.target }?.appliedAt
                else -> looks.firstOrNull { it.kind == step.type && (target.isEmpty() || it.target?.lowercase()?.contains(target) == true) }?.createdAt
            }
            RunbookStepCheck(i, step, if (step.type == "NOTE") null else at != null, at?.let(::secs))
        })
    }

    private fun toStep(m: Map<String, String?>) = RunbookStep(m["type"] ?: "NOTE", m["target"], m["text"].orEmpty())

    private fun requireDomain(domain: String) {
        if (domain !in RuleBasedSimulationEngine.KNOWN_DOMAINS) throw NotFoundException("Unknown domain: $domain")
    }

    private companion object {
        const val MAX_STEPS = 20
        val TYPES = setOf("OPEN_PANEL", "INSPECT_NODE", "QUERY_LOGS", "OPEN_TRACE", "ACTION", "NOTE")
    }
}

@RestController
class RunbookController(
    private val service: RunbookService,
    private val sessionAccessGuard: SessionAccessGuard,
) {
    @GetMapping("/me/runbooks/{domain}")
    fun get(@PathVariable domain: String, @AuthenticatedUserId userId: UUID): RunbookResponse = service.get(userId, domain)

    @PutMapping("/me/runbooks/{domain}")
    fun save(@PathVariable domain: String, @AuthenticatedUserId userId: UUID, @RequestBody steps: List<RunbookStep>): RunbookResponse =
        service.save(userId, domain, steps)

    /** Owner only — a runbook is the learner's own notes. */
    @GetMapping("/sessions/{sessionId}/runbook-check")
    fun check(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): RunbookCheckResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return service.check(sessionId)
    }
}
