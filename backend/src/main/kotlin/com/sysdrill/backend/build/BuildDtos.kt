package com.sysdrill.backend.build

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateBuildSubmissionRequest(
    @field:NotNull val userId: UUID,
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
