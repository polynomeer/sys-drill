package com.sysdrill.backend.scenario

import com.sysdrill.backend.auth.AuthenticatedUserId
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Phase 5 — Scenario Marketplace (docs/adr/0031). Detail (GET /scenarios/{id}) and session start (POST /sessions) are unchanged existing endpoints — a marketplace scenario is just a public scenario with a creator. */
@RestController
@RequestMapping("/marketplace/scenarios")
class MarketplaceController(
    private val marketplaceScenarioService: MarketplaceScenarioService,
) {

    @PostMapping
    fun publish(
        @AuthenticatedUserId userId: UUID,
        @Valid @RequestBody request: PublishMarketplaceScenarioRequest,
    ): ResponseEntity<ScenarioDetailResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(marketplaceScenarioService.publish(userId, request))

    @GetMapping
    fun listAll(): List<ScenarioSummaryResponse> = marketplaceScenarioService.listAll()

    @GetMapping("/mine")
    fun listMine(@AuthenticatedUserId userId: UUID): List<ScenarioSummaryResponse> = marketplaceScenarioService.listMine(userId)
}
