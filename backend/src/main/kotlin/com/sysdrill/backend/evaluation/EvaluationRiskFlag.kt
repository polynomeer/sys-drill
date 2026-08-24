package com.sysdrill.backend.evaluation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "evaluation_risk_flags")
class EvaluationRiskFlag(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "evaluation_id", nullable = false)
    var evaluationId: UUID,

    @Column(name = "risk_key", nullable = false)
    var riskKey: String,

    @Column(nullable = false)
    var severity: String,

    @Column(columnDefinition = "text")
    var description: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
