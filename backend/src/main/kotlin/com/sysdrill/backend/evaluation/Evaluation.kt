package com.sysdrill.backend.evaluation

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
@Table(name = "evaluations")
class Evaluation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "submission_id", nullable = false)
    var submissionId: UUID,

    @Column(name = "rubric_version")
    var rubricVersion: String? = null,

    @Column(name = "total_score")
    var totalScore: Int? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_dimensions", columnDefinition = "jsonb")
    var scoreDimensions: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var strengths: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var weaknesses: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_points", columnDefinition = "jsonb")
    var riskPoints: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "followup_questions", columnDefinition = "jsonb")
    var followupQuestions: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_changes", columnDefinition = "jsonb")
    var recommendedChanges: String? = null,

    @Column(name = "model_provider")
    var modelProvider: String? = null,

    @Column(name = "model_name")
    var modelName: String? = null,

    @Column(name = "latency_ms")
    var latencyMs: Int? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
