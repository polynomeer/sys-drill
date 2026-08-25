package com.sysdrill.backend.reporting

import com.sysdrill.backend.common.readStringList
import com.sysdrill.backend.evaluation.EvaluationRepository
import com.sysdrill.backend.evaluation.EvaluationRiskFlagRepository
import com.sysdrill.backend.submission.SubmissionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

data class TimelineEntry(
    val phase: String,
    val submissionId: UUID,
    val totalScore: Int?,
    val topRisks: List<String>,
)

/**
 * Synthesizes a [Report] from every step's Evaluation once a session
 * completes (docs/ARCHITECTURE.md's Report Service: "여러 평가 결과를 사용자에게
 * 읽기 쉬운 리포트로 합칩니다"). Called from SessionService.advance() on the
 * FEEDBACK_READY -> COMPLETED leg.
 */
@Service
class ReportService(
    private val submissionRepository: SubmissionRepository,
    private val evaluationRepository: EvaluationRepository,
    private val evaluationRiskFlagRepository: EvaluationRiskFlagRepository,
    private val reportRepository: ReportRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun generate(sessionId: UUID): Report {
        val submissions = submissionRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)

        val timeline = submissions.mapNotNull { submission ->
            val evaluation = evaluationRepository.findFirstBySubmissionIdAndIsActiveTrue(submission.id!!)
                ?: return@mapNotNull null
            val risks = evaluationRiskFlagRepository.findByEvaluationId(evaluation.id!!).map { it.description ?: it.riskKey }
            TimelineEntry(
                phase = submission.phase,
                submissionId = submission.id!!,
                totalScore = evaluation.totalScore,
                topRisks = risks,
            )
        }

        val scores = timeline.mapNotNull { it.totalScore }
        val averageScore = if (scores.isNotEmpty()) scores.sum() / scores.size else null
        val summary = if (averageScore != null) {
            "총 ${timeline.size}개 단계를 완료했습니다. 평균 점수 ${averageScore}/100."
        } else {
            "총 ${timeline.size}개 단계를 완료했습니다."
        }

        val improvementGuide = submissions
            .mapNotNull { evaluationRepository.findFirstBySubmissionIdAndIsActiveTrue(it.id!!) }
            .flatMap { objectMapper.readStringList(it.recommendedChanges) }
            .distinct()

        val nextVersion = (reportRepository.findFirstBySessionIdOrderByVersionDesc(sessionId)?.version ?: 0) + 1
        return reportRepository.save(
            Report(
                sessionId = sessionId,
                version = nextVersion,
                summary = summary,
                timelineFeedback = objectMapper.writeValueAsString(timeline),
                improvementGuide = objectMapper.writeValueAsString(improvementGuide),
            )
        )
    }
}
