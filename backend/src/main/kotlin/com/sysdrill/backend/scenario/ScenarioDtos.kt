package com.sysdrill.backend.scenario

import java.util.UUID

data class ScenarioSummaryResponse(
    val id: UUID,
    val domain: String,
    val title: String,
    val difficulty: String?,
)

data class ScenarioDetailResponse(
    val id: UUID,
    val domain: String,
    val title: String,
    val difficulty: String?,
    val baseRequirements: Any?,
)
