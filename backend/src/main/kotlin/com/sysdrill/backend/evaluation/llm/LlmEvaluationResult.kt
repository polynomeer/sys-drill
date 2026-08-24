package com.sysdrill.backend.evaluation.llm

/** The structured schema the system prompt (see V5 migration) instructs the model to return. */
data class LlmEvaluationResult(
    val totalScore: Int = 0,
    val rubricScores: Map<String, Int> = emptyMap(),
    val strengths: List<String> = emptyList(),
    val missedPoints: List<String> = emptyList(),
    val topRisks: List<String> = emptyList(),
    val followupQuestions: List<String> = emptyList(),
    val recommendedChanges: List<String> = emptyList(),
)
