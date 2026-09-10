package com.sysdrill.backend.simulation

import jakarta.validation.constraints.NotNull
import java.time.Instant

data class SystemStateResponse(
    val trafficRps: Double,
    val p95LatencyMs: Double,
    val errorRate: Double,
    val availability: Double,
    val dbReadLoad: Double,
    val dbWriteLoad: Double,
    val connectionPoolUsage: Double,
    val cacheHitRatio: Double,
    val cacheLatencyMs: Double,
    val queueLag: Long,
    val consumerThroughput: Double,
    val externalDependencyLatencyMs: Double,
    val cpuUtilization: Double,
    val memoryUtilization: Double,
) {
    companion object {
        fun from(state: SystemState) = SystemStateResponse(
            trafficRps = state.trafficRps,
            p95LatencyMs = state.p95LatencyMs,
            errorRate = state.errorRate,
            availability = state.availability,
            dbReadLoad = state.dbReadLoad,
            dbWriteLoad = state.dbWriteLoad,
            connectionPoolUsage = state.connectionPoolUsage,
            cacheHitRatio = state.cacheHitRatio,
            cacheLatencyMs = state.cacheLatencyMs,
            queueLag = state.queueLag,
            consumerThroughput = state.consumerThroughput,
            externalDependencyLatencyMs = state.externalDependencyLatencyMs,
            cpuUtilization = state.cpuUtilization,
            memoryUtilization = state.memoryUtilization,
        )
    }
}

data class ApplyActionRequest(@field:NotNull val actionType: SimulationActionType)

/**
 * ADR-0037 / DRILLS_SIMULATION_VISION.md — the Architecture Canvas can now send
 * the design-time values it collected (e.g. a DB node's pool size) as this
 * session's starting [DesignTraits], instead of always starting from
 * [DesignTraits]' hardcoded defaults. Any field the client omits keeps its
 * Kotlin default, so the canvas only needs to send the handful of fields it
 * actually configured.
 */
data class StartIncidentRequest(val traits: DesignTraits = DesignTraits())

data class TimelineStepResponse(
    val step: Int,
    val actionType: String?,
    val label: String,
    val appliedAt: Instant,
    val systemState: SystemStateResponse,
) {
    companion object {
        fun from(step: TimelineStep) = TimelineStepResponse(
            step = step.step,
            actionType = step.actionType,
            label = step.label,
            appliedAt = step.appliedAt,
            systemState = SystemStateResponse.from(step.systemState),
        )
    }
}
