package com.sysdrill.backend.simulation

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.session.SessionAccessGuard
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sessions/{sessionId}/topology")
class SystemTopologyController(
    private val systemTopologyService: SystemTopologyService,
    private val sessionAccessGuard: SessionAccessGuard,
) {

    /**
     * PLAN.md Round E7 — a Game Day spectator may read it too: the incident's Service Map
     * (docs/OBSERVABILITY_UI_PLAN.md O3) is drawn on this graph, and spectators already see
     * the same session's metrics, timeline and series. Writing stays owner-only.
     */
    @GetMapping
    fun get(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): SystemTopologyResponse {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return systemTopologyService.get(sessionId)
    }

    @PutMapping
    fun save(
        @PathVariable sessionId: UUID,
        @AuthenticatedUserId userId: UUID,
        @Valid @RequestBody request: SaveSystemTopologyRequest,
    ): SystemTopologyResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return systemTopologyService.save(sessionId, request)
    }
}
