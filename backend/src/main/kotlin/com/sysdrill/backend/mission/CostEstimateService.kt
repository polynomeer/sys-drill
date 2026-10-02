package com.sysdrill.backend.mission

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.session.Session
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.simulation.SimulationActionType
import com.sysdrill.backend.simulation.SystemTopologyService
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.math.roundToInt

/**
 * docs/DRILLS_EXPANSION_PLAN.md M8 (PLAN.md Round E20) — an estimated monthly cost and an
 * operational-complexity figure for the saved canvas, against the scenario's budget and team.
 * Recomputed on every read (ADR-0011). Not a score: it's a fact handed to the evaluation prompt,
 * so the LLM can say "this design costs 24% over budget and needs Kafka skills the team lacks".
 */
@Service
class CostEstimateService(
    private val sessionRepository: SessionRepository,
    private val missionService: MissionService,
    private val systemTopologyService: SystemTopologyService,
) {
    fun estimate(sessionId: UUID): CostEstimateResponse {
        val session = sessionRepository.findById(sessionId).orElseThrow { NotFoundException("Session not found: $sessionId") }
        return estimate(session)
    }

    fun estimate(session: Session): CostEstimateResponse {
        val constraints = missionService.initialContent(session).constraints ?: return CostEstimateResponse(available = false)
        // An empty (or client-only) canvas is "not drawn" — its default traits would otherwise be priced.
        val profile = systemTopologyService.designProfile(session.id!!, missionService.domainOf(session))
            ?.takeIf { p -> p.nodeKinds.keys.any { it in NODE_MONTHLY_USD } }
        val kinds = profile?.nodeKinds.orEmpty().filterKeys { it in NODE_MONTHLY_USD }
        val lines = kinds.map { (kind, count) ->
            CostLine(kind, KIND_LABELS[kind] ?: kind, count, NODE_MONTHLY_USD.getValue(kind), count * NODE_MONTHLY_USD.getValue(kind))
        } + profile?.traits.orEmpty().filterKeys { it in UNIT_MONTHLY_USD }.filterValues { it > 0 }.map { (trait, units) ->
            CostLine(trait, UNIT_LABELS[trait] ?: trait, units, UNIT_MONTHLY_USD.getValue(trait), units * UNIT_MONTHLY_USD.getValue(trait))
        }
        val monthly = lines.sumOf { it.cost }
        val budget = constraints.budgetPerMonth
        val complexity = kinds.keys.sumOf { kind ->
            (KIND_COMPLEXITY[kind] ?: 0.0) * experienceMultiplier(constraints.opsExperience[kind])
        }.roundToInt()
        return CostEstimateResponse(
            available = true,
            drawn = profile != null,
            monthlyCost = monthly,
            budgetPerMonth = budget,
            budgetDeltaPct = budget?.takeIf { it > 0 }?.let { ((monthly - it) / it * 100).roundToInt() },
            lines = lines,
            complexity = complexity,
            teamCapacity = constraints.teamSize?.let { it * TEAM_CAPACITY_PER_PERSON },
            teamSize = constraints.teamSize,
            opsExperience = constraints.opsExperience.mapKeys { KIND_LABELS[it.key] ?: it.key },
            actionCostDeltas = ACTION_COST_DELTAS.mapKeys { it.key.name },
        )
    }

    /** The evaluation prompt's M8 section — a fact sheet, never a verdict. */
    fun promptSection(session: Session): String? {
        val e = estimate(session).takeIf { it.available && it.drawn } ?: return null
        return buildString {
            appendLine("## 비용·운영 복잡도 (캔버스 기준 추정치)")
            appendLine("- 월 추정 비용 $${"%,.0f".format(e.monthlyCost)}" + (e.budgetPerMonth?.let { " / 예산 $${"%,.0f".format(it)} (${signed(e.budgetDeltaPct ?: 0)}%)" } ?: ""))
            appendLine("- 운영 복잡도 ${e.complexity}" + (e.teamCapacity?.let { " / 팀 역량 $it (${e.teamSize}명)" } ?: ""))
            if (e.opsExperience.isNotEmpty()) appendLine("- 팀 운영 경험: " + e.opsExperience.entries.joinToString { "${it.key} ${it.value}" })
            appendLine("기술을 많이 쓸수록 좋은 설계가 아닙니다. 예산·팀 역량을 넘는 선택이 있다면 트레이드오프 설명 항목에서 그 근거를 요구하고, 넘지 않는다면 언급하지 않아도 됩니다.")
        }
    }

    private fun signed(n: Int) = if (n > 0) "+$n" else "$n"

    companion object {
        /** USD per month per node — estimates for teaching, never tracked against a real cloud price list. */
        val NODE_MONTHLY_USD = mapOf("gateway" to 150.0, "service" to 200.0, "db" to 500.0, "cache" to 250.0, "queue" to 300.0, "cdn" to 100.0)

        /** Scale units the canvas sets per domain (SystemTopologyService), priced per unit. */
        val UNIT_MONTHLY_USD = mapOf("readReplicaCount" to 400.0, "consumerCount" to 30.0, "dispatcherWorkers" to 30.0, "podReplicas" to 20.0)

        val KIND_LABELS = mapOf("gateway" to "게이트웨이", "service" to "서비스", "db" to "DB", "cache" to "캐시", "queue" to "큐", "cdn" to "CDN")
        val UNIT_LABELS = mapOf("readReplicaCount" to "읽기 복제본", "consumerCount" to "컨슈머", "dispatcherWorkers" to "디스패처 워커", "podReplicas" to "Pod")

        /** Base operational burden per distinct kind used — a queue is more to run than a CDN. */
        val KIND_COMPLEXITY = mapOf("gateway" to 8.0, "service" to 6.0, "db" to 10.0, "cache" to 12.0, "queue" to 18.0, "cdn" to 6.0)

        const val TEAM_CAPACITY_PER_PERSON = 15

        fun experienceMultiplier(level: String?): Double = when (level) {
            "LOW" -> 1.5
            "HIGH" -> 0.7
            else -> 1.0
        }

        /** Scale-out actions in [UNIT_MONTHLY_USD] units — the increments the engine applies. */
        val ACTION_COST_DELTAS: Map<SimulationActionType, Double> = mapOf(
            SimulationActionType.ADD_READ_REPLICA to 1 * 400.0,
            SimulationActionType.ADD_CONSUMERS to 8 * 30.0,
            SimulationActionType.ADD_DISPATCHER_WORKERS to 8 * 30.0,
            SimulationActionType.SCALE_OUT_REPLICAS to 36 * 20.0,
        )
    }
}

data class CostLine(val key: String, val label: String, val units: Int, val unitCost: Double, val cost: Double)

data class CostEstimateResponse(
    /** False when the scenario has no constraints — the UI shows nothing. */
    val available: Boolean,
    /** False with no saved canvas: the cost is then 0 and the UI asks for a canvas. */
    val drawn: Boolean = false,
    val monthlyCost: Double = 0.0,
    val budgetPerMonth: Double? = null,
    val budgetDeltaPct: Int? = null,
    val lines: List<CostLine> = emptyList(),
    val complexity: Int = 0,
    val teamCapacity: Int? = null,
    val teamSize: Int? = null,
    val opsExperience: Map<String, String> = emptyMap(),
    val actionCostDeltas: Map<String, Double> = emptyMap(),
)
