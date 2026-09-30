package com.sysdrill.backend.scenario

import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class ScenarioSummaryResponse(
    val id: UUID,
    val domain: String,
    val title: String,
    val difficulty: String?,
    val organizationId: UUID?,
    val creatorNickname: String? = null,
    /**
     * docs/LEARNING_COMMUNITY_PLAN.md §6.3 — 난이도·인기 신호. 공개 목록(GET /scenarios)과
     * 마켓플레이스 목록에서 채워지고, 조직 시나리오 목록 등 나머지 경로에서는 null 이다.
     */
    val completedCount: Long? = null,
    val averageScore: Int? = null,
    /** docs/CODECRAFTERS_BENCHMARK.md §3.5 — the published version's step types in order (e.g. INITIAL, FOLLOWUP, INCIDENT), so catalog cards know whether a Drill has an incident stage without guessing. Filled by GET /scenarios only. */
    val stepTypes: List<String>? = null,
)

data class ScenarioDetailResponse(
    val id: UUID,
    val domain: String,
    val title: String,
    val difficulty: String?,
    val baseRequirements: Any?,
    val organizationId: UUID?,
    val creatorNickname: String? = null,
    /** docs/CODECRAFTERS_BENCHMARK.md §3.1 — the Drill overview page's stage roadmap. Filled only by the public GET /scenarios/{id}; null from the create/publish responses. */
    val steps: List<ScenarioStepSummaryResponse>? = null,
    /** The INITIAL step's prompt only — FOLLOWUP/INCIDENT prompts stay hidden until the session reaches them, so the overview doesn't spoil the mid-session "conditions changed" twist. */
    val initialPrompt: String? = null,
    /** docs/LEARNING_COMMUNITY_PLAN.md §6.3 difficulty signal, same numbers as the marketplace list — the overview page needs them for official scenarios too, which that list doesn't include. */
    val completedCount: Long? = null,
    val averageScore: Int? = null,
)

data class ScenarioStepSummaryResponse(
    val order: Int,
    val type: String,
)

/**
 * PLAN.md step 34 — an org ADMIN authors a private scenario via API (docs/adr/0024).
 * ADR-0038 — [incidentPrompt] is optional: omitted, this is still the original
 * INITIAL+FOLLOWUP-only shape with [domain] as a free-text label (never reaches
 * the simulation engine, so any string is fine — an org's own internal system
 * name, say). Provided, [CustomScenarioService.create] adds a 3rd INCIDENT step,
 * which *does* need [domain] to be one of [com.sysdrill.backend.simulation.RuleBasedSimulationEngine.KNOWN_DOMAINS]
 * (validated in the service, only in that case) so it reaches a real
 * [com.sysdrill.backend.simulation.SimulationEngine] instead of erroring.
 */
data class CreateCustomScenarioRequest(
    @field:NotBlank val title: String,
    val difficulty: String?,
    @field:NotBlank val domain: String,
    @field:NotBlank val initialPrompt: String,
    @field:NotBlank val followupPrompt: String,
    val incidentPrompt: String? = null,
    /**
     * ROADMAP.md Phase 4 "커스텀 루브릭" — optional; omitted, evaluation uses
     * [com.sysdrill.backend.evaluation.Rubric]'s default 7-dimension set,
     * unchanged from before this field existed. Provided, must sum to
     * [com.sysdrill.backend.evaluation.Rubric.maxTotal] (100, validated in
     * [CustomScenarioService.create]) and replaces the default set for every
     * session on this scenario.
     */
    val rubricDimensions: Map<String, Int>? = null,
)

/** Phase 5 (Scenario Marketplace, docs/adr/0031) — any authenticated user publishes a scenario, same INITIAL+FOLLOWUP-only shape as CreateCustomScenarioRequest. */
data class PublishMarketplaceScenarioRequest(
    @field:NotBlank val title: String,
    val difficulty: String?,
    @field:NotBlank val domain: String,
    @field:NotBlank val initialPrompt: String,
    @field:NotBlank val followupPrompt: String,
)
