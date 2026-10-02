package com.sysdrill.backend.simulation

import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.NotFoundException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ForkAction(val action: SimulationActionType, val at: Instant)

/**
 * ADR-0046 — a fork lives only here (Redis, 1h), never as a session or applied_actions row.
 *
 * Its clock: [virtualStart] is placed so that "now" at creation equals the source incident's
 * elapsed time at the fork step. The source's actions up to that step ([prefix]) keep their
 * relative times; the learner's new [actions] land on the real clock. With the deterministic
 * engine that's enough to sample the fork's series exactly like a live incident (ADR-0045).
 */
data class ForkState(
    val forkId: String,
    val sourceSessionId: UUID,
    val ownerUserId: UUID,
    val domain: String,
    val baseTraits: DesignTraits,
    val atStep: Int,
    val virtualStart: Instant,
    val prefix: List<ForkAction>,
    val actions: List<ForkAction> = emptyList(),
)

data class ForkResponse(
    val forkId: String,
    val sourceSessionId: UUID,
    val domain: String,
    val atStep: Int,
    val forkedAtSeconds: Long,
    val prefixActions: List<String>,
    val actions: List<String>,
    val state: SystemStateResponse,
)

/** docs/DRILLS_EXPANSION_PLAN.md M6 — "what if" against what actually happened, on projections both ways. */
data class ForkSide(
    val actions: List<String>,
    /** Seconds from the incident start until the series settles healthy for good, projecting the actions so far; null if not within the horizon. */
    val recoveredAtSeconds: Long?,
    /** Σ errorRate·dt over the horizon — seconds of full-outage equivalent. */
    val impactSeconds: Double,
    val finalErrorRate: Double,
    val finalBacklog: Long,
)

data class ForkComparison(val horizonSeconds: Long, val original: ForkSide, val fork: ForkSide)

