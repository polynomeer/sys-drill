package com.sysdrill.backend.postmortem

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
@Table(name = "postmortems")
class Postmortem(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "session_id", nullable = false, unique = true)
    var sessionId: UUID,

    @Column(name = "root_cause", nullable = false, columnDefinition = "text")
    var rootCause: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mitigation_actions", nullable = false, columnDefinition = "jsonb")
    var mitigationActions: String = "[]",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "root_fix_actions", nullable = false, columnDefinition = "jsonb")
    var rootFixActions: String = "[]",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prevention_items", nullable = false, columnDefinition = "jsonb")
    var preventionItems: String = "[]",

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
