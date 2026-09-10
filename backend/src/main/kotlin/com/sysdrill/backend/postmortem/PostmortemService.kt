package com.sysdrill.backend.postmortem

import com.sysdrill.backend.common.readStringList
import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.identity.TrendDirection
import com.sysdrill.backend.identity.trendDirection
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionService
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.simulation.SimulationService
import com.sysdrill.backend.simulation.SystemStateResponse
import com.sysdrill.backend.simulation.TimelineStep
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID

@Service
class PostmortemService(
    private val sessionRepository: SessionRepository,
    private val sessionService: SessionService,
    private val simulationService: SimulationService,
    private val postmortemRepository: PostmortemRepository,
    private val objectMapper: ObjectMapper,
) {

    /** MTTD = incident start → first action; MTTR = incident start → last action. Null if no incident/no actions yet. */
    private fun mttdMttr(timeline: List<TimelineStep>): Pair<Long?, Long?> {
        val incidentStart = timeline.firstOrNull() ?: return null to null
        val actions = timeline.drop(1)
        val mttd = actions.firstOrNull()?.let { Duration.between(incidentStart.appliedAt, it.appliedAt).seconds }
        val mttr = actions.lastOrNull()?.let { Duration.between(incidentStart.appliedAt, it.appliedAt).seconds }
        return mttd to mttr
    }

    /**
     * [PostmortemResponse.mttdSeconds]/[PostmortemResponse.mttrSeconds]/[PostmortemResponse.actionsTimeline]/
     * before-after metrics are always recomputed from [SimulationService.getTimeline] (ADR-0011 lineage,
     * PLAN.md step 25) — never persisted. Only the user-authored narrative fields live in [Postmortem].
     */
    fun get(sessionId: UUID): PostmortemResponse {
        if (!sessionRepository.existsById(sessionId)) {
            throw NotFoundException("Session not found: $sessionId")
        }
        val timeline = simulationService.getTimeline(sessionId)
        val incidentStart = timeline.firstOrNull()
        val actions = timeline.drop(1)
        val (mttdSeconds, mttrSeconds) = mttdMttr(timeline)
        val saved = postmortemRepository.findBySessionId(sessionId)

        return PostmortemResponse(
            sessionId = sessionId,
            saved = saved != null,
            mttdSeconds = mttdSeconds,
            mttrSeconds = mttrSeconds,
            actionsTimeline = incidentStart?.let { start ->
                actions.map {
                    PostmortemActionSummary(
                        actionType = it.actionType!!,
                        label = it.label,
                        elapsedSeconds = Duration.between(start.appliedAt, it.appliedAt).seconds,
                    )
                }
            } ?: emptyList(),
            metricsBefore = incidentStart?.let { SystemStateResponse.from(it.systemState) },
            metricsAfter = (actions.lastOrNull() ?: incidentStart)?.let { SystemStateResponse.from(it.systemState) },
            rootCause = saved?.rootCause,
            mitigationActions = objectMapper.readStringList(saved?.mitigationActions),
            rootFixActions = objectMapper.readStringList(saved?.rootFixActions),
            preventionItems = objectMapper.readStringList(saved?.preventionItems),
            updatedAt = saved?.updatedAt,
        )
    }

    /**
     * Phase 3-C (docs/DRILLS_SIMULATION_VISION.md §6) — cross-session MTTD/MTTR aggregation, computed at read
     * time from the same [SimulationService.getTimeline] source [get] uses (ADR-0011 lineage): nothing new is
     * persisted, this just folds every one of the user's sessions that actually reached an incident.
     */
    fun getSummary(userId: UUID): PostmortemSummaryResponse {
        data class IncidentSample(val domain: String, val mttdSeconds: Long?, val mttrSeconds: Long?)

        // findByUserIdOrderByStartedAtDesc is newest-first; kept as-is for byDomain/averages (order-independent),
        // but reversed into `chronological` for trend() below, which compares a recent window against a prior
        // one and needs oldest-to-newest order to do that correctly.
        val samples = sessionRepository.findByUserIdOrderByStartedAtDesc(userId).mapNotNull { session: Session ->
            val timeline = simulationService.getTimeline(session.id!!)
            if (timeline.isEmpty()) return@mapNotNull null
            val (mttd, mttr) = mttdMttr(timeline)
            IncidentSample(domain = sessionService.getScenarioDomain(session), mttdSeconds = mttd, mttrSeconds = mttr)
        }
        val chronological = samples.asReversed()

        fun avg(values: List<Long>): Long? = if (values.isEmpty()) null else values.average().toLong()

        // trendDirection() assumes higher is better (it was written for 0-100 scores); MTTD/MTTR are the
        // opposite (lower is better), so the seconds are negated before handing them to the same function —
        // a shrinking MTTD/MTTR then reads as a growing negated sequence, which trendDirection calls IMPROVING.
        fun trend(values: List<Long>): TrendDirection = trendDirection(values.map { -it.toInt() })

        return PostmortemSummaryResponse(
            totalIncidents = samples.size,
            avgMttdSeconds = avg(samples.mapNotNull { it.mttdSeconds }),
            avgMttrSeconds = avg(samples.mapNotNull { it.mttrSeconds }),
            mttdTrend = trend(chronological.mapNotNull { it.mttdSeconds }),
            mttrTrend = trend(chronological.mapNotNull { it.mttrSeconds }),
            byDomain = samples.groupBy { it.domain }.map { (domain, domainSamples) ->
                PostmortemDomainSummary(
                    domain = domain,
                    incidentCount = domainSamples.size,
                    avgMttdSeconds = avg(domainSamples.mapNotNull { it.mttdSeconds }),
                    avgMttrSeconds = avg(domainSamples.mapNotNull { it.mttrSeconds }),
                )
            },
        )
    }

    @Transactional
    fun save(sessionId: UUID, request: SavePostmortemRequest): PostmortemResponse {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { NotFoundException("Session not found: $sessionId") }
        if (session.status != SessionStatus.COMPLETED) {
            throw ConflictException("Session $sessionId is not completed yet — postmortem can only be saved after the session finishes")
        }

        val entity = postmortemRepository.findBySessionId(sessionId)
            ?: Postmortem(sessionId = sessionId, rootCause = request.rootCause)
        entity.rootCause = request.rootCause
        entity.mitigationActions = objectMapper.writeValueAsString(request.mitigationActions)
        entity.rootFixActions = objectMapper.writeValueAsString(request.rootFixActions)
        entity.preventionItems = objectMapper.writeValueAsString(request.preventionItems)
        postmortemRepository.save(entity)

        return get(sessionId)
    }
}
