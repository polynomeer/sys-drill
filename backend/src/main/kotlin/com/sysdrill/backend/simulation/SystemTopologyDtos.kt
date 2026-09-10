package com.sysdrill.backend.simulation

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

/** [saved] false means nobody has drawn/saved a canvas yet — [graph] is then [SystemTopologyService.EMPTY_GRAPH], not null, so the frontend never has to special-case a missing field. */
data class SystemTopologyResponse(
    val sessionId: UUID,
    val saved: Boolean,
    val graph: String,
    val updatedAt: Instant?,
)

data class SaveSystemTopologyRequest(
    @field:NotBlank val graph: String,
)
