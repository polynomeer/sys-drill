package com.sysdrill.backend.simulation

import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.simulation.realinfra.RealInfraCouponEngine
import com.sysdrill.backend.simulation.realinfra.RealInfraNotificationEngine
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

/**
 * What [AppliedAction.parameters] actually holds (PLAN.md step 25 / ADR-0016)
 * — every row records which engine served the session ([engineMode], really
 * only meaningful on the first "INCIDENT_STARTED" row, but harmless and
 * simpler to write on every row than to special-case the first one).
 * [systemState] is populated only for real-infra sessions: a rule-based
 * session's state at any point is a pure function of
 * (domain, incidentActive, traits-after-N-actions) and can always be
 * recomputed later (ADR-0011) — a real-infra session's state came from an
 * actual k6 run against actual infra and cannot be recomputed after the
 * fact, so it has to be captured at the moment it happened or it's lost.
 */
data class AppliedActionSnapshot(
    val engineMode: String,
    val systemState: SystemState? = null,
)

/** One point on an incident's replay timeline (PLAN.md step 25) — [actionType] is null only for the synthetic first "incident started" step. */
data class TimelineStep(
    val step: Int,
    val actionType: String?,
    val label: String,
    val appliedAt: Instant,
    val systemState: SystemState,
)

/** Sentinel [AppliedAction.actionType] for the incident-start row — deliberately not a [SimulationActionType] member, since it isn't a user-applicable action. */
const val INCIDENT_STARTED = "INCIDENT_STARTED"

