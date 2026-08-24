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
@Table(name = "prompt_templates")
class PromptTemplate(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false)
    var purpose: String,

    @Column(nullable = false)
    var version: Int,

    @Column(name = "template_body", nullable = false, columnDefinition = "text")
    var templateBody: String,

    @Column(nullable = false)
    var active: Boolean = false,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
)
