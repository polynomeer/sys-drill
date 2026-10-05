package com.sysdrill.backend.learning

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository

/**
 * docs/LEARNING_COMMUNITY_PLAN.md §5.2 / [ADR-0039] — Learning 개념 라이브러리의
 * 한 항목.
 *
 * **식별자가 `riskKey` 다.** 대리키를 두지 않은 것은 의도다: 이 행들은
 * `RuleEvaluator` 가 채점에 쓰는 riskKey 와 1:1 로 대응해야 하고, 그 대응이
 * 깨지면 "피드백에서 지적받은 개념"과 "학습 화면의 개념"이 서로 다른 것을
 * 가리키게 된다. [LearningConceptCatalogTest] 가 두 목록의 일치를 강제한다.
 *
 * [category] 는 `RuleEvaluator.categoryByRiskKey` 를 복제한 것이 아니라 그것으로
 * 부터 채운 값이다 — 매핑 자체는 채점 로직이므로 코드에 남는다(ADR-0039).
 */
@Entity
@Table(name = "learning_concepts")
class LearningConcept(
    @Id
    @Column(name = "risk_key", nullable = false)
    var riskKey: String = "",

    @Column(nullable = false)
    var category: String = "",

    @Column(nullable = false)
    var label: String = "",

    @Column(nullable = false)
    var summary: String = "",

    @Column(name = "why_it_matters", nullable = false)
    var whyItMatters: String = "",

    /** 관측 가능한 증상 — 이 제품은 개념 암기가 아니라 "지표를 보고 알아채기"를 훈련시킨다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var symptoms: List<String> = emptyList(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var patterns: List<String> = emptyList(),

    @Column(nullable = false)
    var tradeoffs: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_domains", nullable = false, columnDefinition = "jsonb")
    var relatedDomains: List<String> = emptyList(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_actions", nullable = false, columnDefinition = "jsonb")
    var relatedActions: List<String> = emptyList(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_challenges", nullable = false, columnDefinition = "jsonb")
    var relatedChallenges: List<String> = emptyList(),

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    /**
     * PLAN.md Round E18 (L7) — knowledge-map edges as `[{key, relation}]`. PREREQUISITE: `key`
     * comes before this concept. RELATED: undirected, stored on one side only.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_concepts", nullable = false, columnDefinition = "jsonb")
    var relatedConcepts: List<Map<String, String>> = emptyList(),

    /** PLAN.md Round E19 (L8) — fixes that look right but aren't, and when this pattern is the wrong tool. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bad_fixes", nullable = false, columnDefinition = "jsonb")
    var badFixes: List<String> = emptyList(),

    @Column(name = "when_not_to_use", nullable = false)
    var whenNotToUse: String = "",

    /** docs/LEARNING_DEEPENING_PLAN.md L12 — diagrams, mechanism steps, engine-computed numbers ([ContentBlock]). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var blocks: List<Map<String, Any?>> = emptyList(),
)

interface LearningConceptRepository : JpaRepository<LearningConcept, String> {
    fun findAllByOrderByCategoryAscDisplayOrderAscLabelAsc(): List<LearningConcept>
}

/** Korean technical prose reads at roughly 500 characters a minute; never less than one minute. */
private const val READING_CHARS_PER_MINUTE = 500

/** Estimated reading time over every body field the concept page shows (summary through trade-offs). */
fun LearningConcept.readingMinutes(): Int {
    val chars = (listOf(summary, whyItMatters, tradeoffs) + symptoms + patterns).sumOf { it.length }
    return maxOf(1, Math.round(chars.toDouble() / READING_CHARS_PER_MINUTE).toInt())
}
