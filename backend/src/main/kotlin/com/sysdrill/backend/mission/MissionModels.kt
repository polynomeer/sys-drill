package com.sysdrill.backend.mission

/**
 * docs/DRILLS_EXPANSION_PLAN.md §5-1 — what `sessions.mission_state` holds.
 * Every field has a default so a session created before a field existed (or
 * one whose scenario has no mission content at all) reads as "nothing yet".
 * Later rounds add SLO (M3), alert rules (O5) and readiness (O7) here.
 */
data class MissionState(
    /** M1 — ids of the clarifying questions the learner opened, in the order asked. */
    val askedClarifications: List<String> = emptyList(),
    /**
     * The FOLLOWUP variant this session was given, pinned when it entered FOLLOWUP.
     * Before PLAN.md Round E8 the variant was re-picked on every read and could
     * change after an evaluation updated the learner's weakness counts.
     */
    val followupVariantKey: String? = null,
    /** M3 (PLAN.md Round E12) — the learner's own SLO; null until set (the defaults below apply). */
    val slo: SloTargets? = null,
    /** O5 (PLAN.md Round E12) — alert rules, evaluated over the incident's time series. */
    val alertRules: List<AlertRule> = emptyList(),
)

/** docs/DRILLS_EXPANSION_PLAN.md M3 — what "healthy" means for this system, set before the incident. */
data class SloTargets(
    val availabilityPct: Double = 99.9,
    val p95Ms: Double = 500.0,
    val errorRatePct: Double = 1.0,
)

/**
 * docs/OBSERVABILITY_UI_PLAN.md O5 — "[metric] [op] [threshold] for [forSeconds]".
 * [createdAt] matters: a rule only fires from the moment it existed, so writing one
 * after watching the incident unfold can't buy a short detection delay.
 */
data class AlertRule(
    val id: String,
    val metric: String,
    val op: String,
    val threshold: Double,
    val forSeconds: Int,
    val severity: String = "WARN",
    val createdAt: java.time.Instant? = null,
)

/**
 * docs/DRILLS_EXPANSION_PLAN.md M1 — one clarifying question on the INITIAL step.
 * [critical] questions are the ones a good design can't skip; the rest are
 * plausible but secondary. [requirementKey] names the `baseRequirements.nonFunctional`
 * key this question reveals, so the public overview doesn't give it away up front.
 */
data class Clarification(
    val id: String,
    val question: String,
    val answer: String,
    val critical: Boolean = false,
    val requirementKey: String? = null,
)

/** The mission add-ons authored on a scenario's INITIAL step `content` (all optional). */
data class InitialMissionContent(
    val clarifications: List<Clarification> = emptyList(),
    /** M2 (PLAN.md Round E9). */
    val estimation: List<EstimationField> = emptyList(),
)
