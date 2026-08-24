package com.sysdrill.backend.scenario

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "scenarios")
class Scenario(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "content_id", nullable = false)
    var contentId: UUID,

    @Column(nullable = false)
    var domain: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "base_requirements", columnDefinition = "jsonb")
    var baseRequirements: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scoring_profile", columnDefinition = "jsonb")
    var scoringProfile: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
