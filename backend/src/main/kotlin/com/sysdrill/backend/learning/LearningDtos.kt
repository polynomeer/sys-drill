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
)
