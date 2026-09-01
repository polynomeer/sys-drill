package com.sysdrill.backend.build

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

/** PLAN.md step 31 — userId used to be a client-supplied body field; now derived from the caller's token (@AuthenticatedUserId in BuildController). */
data class CreateBuildSubmissionRequest(
    @field:NotBlank val sourceCode: String,
    val commitRef: String? = null,
)

data class BuildStageResultResponse(
    val stageOrder: Int,
    val title: String,
    val status: BuildStageStatus?,
    val feedback: String?,
)

data class BuildSubmissionResponse(
    val id: UUID,
    val status: BuildSubmissionStatus,
    val score: Int?,
    val totalStages: Int,
    val stages: List<BuildStageResultResponse>,
    val createdAt: Instant?,
    val completedAt: Instant?,
)
