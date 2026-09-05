package com.sysdrill.backend.scenario

import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class ScenarioSummaryResponse(
    val id: UUID,
    val domain: String,
    val title: String,
    val difficulty: String?,
    val organizationId: UUID?,
)

data class ScenarioDetailResponse(
    val id: UUID,
    val domain: String,
    val title: String,
    val difficulty: String?,
    val baseRequirements: Any?,
    val organizationId: UUID?,
)

/** PLAN.md step 34 — an org ADMIN authors a private scenario via API (docs/adr/0024), fixed to exactly INITIAL + FOLLOWUP (no Incident/Wargame in v1). */
data class CreateCustomScenarioRequest(
    @field:NotBlank val title: String,
    val difficulty: String?,
    @field:NotBlank val domain: String,
    @field:NotBlank val initialPrompt: String,
    @field:NotBlank val followupPrompt: String,
)
