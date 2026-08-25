package com.sysdrill.backend.build

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
@Table(name = "build_stages")
class BuildStage(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "challenge_id", nullable = false)
    var challengeId: UUID,

    @Column(name = "stage_order", nullable = false)
    var stageOrder: Int,

    @Column(nullable = false)
    var title: String,

    @Column(columnDefinition = "text")
    var spec: String? = null,

    @Column(name = "test_script", nullable = false, columnDefinition = "text")
    var testScript: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
