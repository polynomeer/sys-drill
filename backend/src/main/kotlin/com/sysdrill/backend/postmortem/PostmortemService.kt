package com.sysdrill.backend.postmortem

import com.sysdrill.backend.common.readStringList
import com.sysdrill.backend.common.web.ConflictException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import com.sysdrill.backend.simulation.SimulationService
import com.sysdrill.backend.simulation.SystemStateResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID

@Service
class PostmortemService(
    private val sessionRepository: SessionRepository,
    private val simulationService: SimulationService,
    private val postmortemRepository: PostmortemRepository,
    private val objectMapper: ObjectMapper,
) {

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
        val saved = postmortemRepository.findBySessionId(sessionId)

        return PostmortemResponse(
            sessionId = sessionId,
            saved = saved != null,
            mttdSeconds = incidentStart?.let { start -> actions.firstOrNull()?.let { Duration.between(start.appliedAt, it.appliedAt).seconds } },
            mttrSeconds = incidentStart?.let { start -> actions.lastOrNull()?.let { Duration.between(start.appliedAt, it.appliedAt).seconds } },
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
