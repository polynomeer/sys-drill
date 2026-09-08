package com.sysdrill.backend.architecture

import com.sysdrill.backend.scenario.ScenarioDetailResponse
import jakarta.validation.constraints.NotBlank

/** Phase 6 — Architecture Linter v1 (docs/adr/0034). The raw spec is never persisted (parsed, scanned, and discarded within the request). */
data class AnalyzeRepositoryRequest(
    @field:NotBlank val openApiSpec: String,
)

data class ArchitectureAnalysisResponse(
    val scenario: ScenarioDetailResponse,
    val findings: List<String>,
)
