package com.sysdrill.backend.learning

/** 목록 화면용 요약 — 상세 필드(패턴·트레이드오프 등)는 [LearningConceptDetail] 에만 있다. */
data class LearningConceptSummary(
    val riskKey: String,
    val label: String,
    val summary: String,
    /**
     * 이 개념을 내가 지적받은 횟수. 이력이 없으면 0.
     * 학습 화면이 "누구에게나 같은 목록"에서 "내 약점이 표시된 목록"이 되는 지점이다.
     */
    val myWeaknessCount: Int,
    /** docs/CODECRAFTERS_BENCHMARK.md §3.7 — the domain track page lists concepts by domain without fetching every detail. */
    val relatedDomains: List<String> = emptyList(),
    /** docs/CODECRAFTERS_BENCHMARK.md §3.6 — estimated reading time of the full concept, see [LearningConcept.readingMinutes]. */
    val readingMinutes: Int = 1,
    /** PLAN.md Round E18 (L7) — derived from Drill results only; reading never changes it. */
    val mastery: MasteryLevel = MasteryLevel.NOT_STARTED,
)

data class LearningCategory(
    val category: String,
    val label: String,
    val concepts: List<LearningConceptSummary>,
    /** 이 카테고리에서 내가 지적받은 총 횟수 — 카테고리 정렬·강조에 쓴다. */
    val myWeaknessCount: Int,
)

data class LearningConceptDetail(
    val riskKey: String,
    val category: String,
    val categoryLabel: String,
    val label: String,
    val summary: String,
    val whyItMatters: String,
    val symptoms: List<String>,
    val patterns: List<String>,
    val tradeoffs: String,
    val relatedDomains: List<String>,
    val relatedActions: List<String>,
    val relatedChallenges: List<String>,
    val myWeaknessCount: Int,
    val readingMinutes: Int = 1,
    /** PLAN.md Round E18 (L7). */
    val mastery: MasteryLevel = MasteryLevel.NOT_STARTED,
    val cleanVariants: Int = 0,
    val relatedConcepts: List<RelatedConceptLink> = emptyList(),
    /** Labs (L5) that exercise this concept — the stage rail's Experiment/Break. */
    val labs: List<String> = emptyList(),
    /** PLAN.md Round E19 (L8). */
    val badFixes: List<String> = emptyList(),
    val whenNotToUse: String = "",
    /** Incident domains whose failure pattern lists this concept. */
    val failurePatterns: List<String> = emptyList(),
    /** docs/LEARNING_DEEPENING_PLAN.md L12 — engine values already filled in. */
    val blocks: List<ContentBlock> = emptyList(),
)

data class RelatedConceptLink(val riskKey: String, val label: String, val relation: String)

/** PLAN.md Round E18 (L7) — the knowledge map: 25 concepts and their edges, with my mastery on each node. */
data class KnowledgeMap(val nodes: List<KnowledgeMapNode>, val edges: List<KnowledgeMapEdge>)

data class KnowledgeMapNode(
    val riskKey: String,
    val label: String,
    val category: String,
    val categoryLabel: String,
    val mastery: MasteryLevel,
)

/** [relation] PREREQUISITE (source before target) or RELATED. */
data class KnowledgeMapEdge(val source: String, val target: String, val relation: String)

/** 학습 경로의 한 단계 상태 — 전부 기존 이력에서 파생한다(ADR-0011). */
enum class LearningStepStatus {
    /** 관련 도메인 세션 이력이 없다. */
    NOT_STARTED,

    /** 해봤지만 가장 최근 완료 세션에서 여전히 이 개념을 지적받았다. */
    IN_PROGRESS,

    /** 가장 최근 완료 세션에서 이 개념 지적이 없었다. */
    ADDRESSED,
}

data class LearningPathStep(
    val riskKey: String,
    val label: String,
    val summary: String,
    val status: LearningStepStatus,
    /** 누적 지적 횟수. */
    val weaknessCount: Int,
    /** 왜 이 상태인지 — 추천에 근거를 붙이는 것이 이 화면의 요점이다. */
    val evidence: String,
    val relatedDomains: List<String>,
    val relatedChallenges: List<String>,
    /** PLAN.md Round E18 — the 4-level mastery [status] is derived from. */
    val mastery: MasteryLevel = MasteryLevel.NOT_STARTED,
)

data class LearningPath(
    val recommendedCategory: String?,
    val categoryLabel: String?,
    /** 왜 이 역량을 골랐는지. 이력이 없으면 시작 안내 문구가 들어간다. */
    val rationale: String,
    val steps: List<LearningPathStep>,
)
