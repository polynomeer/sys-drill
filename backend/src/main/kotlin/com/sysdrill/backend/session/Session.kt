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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
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

    /**
     * 슬라이스 6 (docs/adr/0041) — `PRIVATE` 또는 `PUBLIC`. 기본은 비공개이고,
     * 완료한 세션에 한해 본인이 명시적으로 공개할 수 있다.
     */
    @Column(nullable = false)
    var visibility: String = "PRIVATE",

    /** 공개 시 닉네임 대신 "익명"으로 표시한다. */
    @Column(name = "shared_anonymously", nullable = false)
    var sharedAnonymously: Boolean = false,

    /** 공개한 시각. 공개를 철회하면 다시 null. */
    @Column(name = "shared_at")
    var sharedAt: Instant? = null,

    /**
     * PLAN.md Round E8 — the mission's in-progress state as JSON ([com.sysdrill.backend.mission.MissionState]):
     * clarifying questions asked, the pinned tail-design variant, and later SLO / alert rules.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mission_state", columnDefinition = "jsonb", nullable = false)
    var missionState: String = "{}",

    /** PLAN.md Round E15 (C8) — the author's note on a shared writeup; everything else is summarised automatically. */
    @Column(name = "writeup_note")
    var writeupNote: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
