package com.sysdrill.backend.build

import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** docs/ARCHITECTURE.md's Build Mode API (PLAN.md step 9) — CLI-driven, no frontend UI yet (see PLAN.md notes). */
@RestController
class BuildController(
    private val buildSubmissionService: BuildSubmissionService,
    private val buildStageRepository: BuildStageRepository,
    private val buildStageResultRepository: BuildStageResultRepository,
) {

    @PostMapping("/build-challenges/{slug}/submissions")
    fun submit(
        @PathVariable slug: String,
        @Valid @RequestBody request: CreateBuildSubmissionRequest,
    ): ResponseEntity<BuildSubmissionResponse> {
        val submission = buildSubmissionService.submit(slug, request.userId, request.sourceCode, request.commitRef)
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(submission))
    }

    @GetMapping("/build-submissions/{id}")
    fun get(@PathVariable id: UUID): BuildSubmissionResponse = toResponse(buildSubmissionService.get(id))

    private fun toResponse(submission: BuildSubmission): BuildSubmissionResponse {
        val stages = buildStageRepository.findByChallengeIdOrderByStageOrderAsc(submission.challengeId)
        val resultsByStageId = buildStageResultRepository
            .findBySubmissionIdOrderByCreatedAtAsc(submission.id!!)
            .associateBy { it.stageId }

        return BuildSubmissionResponse(
            id = submission.id!!,
            status = submission.status,
            score = submission.score,
            totalStages = stages.size,
            stages = stages.map { stage ->
                val result = resultsByStageId[stage.id]
                BuildStageResultResponse(
                    stageOrder = stage.stageOrder,
                    title = stage.title,
                    status = result?.status,
                    feedback = result?.feedback,
                )
            },
            createdAt = submission.createdAt,
            completedAt = submission.completedAt,
        )
    }
}
