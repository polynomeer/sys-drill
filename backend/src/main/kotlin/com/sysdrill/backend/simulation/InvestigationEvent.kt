package com.sysdrill.backend.simulation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

/**
 * docs/OBSERVABILITY_UI_PLAN.md O0-b (PLAN.md Round E17) — one look during an incident:
 * a panel opened, a service-map node inspected, a log search. Never changes the
 * simulation and never scores anything; the postmortem shows it next to the actions.
 */
@Entity
@Table(name = "investigation_events")
class InvestigationEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    /** OPEN_PANEL / INSPECT_NODE / QUERY_LOGS / OPEN_TRACE. */
    @Column(nullable = false)
    var kind: String,

    var target: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)

interface InvestigationEventRepository : JpaRepository<InvestigationEvent, UUID> {
    fun findBySessionIdOrderByCreatedAtAsc(sessionId: UUID): List<InvestigationEvent>
    fun findFirstBySessionIdAndKindAndTargetOrderByCreatedAtDesc(sessionId: UUID, kind: String, target: String?): InvestigationEvent?
}

enum class InvestigationKind { OPEN_PANEL, INSPECT_NODE, QUERY_LOGS, OPEN_TRACE }

data class RecordInvestigationRequest(
    val kind: InvestigationKind,
    @field:jakarta.validation.constraints.Size(max = 200)
    val target: String? = null,
)

data class InvestigationEventResponse(val kind: String, val target: String?, val at: Instant)
