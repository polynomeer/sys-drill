package com.sysdrill.backend.simulation

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

/**
 * ADR-0037's next slice (persistence-only, PLAN.md "Drills 고도화") — the
 * Architecture Canvas's node/edge graph, previously only in a per-browser-tab
 * localStorage draft ([DiagramCanvas.tsx]'s `saveCanvasDraft`). [graph] is an
 * opaque JSON blob (the same `{nodes, edges}` shape the frontend already
 * serializes) — this entity deliberately does not model nodes as separate
 * rows, since nothing server-side queries per-node yet (the simulation
 * engine still reads [DesignTraits] via Slice 1, unchanged). One row per
 * session, same shape as [com.sysdrill.backend.postmortem.Postmortem].
 */
@Entity
@Table(name = "system_topologies")
class SystemTopology(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "session_id", nullable = false, unique = true)
    var sessionId: UUID,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var graph: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
