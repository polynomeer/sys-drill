package com.sysdrill.backend.evaluation

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EvaluationRepository : JpaRepository<Evaluation, UUID> {
    fun existsBySubmissionIdAndIsActiveTrue(submissionId: UUID): Boolean
    fun findBySubmissionId(submissionId: UUID): List<Evaluation>
    fun findFirstBySubmissionIdAndIsActiveTrue(submissionId: UUID): Evaluation?
    fun findBySubmissionIdInAndIsActiveTrue(submissionIds: Collection<UUID>): List<Evaluation>
}

interface EvaluationRiskFlagRepository : JpaRepository<EvaluationRiskFlag, UUID> {
    fun findByEvaluationId(evaluationId: UUID): List<EvaluationRiskFlag>

    /** docs/LEARNING_COMMUNITY_PLAN.md §5.3 — 학습 경로가 여러 세션의 지적 이력을 한 번에 본다. */
    fun findByEvaluationIdIn(evaluationIds: Collection<UUID>): List<EvaluationRiskFlag>
}
