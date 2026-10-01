package com.sysdrill.backend.mission

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.session.SessionAccessGuard
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** docs/DRILLS_EXPANSION_PLAN.md M1 (PLAN.md Round E8) — "질문하기": the deliberately incomplete brief's missing pieces. */
@RestController
@RequestMapping("/sessions/{sessionId}")
class MissionController(
    private val missionService: MissionService,
    private val sessionAccessGuard: SessionAccessGuard,
) {

    /** Questions without answers until asked. A Game Day spectator sees what the owner has asked so far. */
    @GetMapping("/clarifications")
    fun list(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): ClarificationsResponse {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return missionService.clarifications(sessionId)
    }

    @PostMapping("/clarifications/{questionId}")
    fun ask(
        @PathVariable sessionId: UUID,
        @PathVariable questionId: String,
        @AuthenticatedUserId userId: UUID,
    ): ClarificationsResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return missionService.ask(sessionId, questionId)
    }

    /** docs/DRILLS_EXPANSION_PLAN.md M2 (PLAN.md Round E9) — fields to estimate; judged results after the INITIAL submit. */
    @GetMapping("/estimation")
    fun estimation(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): EstimationResponse {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return missionService.estimation(sessionId)
    }

    /** M3 / O5 (PLAN.md Round E12) — SLO, alert rules, and the domain's suggested starting rules. */
    @GetMapping("/ops")
    fun ops(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): OpsConfigResponse {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return missionService.ops(sessionId)
    }

    @PutMapping("/ops/slo")
    fun updateSlo(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID, @RequestBody slo: SloTargets): OpsConfigResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return missionService.updateSlo(sessionId, slo)
    }

    @PutMapping("/ops/alert-rules")
    fun updateAlertRules(
        @PathVariable sessionId: UUID,
        @AuthenticatedUserId userId: UUID,
        @RequestBody rules: List<AlertRuleInput>,
    ): OpsConfigResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        return missionService.updateAlertRules(sessionId, rules)
    }

    /** docs/DRILLS_EXPANSION_PLAN.md M4 (PLAN.md Round E10) — the INITIAL feedback's follow-up questions to answer with FOLLOWUP. */
    @GetMapping("/defense")
    fun defense(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): DefenseResponse {
        sessionAccessGuard.requireOwnerOrSpectator(sessionId, userId)
        return missionService.defense(sessionId)
    }
}
