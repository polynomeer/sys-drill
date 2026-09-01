package com.sysdrill.backend.reporting

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.session.SessionAccessGuard
import java.time.Instant
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

data class ReportResponse(
    val id: UUID,
    val sessionId: UUID,
    val version: Int,
    val summary: String?,
    val timelineFeedback: List<TimelineEntry>,
    val improvementGuide: List<String>,
    val buildSummary: BuildSummary?,
    val createdAt: Instant?,
)

/** docs/ARCHITECTURE.md §10 API table: "GET /sessions/{id}/report — 세션 종합 리포트 조회". */
@RestController
class ReportController(
    private val reportRepository: ReportRepository,
    private val objectMapper: ObjectMapper,
    private val sessionAccessGuard: SessionAccessGuard,
) {

    @GetMapping("/sessions/{sessionId}/report")
    fun getReport(@PathVariable sessionId: UUID, @AuthenticatedUserId userId: UUID): ReportResponse {
        sessionAccessGuard.requireOwner(sessionId, userId)
        val report = reportRepository.findFirstBySessionIdOrderByVersionDesc(sessionId)
            ?: throw NotFoundException("No report for session $sessionId yet — it may not be COMPLETED")
        return ReportResponse(
            id = report.id!!,
            sessionId = report.sessionId,
            version = report.version,
            summary = report.summary,
            timelineFeedback = report.timelineFeedback?.let {
                objectMapper.readValue(it, Array<TimelineEntry>::class.java).toList()
            } ?: emptyList(),
            improvementGuide = report.improvementGuide?.let {
                objectMapper.readValue(it, Array<String>::class.java).toList()
            } ?: emptyList(),
            buildSummary = report.buildSummary?.let { objectMapper.readValue(it, BuildSummary::class.java) },
            createdAt = report.createdAt,
        )
    }
}
