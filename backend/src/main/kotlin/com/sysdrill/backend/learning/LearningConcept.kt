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
)

interface LearningConceptRepository : JpaRepository<LearningConcept, String> {
    fun findAllByOrderByCategoryAscDisplayOrderAscLabelAsc(): List<LearningConcept>
}
