package com.sysdrill.backend.session

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sessions")
class Session(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "scenario_version_id", nullable = false)
    var scenarioVersionId: UUID,

    /** Set when this session was entered via Bridge Mode, right after completing a Build submission. */
    @Column(name = "build_submission_id")
    var buildSubmissionId: UUID? = null,

    /** PLAN.md step 28 — opt-in at session start; drives per-phase time limits (sysdrill.session.interview-timer.*). */
    @Column(name = "interview_mode", nullable = false)
    var interviewMode: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SessionStatus = SessionStatus.IN_PROGRESS,

    @Column(name = "current_phase")
    var currentPhase: String? = null,

    var seed: String? = null,

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.now(),

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
