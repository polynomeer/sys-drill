package com.sysdrill.backend.session

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.organization.OrganizationMembershipRepository
import com.sysdrill.backend.submission.Submission
import com.sysdrill.backend.submission.SubmissionRepository
import java.util.UUID
import org.springframework.stereotype.Component

/**
 * PLAN.md step 31 — every sessionId/submissionId-scoped endpoint used to have
 * zero ownership check (any caller who knew the UUID could read/write it).
 * Reuses [NotFoundException] (404) for "not yours", not a new 403, so a
 * caller can't distinguish "doesn't exist" from "exists but isn't mine".
 */
@Component
class SessionAccessGuard(
    private val sessionRepository: SessionRepository,
    private val submissionRepository: SubmissionRepository,
    private val organizationMembershipRepository: OrganizationMembershipRepository,
) {

    fun requireOwner(sessionId: UUID, userId: UUID): Session {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { NotFoundException("Session not found: $sessionId") }
        if (session.userId != userId) throw NotFoundException("Session not found: $sessionId")
        return session
    }

    /**
     * PLAN.md step 36 (Game Day) — read-only access for the owner or a Game
     * Day spectator: anyone who shares *any* organization with the session's
     * owner, regardless of which scenario the session is on. This is
     * deliberately not tied to the scenario's own organizationId (step
     * 34/ADR-0024) — a custom scenario never reaches the Incident/Wargame
     * phase spectating exists to watch, so scoping by "does the org own this
     * scenario" would make Game Day watch nothing in practice. Mutating
     * endpoints (submit/advance/incident actions) stay on [requireOwner] —
     * spectating never allows changing state.
     */
    fun requireOwnerOrSpectator(sessionId: UUID, userId: UUID): Session {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { NotFoundException("Session not found: $sessionId") }
        if (session.userId == userId) return session
        val ownerOrgIds = organizationMembershipRepository.findByUserId(session.userId).map { it.organizationId }.toSet()
        val callerOrgIds = organizationMembershipRepository.findByUserId(userId).map { it.organizationId }.toSet()
        if (ownerOrgIds.none { it in callerOrgIds }) throw NotFoundException("Session not found: $sessionId")
        return session
    }

    /** 2-hop: submissionId -> Submission.sessionId -> Session.userId. */
    fun requireOwnerOfSubmission(submissionId: UUID, userId: UUID): Submission {
        val submission = submissionRepository.findById(submissionId)
            .orElseThrow { NotFoundException("Submission not found: $submissionId") }
        requireOwner(submission.sessionId, userId)
        return submission
    }
}
