package com.sysdrill.backend.scenario

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ScenarioRepository : JpaRepository<Scenario, UUID> {
    fun findByOrganizationIdIsNull(): List<Scenario>
    fun findByOrganizationId(organizationId: UUID): List<Scenario>
}

interface ScenarioVersionRepository : JpaRepository<ScenarioVersion, UUID> {
    fun findFirstByScenarioIdAndStatusOrderByVersionNoDesc(scenarioId: UUID, status: String): ScenarioVersion?
}

interface ScenarioStepRepository : JpaRepository<ScenarioStep, UUID> {
    fun findByScenarioVersionIdAndStepOrder(scenarioVersionId: UUID, stepOrder: Int): ScenarioStep?
}
