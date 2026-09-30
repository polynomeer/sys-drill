package com.sysdrill.backend.scenario

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ScenarioRepository : JpaRepository<Scenario, UUID> {
    fun findByOrganizationIdIsNull(): List<Scenario>
    fun findByOrganizationId(organizationId: UUID): List<Scenario>
    fun findByCreatorUserIdIsNotNull(): List<Scenario>
    fun findByCreatorUserId(creatorUserId: UUID): List<Scenario>
    fun findByOrganizationIdIsNullAndVisibility(visibility: String): List<Scenario>
    fun findByCreatorUserIdIsNotNullAndVisibility(visibility: String): List<Scenario>
    fun findByCreatorUserIdAndVisibility(creatorUserId: UUID, visibility: String): List<Scenario>
}

interface ScenarioVersionRepository : JpaRepository<ScenarioVersion, UUID> {
    fun findFirstByScenarioIdAndStatusOrderByVersionNoDesc(scenarioId: UUID, status: String): ScenarioVersion?

    /** docs/LEARNING_COMMUNITY_PLAN.md §6.3 — 시나리오 통계가 그 시나리오의 모든 버전을 함께 센다. */
    fun findByScenarioIdIn(scenarioIds: Collection<UUID>): List<ScenarioVersion>
}

interface ScenarioStepRepository : JpaRepository<ScenarioStep, UUID> {
    fun findByScenarioVersionIdAndStepOrder(scenarioVersionId: UUID, stepOrder: Int): ScenarioStep?
    fun findByScenarioVersionIdOrderByStepOrder(scenarioVersionId: UUID): List<ScenarioStep>
}
