package com.sysdrill.backend.session

import com.sysdrill.backend.common.web.NotFoundException
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
) {

    fun requireOwner(sessionId: UUID, userId: UUID): Session {
        val session = sessionRepository.findById(sessionId)
            .orElseThrow { NotFoundException("Session not found: $sessionId") }
        if (session.userId != userId) throw NotFoundException("Session not found: $sessionId")
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
