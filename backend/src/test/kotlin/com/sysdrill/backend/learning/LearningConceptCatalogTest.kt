package com.sysdrill.backend.learning

import com.sysdrill.backend.evaluation.RuleEvaluator
import com.sysdrill.backend.simulation.SimulationActionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * ADR-0039 가 약속한 검증 — 시드된 개념 카탈로그가 채점 엔진과 어긋나지
 * 않는지 확인한다.
 *
 * 이 테스트가 없으면 어긋남이 조용히 진행된다: `RuleEvaluator` 에 riskKey 를
 * 하나 추가해도 아무것도 깨지지 않고, 다만 사용자가 그 개념을 지적받은 뒤
 * Learning 에서 찾으면 없을 뿐이다. 실패 메시지가 무엇을 해야 하는지 바로
 * 알려주도록 차집합을 양방향으로 확인한다.
 */
@SpringBootTest
class LearningConceptCatalogTest(
    @Autowired val conceptRepository: LearningConceptRepository,
) {

    private val seeded by lazy { conceptRepository.findAll().associateBy { it.riskKey } }

    @Test
    fun `채점 엔진의 riskKey 와 개념 카탈로그가 1대1로 대응한다`() {
        val scoringKeys = RuleEvaluator.categoryByRiskKey.keys

        assertThat(seeded.keys)
            .describedAs("RuleEvaluator 에는 있는데 learning_concepts 에 없는 riskKey — 시드 마이그레이션을 추가하세요")
            .containsAll(scoringKeys)
        assertThat(scoringKeys)
            .describedAs("learning_concepts 에는 있는데 RuleEvaluator 가 더 이상 쓰지 않는 riskKey — 개념을 지우거나 채점 규칙을 되살리세요")
            .containsAll(seeded.keys)
    }

    @Test
    fun `개념의 카테고리는 채점 엔진의 분류와 일치한다`() {
        // learning_concepts.category 는 categoryByRiskKey 의 복제본이 아니라 그것으로부터
        // 채운 값이다(ADR-0039). 복제인 이상 어긋날 수 있으므로 여기서 고정한다.
        val mismatched = seeded.values
            .filter { it.category != RuleEvaluator.categoryByRiskKey[it.riskKey] }
            .map { "${it.riskKey}: seeded=${it.category}, scoring=${RuleEvaluator.categoryByRiskKey[it.riskKey]}" }

        assertThat(mismatched).describedAs("카테고리 불일치").isEmpty()
    }

    @Test
    fun `모든 개념이 설명 필드를 비워두지 않는다`() {
        val incomplete = seeded.values.filter {
            it.label.isBlank() || it.summary.isBlank() || it.whyItMatters.isBlank() ||
                it.tradeoffs.isBlank() || it.symptoms.isEmpty() || it.patterns.isEmpty()
        }.map { it.riskKey }

        assertThat(incomplete).describedAs("필수 설명이 빈 개념").isEmpty()
    }

    @Test
    fun `연결된 액션은 실제 SimulationActionType 이다`() {
        val valid = SimulationActionType.entries.map { it.name }.toSet()
        val unknown = seeded.values.flatMap { c -> c.relatedActions.map { c.riskKey to it } }
            .filterNot { (_, action) -> action in valid }

        assertThat(unknown).describedAs("존재하지 않는 액션을 가리키는 개념").isEmpty()
    }

    @Test
    fun `연결된 도메인은 실제 시나리오 도메인이다`() {
        val valid = setOf(
            "coupon", "notification", "product-browsing",
            "payment", "reservation", "batch-settlement", "autoscaling",
        )
        val unknown = seeded.values.flatMap { c -> c.relatedDomains.map { c.riskKey to it } }
            .filterNot { (_, domain) -> domain in valid }

        assertThat(unknown).describedAs("존재하지 않는 도메인을 가리키는 개념").isEmpty()
    }

    @Test
    fun `지식 맵 엣지는 실제 개념을 가리키고 모든 개념이 연결되며 선행 관계에 순환이 없다`() {
        val refs = seeded.values.flatMap { c -> c.relatedConcepts.map { Triple(c.riskKey, it["key"], it["relation"]) } }
        assertThat(refs.filter { it.second !in seeded.keys }).describedAs("없는 개념을 가리키는 엣지").isEmpty()
        assertThat(refs.map { it.third }.toSet()).isSubsetOf("PREREQUISITE", "RELATED")

        val connected = refs.flatMap { listOf(it.first, it.second) }.toSet()
        assertThat(seeded.keys - connected).describedAs("지도에서 고립된 개념 — V62 같은 시드로 엣지를 추가하세요").isEmpty()

        // PREREQUISITE: key → concept. A cycle would make "먼저 볼 개념" meaningless.
        val next = refs.filter { it.third == "PREREQUISITE" }.groupBy({ it.second!! }, { it.first })
        fun reaches(from: String, target: String, seen: MutableSet<String> = mutableSetOf()): Boolean =
            next[from].orEmpty().any { it == target || (seen.add(it) && reaches(it, target, seen)) }
        assertThat(seeded.keys.filter { reaches(it, it) }).describedAs("선행 관계 순환").isEmpty()
    }
}
