package com.sysdrill.backend.build

import jakarta.persistence.Column
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

enum class BuildSubmissionStatus { QUEUED, RUNNING, COMPLETED, ERROR }

@Entity
@Table(name = "build_submissions")
class BuildSubmission(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "challenge_id", nullable = false)
    var challengeId: UUID,

    @Column(name = "commit_ref")
    var commitRef: String? = null,

    @Column(name = "source_code", nullable = false, columnDefinition = "text")
    var sourceCode: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BuildSubmissionStatus = BuildSubmissionStatus.QUEUED,

    var score: Int? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,
)
