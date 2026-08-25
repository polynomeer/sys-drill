package com.sysdrill.backend.reporting

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "reports")
class Report(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Column(nullable = false)
    var version: Int = 1,

    @Column(columnDefinition = "text")
    var summary: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "timeline_feedback", columnDefinition = "jsonb")
    var timelineFeedback: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "improvement_guide", columnDefinition = "jsonb")
    var improvementGuide: String? = null,

    /** Set when the session was entered via Bridge Mode — see [com.sysdrill.backend.reporting.BuildSummary]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "build_summary", columnDefinition = "jsonb")
    var buildSummary: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