@Service
class SimulationService(
    private val sessionRepository: SessionRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioRepository: ScenarioRepository,
    private val stateStore: SimulationStateStore,
    private val appliedActionRepository: AppliedActionRepository,
    private val realInfraCouponEngine: RealInfraCouponEngine,
    private val realInfraNotificationEngine: RealInfraNotificationEngine,
    private val objectMapper: ObjectMapper,
) {

    /**
     * Which [SimulationEngine] serves [EngineMode.REAL_INFRA] for each opted-in
     * domain (PLAN.md step 27 generalizes this from step 21's single
     * coupon-only field) — the set of keys IS the set of domains real-infra
     * mode is valid for; [startIncident] and [engineFor] both derive from it
     * so a third real-infra domain only ever needs one new map entry.
     */
    private val realInfraEngines: Map<String, SimulationEngine> = mapOf(
        RuleBasedSimulationEngine.DOMAIN_COUPON to realInfraCouponEngine,
        RuleBasedSimulationEngine.DOMAIN_NOTIFICATION to realInfraNotificationEngine,
    )

    /**
     * Starts the session's scenario-appropriate incident template (docs/PRD.md §8).
     * [realInfra] opts into PLAN.md step 21's real-infra pilot — only valid for
     * domains in [realInfraEngines] (ADR-0013); every other domain, and
     * non-opted-in sessions of an eligible domain, are completely unaffected
     * by its existence.
     */
    @Transactional
    fun startIncident(sessionId: UUID, realInfra: Boolean = false): SystemState {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { NotFoundException("Session not found: $sessionId") }
        val domain = resolveDomain(session.scenarioVersionId)
        if (realInfra && domain !in realInfraEngines) {
            throw BadRequestException("Real-infra mode is not available for domain: $domain")
        }
        val traits = when {
            !realInfra -> DesignTraits()
            domain == RuleBasedSimulationEngine.DOMAIN_COUPON -> DesignTraits(dbPoolSize = RealInfraCouponEngine.INITIAL_DB_POOL_SIZE)
            domain == RuleBasedSimulationEngine.DOMAIN_NOTIFICATION -> DesignTraits(consumerCount = RealInfraNotificationEngine.INITIAL_CONSUMER_COUNT)
            else -> DesignTraits()
        }
        val engineMode = if (realInfra) EngineMode.REAL_INFRA else EngineMode.RULE_BASED
        val state = SimulationSessionState(
            sessionId = sessionId,
            domain = domain,
            incidentActive = true,
            traits = traits,
            engineMode = engineMode,
        )
        stateStore.save(sessionId, state)
        val computed = engineFor(state).computeState(state)

        // Step 0 of the incident replay timeline (PLAN.md step 25) — without this
        // row, a replay would have no way to know which engine served the session
        // once SimulationStateStore's 6h Redis TTL expires, and (for real-infra)
        // no way to recover the very first measurement at all.
        appliedActionRepository.save(
            AppliedAction(
                sessionId = sessionId,
                actionType = INCIDENT_STARTED,
                effect = "인시던트 시작",
                parameters = objectMapper.writeValueAsString(
                    AppliedActionSnapshot(engineMode = engineMode.name, systemState = computed.takeIf { realInfra })
                ),
            )
        )
        return computed
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
        return engineFor(state).computeState(state)
    }

    @Transactional
    fun applyAction(sessionId: UUID, actionType: SimulationActionType): SystemState {
        requireSessionExists(sessionId)
        val current = stateStore.find(sessionId)
            ?: throw NotFoundException("No simulation in progress for session $sessionId — start an incident first")

        val updated = engineFor(current).applyAction(current, actionType)
        stateStore.save(sessionId, updated)
        // For real-infra, engineFor(current).applyAction(...) above already ran the
        // real probe and cached its result — this computeState call is a cheap
        // cache read (RealInfraCouponEngine's doc comment), not a second probe.
        val resultState = engineFor(updated).computeState(updated)

        appliedActionRepository.save(
            AppliedAction(
                sessionId = sessionId,
                actionType = actionType.name,
                effect = describe(actionType),
                parameters = objectMapper.writeValueAsString(
                    AppliedActionSnapshot(
                        engineMode = updated.engineMode.name,
                        systemState = resultState.takeIf { updated.engineMode == EngineMode.REAL_INFRA },
                    )
                ),
            )
        )

        return resultState
    }

    /**
     * Reconstructs the incident's metrics-over-time timeline (PLAN.md step 25,
     * ADR-0016) from [AppliedAction] rows — never a second, separately-tracked
     * history. A rule-based session's steps are replayed by re-running
     * [RuleBasedSimulationEngine] over the stored action sequence starting from
     * default [DesignTraits] (ADR-0011: derived, not persisted); a real-infra
     * session's steps are read directly from each row's stored snapshot, since
     * those numbers came from real infra and can't be recomputed after the fact.
     */
    fun getTimeline(sessionId: UUID): List<TimelineStep> {
        requireSessionExists(sessionId)
        val events = appliedActionRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
        if (events.isEmpty()) return emptyList()

        val firstSnapshot = readSnapshot(events.first())
        return if (firstSnapshot?.engineMode == EngineMode.REAL_INFRA.name) {
            events.mapIndexed { index, event ->
                val snapshot = readSnapshot(event)
                    ?: error("Real-infra session $sessionId is missing a state snapshot on action ${event.id}")
                TimelineStep(
                    step = index,
                    actionType = event.actionType.takeIf { it != INCIDENT_STARTED },
                    label = event.effect ?: event.actionType,
                    appliedAt = event.createdAt!!,
                    systemState = snapshot.systemState
                        ?: error("Real-infra session $sessionId is missing a state snapshot on action ${event.id}"),
                )
            }
        } else {
            val session = sessionRepository.findById(sessionId)
                .orElseThrow { NotFoundException("Session not found: $sessionId") }
            val domain = resolveDomain(session.scenarioVersionId)
            var replayState = SimulationSessionState(sessionId, domain, incidentActive = true, traits = DesignTraits())
            events.mapIndexed { index, event ->
                if (index > 0) {
                    val actionType = SimulationActionType.valueOf(event.actionType)
                    replayState = RuleBasedSimulationEngine.applyAction(replayState, actionType)
                }
                TimelineStep(
                    step = index,
                    actionType = event.actionType.takeIf { it != INCIDENT_STARTED },
                    label = event.effect ?: event.actionType,
                    appliedAt = event.createdAt!!,
                    systemState = RuleBasedSimulationEngine.computeState(replayState),
                )
            }
        }
    }

    private fun readSnapshot(event: AppliedAction): AppliedActionSnapshot? =
        event.parameters?.let { objectMapper.readValue(it, AppliedActionSnapshot::class.java) }

    private fun engineFor(state: SimulationSessionState): SimulationEngine =
        if (state.engineMode == EngineMode.REAL_INFRA) realInfraEngines.getValue(state.domain) else RuleBasedSimulationEngine

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
        SimulationActionType.ENABLE_CHECKPOINT_RESTART ->
            "긍정 효과: 실패 시 처음부터가 아니라 실패한 청크부터 재개하여 낭비되는 작업량 대폭 감소. 가능한 부작용: 체크포인트 저장·조회 비용 추가."
        SimulationActionType.REDUCE_CHUNK_SIZE ->
            "긍정 효과: 실패 시 재처리해야 할 레코드 범위 축소. 가능한 부작용: 청크당 커밋 오버헤드 비중 증가로 정상 처리량 자체는 감소."
        SimulationActionType.ENABLE_IDEMPOTENT_RECONCILIATION ->
            "긍정 효과: 재처리된 레코드가 중복 반영되지 않아 정산 정합성 유지. 가능한 부작용: 레코드별 처리 이력 저장·조회 비용 추가."
    }
}
