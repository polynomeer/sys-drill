package com.sysdrill.backend.simulation

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.SessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SimulationService(
    private val sessionRepository: SessionRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioRepository: ScenarioRepository,
    private val stateStore: SimulationStateStore,
    private val appliedActionRepository: AppliedActionRepository,
) {

    /** Starts the session's scenario-appropriate incident template (docs/PRD.md §8). */
    fun startIncident(sessionId: UUID): SystemState {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { NotFoundException("Session not found: $sessionId") }
        val domain = resolveDomain(session.scenarioVersionId)
        val state = SimulationSessionState(domain = domain, incidentActive = true, traits = DesignTraits())
        stateStore.save(sessionId, state)
        return SimulationEngine.computeState(state)
    }

    private fun resolveDomain(scenarioVersionId: UUID): String {
        val version = scenarioVersionRepository.findById(scenarioVersionId)
            .orElseThrow { NotFoundException("Scenario version not found: $scenarioVersionId") }
        val scenario = scenarioRepository.findById(version.scenarioId)
            .orElseThrow { NotFoundException("Scenario not found: ${version.scenarioId}") }
        return scenario.domain
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
        SimulationActionType.ADD_CONSUMERS ->
            "긍정 효과: 컨슈머 처리량 증가로 backlog 감소. 가능한 부작용: provider 동시 호출 증가, 다운스트림 부하 전이."
        SimulationActionType.ENABLE_CIRCUIT_BREAKER ->
            "긍정 효과: 죽은 provider를 기다리지 않아 컨슈머가 빠르게 회복. 가능한 부작용: breaker OPEN 동안 해당 provider 메시지 유실/지연 가능."
        SimulationActionType.ADJUST_RETRY_BACKOFF ->
            "긍정 효과: 재시도 폭풍(retry storm) 완화. 가능한 부작용: 개별 메시지의 전달 지연 증가."
        SimulationActionType.SPLIT_CACHE_POLICY ->
            "긍정 효과: 데이터 특성별 TTL 분리로 hit ratio 회복. 가능한 부작용: 캐시 정책 복잡도 증가."
        SimulationActionType.ENABLE_SINGLE_FLIGHT ->
            "긍정 효과: 동시 cache miss의 DB 요청 중복(dogpile) 제거. 가능한 부작용: 요청 간 대기(head-of-line) 발생 가능."
        SimulationActionType.ADD_READ_REPLICA ->
            "긍정 효과: DB read 용량 증가. 가능한 부작용: replica lag으로 인한 조회 최신성 저하."
        SimulationActionType.ADD_DISPATCHER_WORKERS ->
            "긍정 효과: outbox 처리량 증가로 backlog 감소. 가능한 부작용: 외부 PG에 대한 동시 호출 증가."
        SimulationActionType.ENABLE_IDEMPOTENT_PG_RETRY ->
            "긍정 효과: 응답 유실로 인한 재시도가 중복 처리를 만들지 않아 유효 부하 감소. 가능한 부작용: 멱등성 키 저장·조회 비용 추가."
        SimulationActionType.ISOLATE_PAYMENT_POOL ->
            "긍정 효과: outbox backlog가 주문 처리용 커넥션 풀로 번지지 않음(bulkhead). 가능한 부작용: 결제 전용 풀 자체가 포화되면 그 풀 안에서는 여전히 지연."
        SimulationActionType.ENABLE_FINE_GRAINED_LOCKING ->
            "긍정 효과: 무관한 좌석 간 락 경합 제거로 유효 처리 용량 증가. 가능한 부작용: 락 구현·관리 복잡도 증가(좌석 수만큼 락 필요)."
        SimulationActionType.SHORTEN_HOLD_TIMEOUT ->
            "긍정 효과: 결제 미완료로 이탈한 홀드가 자원을 점유하는 시간 감소. 가능한 부작용: 정상 사용자가 실제로 필요한 시간보다 일찍 홀드가 풀릴 위험."
        SimulationActionType.ENABLE_ATOMIC_INVENTORY_CHECK ->
            "긍정 효과: 재고 확인과 확정 사이의 경쟁으로 인한 낭비성 재시도 제거. 가능한 부작용: 원자적 처리를 위한 락/트랜잭션 범위 확대."
    }
}