@Service
class ForkService(
    private val redisTemplate: StringRedisTemplate,
    private val simulationService: SimulationService,
    private val objectMapper: ObjectMapper,
) {

    fun create(sourceSessionId: UUID, ownerUserId: UUID, atStep: Int, now: Instant = Instant.now()): ForkResponse {
        val source = simulationService.forkSource(sourceSessionId)
        if (source.engineMode != EngineMode.RULE_BASED.name) {
            throw BadRequestException("실제 인프라 세션은 포크할 수 없습니다 — 측정값이라 다시 계산할 수 없습니다(ADR-0016)")
        }
        val steps = source.timeline
        if (atStep !in steps.indices) throw BadRequestException("atStep must be between 0 and ${steps.size - 1}")
        val start = steps.first().appliedAt
        val elapsed = Duration.between(start, steps[atStep].appliedAt)
        val virtualStart = now.minus(elapsed)
        val prefix = steps.subList(1, atStep + 1).map { step ->
            ForkAction(SimulationActionType.valueOf(step.actionType!!), virtualStart.plus(Duration.between(start, step.appliedAt)))
        }
        val fork = ForkState(
            forkId = UUID.randomUUID().toString(),
            sourceSessionId = sourceSessionId,
            ownerUserId = ownerUserId,
            domain = source.domain,
            baseTraits = source.baseTraits,
            atStep = atStep,
            virtualStart = virtualStart,
            prefix = prefix,
        )
        save(fork)
        return response(fork, now)
    }

    fun get(forkId: String, userId: UUID, now: Instant = Instant.now()): ForkResponse = response(require(forkId, userId), now)

    fun apply(forkId: String, userId: UUID, action: SimulationActionType, now: Instant = Instant.now()): ForkResponse {
        val fork = require(forkId, userId)
        // Same domain check the live engine does (wrong-domain actions throw).
        runCatching {
            RuleBasedSimulationEngine.applyAction(SimulationSessionState(UUID(0, 0), fork.domain, true, fork.baseTraits), action)
        }.getOrElse { throw BadRequestException("$action does not apply to the ${fork.domain} incident") }
        val next = fork.copy(actions = fork.actions + ForkAction(action, now))
        save(next)
        return response(next, now)
    }

    fun series(forkId: String, userId: UUID, now: Instant = Instant.now()): SimulationSeries {
        val fork = require(forkId, userId)
        val to = minOf(now, fork.virtualStart.plus(HORIZON))
        val from = fork.virtualStart.minusSeconds(60)
        val step = Duration.ofSeconds(maxOf(5L, Math.ceilDiv(Duration.between(from, to).seconds, 120L)))
        val points = TelemetrySampler.sample(fork.domain, fork.baseTraits, fork.virtualStart, timed(fork), from, to, step)
        return SimulationSeries(EngineMode.RULE_BASED.name, fork.virtualStart, null, points.zip(TelemetrySampler.classify(points, fork.virtualStart)))
    }

    /**
     * Both sides projected over the same horizon from their own incident start: the original with
     * every action it took, the fork with the prefix plus what the learner has done so far.
     */
    fun compare(forkId: String, userId: UUID): ForkComparison {
        val fork = require(forkId, userId)
        val source = simulationService.forkSource(fork.sourceSessionId)
        val start = source.timeline.first().appliedAt
        val original = source.timeline.drop(1).map { TimedAction(it.appliedAt, SimulationActionType.valueOf(it.actionType!!)) }
        return ForkComparison(
            horizonSeconds = HORIZON.seconds,
            original = side(fork.domain, fork.baseTraits, start, original),
            fork = side(fork.domain, fork.baseTraits, fork.virtualStart, timed(fork)),
        )
    }

    private fun side(domain: String, traits: DesignTraits, start: Instant, actions: List<TimedAction>): ForkSide {
        val points = TelemetrySampler.sample(domain, traits, start, actions, start, start.plus(HORIZON), Duration.ofSeconds(5))
        val statuses = TelemetrySampler.classify(points, start)
        // The moment from which it stays healthy to the end of the horizon. "First RECOVERED point" isn't
        // enough: a fix applied at second 0 means the incident never shows, so it's HEALTHY throughout;
        // and the early ramp reads HEALTHY before turning critical.
        val settled = setOf(HealthStatus.HEALTHY, HealthStatus.RECOVERED)
        val lastBad = statuses.indexOfLast { it !in settled }
        val recovered = if (lastBad == statuses.lastIndex) null else points[lastBad + 1].at
        val impact = points.zipWithNext().sumOf { (a, b) -> a.state.errorRate * Duration.between(a.at, b.at).seconds }
        return ForkSide(
            actions = actions.map { it.action.name },
            recoveredAtSeconds = recovered?.let { Duration.between(start, it).seconds },
            impactSeconds = impact,
            finalErrorRate = points.last().state.errorRate,
            finalBacklog = points.last().backlog,
        )
    }

    private fun timed(fork: ForkState) = (fork.prefix + fork.actions).map { TimedAction(it.at, it.action) }

    private fun response(fork: ForkState, now: Instant): ForkResponse {
        val point = TelemetrySampler.sampleAt(fork.domain, fork.baseTraits, fork.virtualStart, timed(fork), listOf(now)).single()
        return ForkResponse(
            forkId = fork.forkId,
            sourceSessionId = fork.sourceSessionId,
            domain = fork.domain,
            atStep = fork.atStep,
            forkedAtSeconds = fork.prefix.lastOrNull()?.let { Duration.between(fork.virtualStart, it.at).seconds } ?: 0,
            prefixActions = fork.prefix.map { it.action.name },
            actions = fork.actions.map { it.action.name },
            state = SystemStateResponse.from(point.state),
        )
    }

    /** Forks belong to whoever created them; anyone else gets a 404 (like the session guard). */
    private fun require(forkId: String, userId: UUID): ForkState {
        val json = redisTemplate.opsForValue().get(key(forkId)) ?: throw NotFoundException("Fork not found or expired: $forkId")
        val fork = objectMapper.readerFor(ForkState::class.java).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue<ForkState>(json)
        if (fork.ownerUserId != userId) throw NotFoundException("Fork not found or expired: $forkId")
        return fork
    }

    private fun save(fork: ForkState) {
        redisTemplate.opsForValue().set(key(fork.forkId), objectMapper.writeValueAsString(fork), TTL)
    }

    private fun key(forkId: String) = "sysdrill:fork:$forkId"

    companion object {
        val TTL: Duration = Duration.ofHours(1)
        /** Comparison and fork series run up to 30 minutes after the (virtual) incident start, like the live series. */
        val HORIZON: Duration = Duration.ofMinutes(30)
    }
}
