package com.sysdrill.backend.scenario

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
@Table(name = "scenario_steps")
class ScenarioStep(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "scenario_version_id", nullable = false)
    var scenarioVersionId: UUID,

    @Column(name = "step_order", nullable = false)
    var stepOrder: Int,

    @Column(name = "step_type", nullable = false)
    var stepType: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_condition", columnDefinition = "jsonb")
    var triggerCondition: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var content: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
