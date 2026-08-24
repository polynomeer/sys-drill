package com.sysdrill.backend.evaluation

import com.sysdrill.backend.common.web.NotFoundException
import java.time.Instant
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

data class RiskFlagResponse(val riskKey: String, val severity: String, val description: String?)

data class EvaluationResponse(
    val id: UUID,
    val submissionId: UUID,
    val rubricVersion: String?,
    val totalScore: Int?,
    val rubricScores: Map<String, Int>,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val riskFlags: List<RiskFlagResponse>,
    val followupQuestions: List<String>,
    val recommendedChanges: List<String>,
    val modelProvider: String?,
    val modelName: String?,
    val createdAt: Instant?,
)

/** docs/ARCHITECTURE.md §10 API table: "GET /submissions/{id}/feedback — 답안별 평가 조회". */
@RestController
class EvaluationController(
    private val evaluationRepository: EvaluationRepository,
    private val evaluationRiskFlagRepository: EvaluationRiskFlagRepository,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping("/submissions/{submissionId}/feedback")
    fun getFeedback(@PathVariable submissionId: UUID): EvaluationResponse {
        val evaluation = evaluationRepository.findFirstBySubmissionIdAndIsActiveTrue(submissionId)
            ?: throw NotFoundException("No active evaluation for submission $submissionId")
        val riskFlags = evaluationRiskFlagRepository.findByEvaluationId(evaluation.id!!)

        return EvaluationResponse(
            id = evaluation.id!!,
            submissionId = evaluation.submissionId,
            rubricVersion = evaluation.rubricVersion,
            totalScore = evaluation.totalScore,
            rubricScores = objectMapper.readIntMap(evaluation.scoreDimensions),
            strengths = objectMapper.readStringList(evaluation.strengths),
            weaknesses = objectMapper.readStringList(evaluation.weaknesses),
            riskFlags = riskFlags.map { RiskFlagResponse(it.riskKey, it.severity, it.description) },
            followupQuestions = objectMapper.readStringList(evaluation.followupQuestions),
            recommendedChanges = objectMapper.readStringList(evaluation.recommendedChanges),
            modelProvider = evaluation.modelProvider,
            modelName = evaluation.modelName,
            createdAt = evaluation.createdAt,
        )
    }
}
