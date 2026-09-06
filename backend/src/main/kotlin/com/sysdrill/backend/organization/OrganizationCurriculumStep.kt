package com.sysdrill.backend.organization

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

/** PLAN.md step 39 — one ordered step of an organization's single onboarding curriculum (docs/adr/0030: advisory, not gated). */
@Entity
@Table(name = "organization_curriculum_steps")
class OrganizationCurriculumStep(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "organization_id", nullable = false)
    var organizationId: UUID,

    @Column(name = "scenario_id", nullable = false)
    var scenarioId: UUID,

    @Column(name = "step_order", nullable = false)
    var stepOrder: Int,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
