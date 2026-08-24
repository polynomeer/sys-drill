package com.sysdrill.backend.scenario

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ScenarioRepository : JpaRepository<Scenario, UUID>

interface ScenarioVersionRepository : JpaRepository<ScenarioVersion, UUID>

interface ScenarioStepRepository : JpaRepository<ScenarioStep, UUID>
