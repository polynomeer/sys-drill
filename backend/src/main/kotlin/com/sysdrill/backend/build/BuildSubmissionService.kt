package com.sysdrill.backend.build

import com.sysdrill.backend.common.events.BuildSubmissionRequested
import com.sysdrill.backend.common.web.NotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BuildSubmissionService(
    private val buildChallengeRepository: BuildChallengeRepository,
    private val buildSubmissionRepository: BuildSubmissionRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun submit(slug: String, userId: UUID, sourceCode: String, commitRef: String?): BuildSubmission {
        val challenge = buildChallengeRepository.findBySlug(slug)
            ?: throw NotFoundException("Build challenge not found: $slug")

        val submission = buildSubmissionRepository.save(
            BuildSubmission(
                userId = userId,
                challengeId = challenge.id!!,
                commitRef = commitRef,
                sourceCode = sourceCode,
            )
        )
        // Delivered after this transaction commits (BuildJobPublisher) so the worker
        // never dequeues a submission id before it's visible in a fresh transaction —
        // same race EvaluationRequestPublisher fixes for evaluation jobs.
        eventPublisher.publishEvent(BuildSubmissionRequested(submission.id!!))
        return submission
    }

    fun get(submissionId: UUID): BuildSubmission =
        buildSubmissionRepository.findById(submissionId)
            .orElseThrow { NotFoundException("Build submission not found: $submissionId") }
}
