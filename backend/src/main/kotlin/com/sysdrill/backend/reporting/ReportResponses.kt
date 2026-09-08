package com.sysdrill.backend.reporting

import tools.jackson.databind.ObjectMapper

/** Shared by [ReportController] (the session owner's own report) and the org assessment report endpoint (docs/adr/0033) — same jsonb-parsing logic either way. */
object ReportResponses {
    fun toResponse(report: Report, objectMapper: ObjectMapper) = ReportResponse(
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
