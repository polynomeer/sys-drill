package com.sysdrill.backend.postmortem

import com.sysdrill.backend.simulation.SystemStateResponse
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class PostmortemActionSummary(
    val actionType: String,
    val label: String,
    val elapsedSeconds: Long,
)

/**
 * MTTD/MTTR·조치 타임라인·전후 지표는 [com.sysdrill.backend.simulation.SimulationService.getTimeline]로부터
 * 매 조회마다 다시 계산한다 — [saved]가 false면 [rootCause]/[mitigationActions]/[rootFixActions]/[preventionItems]는
 * 아직 아무도 작성하지 않은 빈 초안이라는 뜻이다.
 */
data class PostmortemResponse(
    val sessionId: UUID,
    val saved: Boolean,
    val mttdSeconds: Long?,
    val mttrSeconds: Long?,
    val actionsTimeline: List<PostmortemActionSummary>,
    val metricsBefore: SystemStateResponse?,
    val metricsAfter: SystemStateResponse?,
    val rootCause: String?,
    val mitigationActions: List<String>,
    val rootFixActions: List<String>,
    val preventionItems: List<String>,
    val updatedAt: Instant?,
)

data class SavePostmortemRequest(
    @field:NotBlank val rootCause: String,
    val mitigationActions: List<String> = emptyList(),
    val rootFixActions: List<String> = emptyList(),
    val preventionItems: List<String> = emptyList(),
)
