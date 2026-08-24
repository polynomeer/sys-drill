package com.sysdrill.backend.simulation

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.session.SessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SimulationService(
    private val sessionRepository: SessionRepository,
    private val stateStore: SimulationStateStore,
    private val appliedActionRepository: AppliedActionRepository,
) {

    /** Starts the "선착순 쿠폰" incident template (docs/PRD.md §8.1) for a session. */
    fun startIncident(sessionId: UUID): SystemState {
        requireSessionExists(sessionId)
        val state = SimulationSessionState(incidentActive = true, traits = DesignTraits())
        stateStore.save(sessionId, state)
        return SimulationEngine.computeState(state)
    }

    fun getState(sessionId: UUID): SystemState {
        requireSessionExists(sessionId)
        val state = stateStore.find(sessionId)
            ?: throw NotFoundException("No simulation in progress for session $sessionId — start an incident first")
        return SimulationEngine.computeState(state)
    }

    @Transactional
    fun applyAction(sessionId: UUID, actionType: SimulationActionType): SystemState {
        requireSessionExists(sessionId)
        val current = stateStore.find(sessionId)
            ?: throw NotFoundException("No simulation in progress for session $sessionId — start an incident first")

        val updated = SimulationEngine.applyAction(current, actionType)
        stateStore.save(sessionId, updated)

        appliedActionRepository.save(
            AppliedAction(sessionId = sessionId, actionType = actionType.name, effect = describe(actionType))
        )

        return SimulationEngine.computeState(updated)
    }

    private fun requireSessionExists(sessionId: UUID) {
        if (!sessionRepository.existsById(sessionId)) {
            throw NotFoundException("Session not found: $sessionId")
        }
    }

    /** docs/ARCHITECTURE.md §6.1 긍정 효과/가능한 부작용. */
    private fun describe(actionType: SimulationActionType): String = when (actionType) {
        SimulationActionType.STRENGTHEN_RATE_LIMIT ->
            "긍정 효과: DB/다운스트림 보호. 가능한 부작용: 일부 사용자 거절, UX 저하."
        SimulationActionType.INCREASE_CACHE_TTL ->
            "긍정 효과: DB 부하·latency 감소. 가능한 부작용: stale data 위험."
        SimulationActionType.INCREASE_DB_POOL ->
            "긍정 효과: 대기 요청 일부 감소. 가능한 부작용: DB 자체 한계 초과 가능."
    }
}
