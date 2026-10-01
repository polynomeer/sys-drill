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
