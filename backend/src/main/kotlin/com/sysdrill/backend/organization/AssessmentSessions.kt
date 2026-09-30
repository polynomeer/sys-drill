package com.sysdrill.backend.organization

import org.springframework.stereotype.Component
import java.util.UUID

/**
 * ADR-0043 — hiring-assessment sessions are ordinary `sessions` rows on
 * (public or organization) scenarios; only `organization_assessments.result_session_id`
 * marks them. Community and public-facing features use this to keep them out:
 * an assessment is the organization's evaluation artifact, not the candidate's
 * public training record.
 */
@Component
class AssessmentSessions(private val assessmentRepository: OrganizationAssessmentRepository) {

    /** Which of [sessionIds] are assessment results. One query, empty input short-circuits. */
    fun among(sessionIds: Collection<UUID>): Set<UUID> {
        if (sessionIds.isEmpty()) return emptySet()
        return assessmentRepository.findByResultSessionIdIn(sessionIds).mapNotNull { it.resultSessionId }.toSet()
    }

    fun isAssessment(sessionId: UUID): Boolean = assessmentRepository.existsByResultSessionId(sessionId)
}
