package com.sysdrill.backend.scenario

import com.sysdrill.backend.content.ContentItem
import tools.jackson.databind.ObjectMapper

/** Shared by [ScenarioController] (public scenarios) and [OrganizationController][com.sysdrill.backend.organization.OrganizationController] (a member's own org-scoped scenarios) — same DTOs, same ContentItem-lookup shape either way. */
object ScenarioResponses {
    fun toSummary(scenario: Scenario, content: ContentItem?) = ScenarioSummaryResponse(
        id = scenario.id!!,
        domain = scenario.domain,
        title = content?.title ?: scenario.domain,
        difficulty = content?.difficulty,
        organizationId = scenario.organizationId,
    )

    fun toDetail(scenario: Scenario, content: ContentItem?, objectMapper: ObjectMapper) = ScenarioDetailResponse(
        id = scenario.id!!,
        domain = scenario.domain,
        title = content?.title ?: scenario.domain,
        difficulty = content?.difficulty,
        baseRequirements = scenario.baseRequirements?.let { objectMapper.readValue(it, Any::class.java) },
        organizationId = scenario.organizationId,
    )
}
