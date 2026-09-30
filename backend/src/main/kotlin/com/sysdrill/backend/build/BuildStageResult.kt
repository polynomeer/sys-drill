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

enum class BuildStageStatus { PASSED, FAILED }

@Entity
@Table(name = "build_stage_results")
class BuildStageResult(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "submission_id", nullable = false)
    var submissionId: UUID,

    @Column(name = "stage_id", nullable = false)
    var stageId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BuildStageStatus,

    @Column(columnDefinition = "text")
    var feedback: String? = null,

    /** V49 — raw sandbox output (stdout+stderr), tail-capped by [BuildRunnerWorker], for the test log panel. */
    @Column(columnDefinition = "text")
    var output: String? = null,

    @Column(name = "duration_ms")
    var durationMs: Int? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
