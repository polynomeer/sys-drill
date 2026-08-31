package com.sysdrill.backend.submission

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
@Table(name = "submissions")
class Submission(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Column(nullable = false)
    var phase: String,

    @Column(name = "raw_text", columnDefinition = "text")
    var rawText: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_json", columnDefinition = "jsonb")
    var structuredJson: String? = null,

    @Column(name = "revision_no", nullable = false)
    var revisionNo: Int = 1,

    // Uniqueness is enforced per-session, not globally — see
    // V3__scope_submission_client_request_id_per_session.sql.
    @Column(name = "client_request_id")
    var clientRequestId: String? = null,

    /** PLAN.md step 28 — null when the session isn't in interview-timer mode (not "false": not applicable, not late). */
    @Column(name = "on_time")
    var onTime: Boolean? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
