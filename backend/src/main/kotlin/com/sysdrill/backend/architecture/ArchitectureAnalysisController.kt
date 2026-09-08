package com.sysdrill.backend.architecture

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.scenario.ScenarioSummaryResponse
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Phase 6 — Architecture Linter v1 (docs/adr/0034). Fully personal — no public sub-path, unlike /scenarios or /marketplace/scenarios. */
@RestController
@RequestMapping("/architecture-analysis")
class ArchitectureAnalysisController(
    private val architectureAnalysisService: ArchitectureAnalysisService,
) {

    @PostMapping
    fun analyze(
        @AuthenticatedUserId userId: UUID,
        @Valid @RequestBody request: AnalyzeRepositoryRequest,
    ): ArchitectureAnalysisResponse = architectureAnalysisService.analyze(userId, request.openApiSpec)

    @GetMapping("/scenarios")
    fun listMine(@AuthenticatedUserId userId: UUID): List<ScenarioSummaryResponse> = architectureAnalysisService.listMine(userId)
}
