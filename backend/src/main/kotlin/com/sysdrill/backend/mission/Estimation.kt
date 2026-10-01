package com.sysdrill.backend.mission

import kotlin.math.abs
import kotlin.math.log10

/**
 * docs/DRILLS_EXPANSION_PLAN.md M2 · docs/LEARNING_EXPANSION_PLAN.md L6 (PLAN.md Round E9) —
 * back-of-the-envelope estimation is judged on **order of magnitude**, not the exact
 * number: within a factor of two (|log10(estimate / truth)| ≤ 0.3) counts as on target.
 * One judge for both the Drill's estimation step and the Learning Capacity Lab, so
 * "practise here, use it there" means the same rule.
 */
object EstimateJudge {

    const val TOLERANCE_LOG10 = 0.3

    fun judge(key: String, estimate: Double?, truth: Double): EstimateResult {
        if (estimate == null || estimate <= 0.0 || truth <= 0.0) {
            return EstimateResult(key, estimate, truth, ratio = null, onTarget = false, direction = "MISSING")
        }
        val ratio = estimate / truth
        val off = log10(ratio)
        return EstimateResult(
            key = key,
            estimate = estimate,
            truth = truth,
            ratio = ratio,
            onTarget = abs(off) <= TOLERANCE_LOG10,
            direction = when {
                abs(off) <= TOLERANCE_LOG10 -> "ON_TARGET"
                off < 0 -> "UNDER"
                else -> "OVER"
            },
        )
    }
}

data class EstimateResult(
    val key: String,
    val estimate: Double?,
    val truth: Double,
    /** estimate / truth — 0.42 means "42% of the real value". */
    val ratio: Double?,
    val onTarget: Boolean,
    /** ON_TARGET / UNDER / OVER / MISSING. */
    val direction: String,
)

/** M2 — one quantity the learner estimates before designing (authored on the INITIAL step). */
data class EstimationField(
    val key: String,
    val label: String,
    val unit: String,
    /** The scenario's own value, worked out from its requirements. Never sent before the INITIAL submit. */
    val answer: Double,
    val hint: String? = null,
)
