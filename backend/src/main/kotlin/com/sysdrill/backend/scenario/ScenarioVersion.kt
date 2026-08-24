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
@Table(name = "scenario_versions")
class ScenarioVersion(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "scenario_id", nullable = false)
    var scenarioId: UUID,

    @Column(name = "version_no", nullable = false)
    var versionNo: Int,

    @Column(nullable = false)
    var status: String = "DRAFT",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "followup_rules", columnDefinition = "jsonb")
    var followupRules: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "incident_rules", columnDefinition = "jsonb")
    var incidentRules: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
