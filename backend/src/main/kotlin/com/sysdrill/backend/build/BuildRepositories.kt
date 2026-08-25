package com.sysdrill.backend.build

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuildChallengeRepository : JpaRepository<BuildChallenge, UUID> {
    fun findBySlug(slug: String): BuildChallenge?
}

interface BuildStageRepository : JpaRepository<BuildStage, UUID> {
    fun findByChallengeIdOrderByStageOrderAsc(challengeId: UUID): List<BuildStage>
}

interface BuildSubmissionRepository : JpaRepository<BuildSubmission, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<BuildSubmission>
}

interface BuildStageResultRepository : JpaRepository<BuildStageResult, UUID> {
    fun findBySubmissionIdOrderByCreatedAtAsc(submissionId: UUID): List<BuildStageResult>
}
