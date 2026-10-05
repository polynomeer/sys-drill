package com.sysdrill.backend.learning

import com.sysdrill.backend.common.web.NotFoundException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service

/**
 * docs/LEARNING_EXPANSION_PLAN.md L8 (PLAN.md Round E19) — one incident pattern, keyed by the
 * incident domain it is 1:1 with. Concepts are design risks (keyed by riskKey); patterns are what
 * production looks like when those risks fire — a different axis, so a different table.
 */
@Entity
@Table(name = "failure_patterns")
class FailurePattern(
    @Id
    var domain: String = "",
    var name: String = "",
    var summary: String = "",
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var symptoms: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "typical_metrics", columnDefinition = "jsonb") var typicalMetrics: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "typical_logs", columnDefinition = "jsonb") var typicalLogs: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "common_causes", columnDefinition = "jsonb") var commonCauses: List<String> = emptyList(),
    /** `[{fix, why}]` — the engine's own action side effects in words (ARCHITECTURE §6.4). */
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "bad_fixes", columnDefinition = "jsonb") var badFixes: List<Map<String, String>> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var mitigations: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var prevention: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "related_concepts", columnDefinition = "jsonb") var relatedConcepts: List<String> = emptyList(),
    @Column(name = "display_order") var displayOrder: Int = 0,
    /** docs/LEARNING_DEEPENING_PLAN.md L12 — see [ContentBlock]. */
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") var blocks: List<Map<String, Any?>> = emptyList(),
)

interface FailurePatternRepository : JpaRepository<FailurePattern, String> {
    fun findAllByOrderByDisplayOrderAsc(): List<FailurePattern>
}

data class FailurePatternSummary(val domain: String, val name: String, val summary: String, val symptoms: List<String>)

data class BadFix(val fix: String, val why: String)

data class FailurePatternDetail(
    val domain: String,
    val name: String,
    val summary: String,
    val symptoms: List<String>,
    val typicalMetrics: List<String>,
    val typicalLogs: List<String>,
    val commonCauses: List<String>,
    val badFixes: List<BadFix>,
    val mitigations: List<String>,
    val prevention: List<String>,
    val relatedConcepts: List<RelatedConceptLink>,
    val blocks: List<ContentBlock> = emptyList(),
)

@Service
class FailurePatternService(
    private val repository: FailurePatternRepository,
    private val conceptRepository: LearningConceptRepository,
    private val contentBlockService: ContentBlockService,
) {
    fun list(): List<FailurePatternSummary> =
        repository.findAllByOrderByDisplayOrderAsc().map { FailurePatternSummary(it.domain, it.name, it.summary, it.symptoms) }

    fun detail(domain: String): FailurePatternDetail {
        val p = repository.findById(domain).orElseThrow { NotFoundException("No failure pattern for $domain") }
        val labels = conceptRepository.findAllById(p.relatedConcepts).associate { it.riskKey to it.label }
        return FailurePatternDetail(
            domain = p.domain,
            name = p.name,
            summary = p.summary,
            symptoms = p.symptoms,
            typicalMetrics = p.typicalMetrics,
            typicalLogs = p.typicalLogs,
            commonCauses = p.commonCauses,
            badFixes = p.badFixes.map { BadFix(it["fix"].orEmpty(), it["why"].orEmpty()) },
            mitigations = p.mitigations,
            prevention = p.prevention,
            relatedConcepts = p.relatedConcepts.mapNotNull { key -> labels[key]?.let { RelatedConceptLink(key, it, "RELATED") } },
            blocks = contentBlockService.resolve(p.blocks),
        )
    }
}
