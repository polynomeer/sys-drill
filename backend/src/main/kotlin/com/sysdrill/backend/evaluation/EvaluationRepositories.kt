package com.sysdrill.backend.evaluation

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EvaluationRepository : JpaRepository<Evaluation, UUID> {
    fun existsBySubmissionIdAndIsActiveTrue(submissionId: UUID): Boolean
    fun findBySubmissionId(submissionId: UUID): List<Evaluation>
    fun findFirstBySubmissionIdAndIsActiveTrue(submissionId: UUID): Evaluation?
}

interface EvaluationRiskFlagRepository : JpaRepository<EvaluationRiskFlag, UUID> {
    fun findByEvaluationId(evaluationId: UUID): List<EvaluationRiskFlag>
}
