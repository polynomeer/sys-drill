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
    /** docs/CODECRAFTERS_BENCHMARK.md §3.3 — raw sandbox output for the test log panel (null until the stage has run, and for results stored before V49). */
    val output: String? = null,
    val durationMs: Int? = null,
)

/** GET /build-challenges/{slug} — the stage roadmap and instructions, available before any submission exists. */
data class BuildChallengeResponse(
    val slug: String,
    val title: String,
    val language: String,
    val sourceFileName: String,
    val stages: List<BuildStageInfoResponse>,
)

data class BuildStageInfoResponse(
    val stageOrder: Int,
    val title: String,
    val spec: String?,
    val instructions: String?,
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
