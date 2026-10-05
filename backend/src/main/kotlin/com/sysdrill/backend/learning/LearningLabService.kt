package com.sysdrill.backend.learning

import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.mission.EstimateJudge
import com.sysdrill.backend.mission.EstimateResult
import com.sysdrill.backend.simulation.DesignTraits
import com.sysdrill.backend.simulation.RuleBasedSimulationEngine
import com.sysdrill.backend.simulation.SimulationSessionState
import com.sysdrill.backend.simulation.SystemStateResponse
import org.springframework.stereotype.Service
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper

/** Capacity Lab spec (V58) — see [CapacityFormula] for how answers are computed. */
data class CapacitySpec(
    val inputs: List<CapacityInput> = emptyList(),
    val asks: List<CapacityAsk> = emptyList(),
    val variants: List<CapacityVariant> = listOf(CapacityVariant("기본")),
)

data class CapacityInput(val key: String, val label: String, val value: Double, val unit: String)
data class CapacityAsk(val key: String, val label: String, val unit: String, val formula: String)
data class CapacityVariant(val label: String, val overrides: Map<String, Double> = emptyMap())

data class LabSummary(
    val slug: String,
    val kind: String,
    val title: String,
    val summary: String,
    val riskKey: String?,
    val domain: String?,
)

/** docs/LEARNING_EXPANSION_PLAN.md L5 — ENGINE lab spec (V59). */
data class EngineSpec(
    val knobs: List<EngineKnob> = emptyList(),
    val watch: List<String> = emptyList(),
    val predict: List<String> = emptyList(),
    /** docs/LEARNING_DEEPENING_PLAN.md L15 — targets to reach with the knobs; each is proven reachable by LabGoalTest. */
    val goals: List<LabGoal> = emptyList(),
    /** Other concepts (riskKeys) this lab exercises besides its own `risk_key` — the concept page lists it for them too. */
    val concepts: List<String> = emptyList(),
)

/**
 * A lab target: every condition must hold at once. A condition reads either a [GoalCondition.metric]
 * of the engine's state or a [GoalCondition.trait] the knobs set ("pool at most 80" — the cost side).
 */
data class LabGoal(
    val id: String,
    val label: String,
    val conditions: List<GoalCondition>,
    val incident: Boolean = true,
    val hint: String = "",
)

/** [op]: `<=`, `>=`, `<`, `>` or `==` (booleans use `==`). */
data class GoalCondition(val metric: String? = null, val trait: String? = null, val op: String, val value: Any) {
    fun holds(actual: Any?): Boolean = when (op) {
        "==" -> if (value is Number && actual is Number) value.toDouble() == actual.toDouble() else value == actual
        else -> {
            val a = (actual as? Number)?.toDouble() ?: return false
            val v = (value as Number).toDouble()
            when (op) {
                "<=" -> a <= v
                ">=" -> a >= v
                "<" -> a < v
                ">" -> a > v
                else -> error("Unknown goal operator $op")
            }
        }
    }
}

data class EngineKnob(
    /** A [DesignTraits] field name. */
    val trait: String,
    val label: String,
    /** "number" or "boolean". */
    val type: String,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    /** Filled from [DesignTraits]' defaults when serving the view — the incident's own starting point. */
    val default: Any? = null,
)

data class EngineLabView(
    val slug: String,
    val title: String,
    val summary: String,
    val riskKey: String?,
    val domain: String,
    val knobs: List<EngineKnob>,
    val watch: List<String>,
    val predict: List<String>,
    val goals: List<LabGoal> = emptyList(),
)

/** L15 sweep — one numeric [knob] across its range, every other knob as given. */
data class EngineSweepRequest(
    val traits: Map<String, Any?> = emptyMap(),
    val incidentActive: Boolean = true,
    val knob: String,
)

data class EngineSweepResult(val knob: String, val points: List<EngineSweepPoint>)
data class EngineSweepPoint(val value: Int, val state: SystemStateResponse)

/** L15 replay — the incident from the default design, switching to [traits] at each of [applyAt] seconds. */
data class EngineReplayRequest(
    val traits: Map<String, Any?> = emptyMap(),
    val applyAt: List<Long> = listOf(120),
    val durationSeconds: Long = 300,
)

data class EngineRunRequest(
    val traits: Map<String, Any?> = emptyMap(),
    /** "Break": the same settings with the domain's incident on. */
    val incidentActive: Boolean = true,
)

/** A Capacity Lab problem as shown before checking — formulas stay server-side until then. */
data class CapacityLabView(
    val slug: String,
    val title: String,
    val summary: String,
    val variants: List<CapacityVariantView>,
    val asks: List<CapacityAskView>,
)

data class CapacityVariantView(val label: String, val inputs: List<CapacityInput>)
data class CapacityAskView(val key: String, val label: String, val unit: String)

data class CapacityCheckRequest(val variant: Int = 0, val answers: Map<String, Double?> = emptyMap())

data class CapacityCheckResult(
    val variant: Int,
    val results: List<CapacityAskResult>,
)

data class CapacityAskResult(
    val key: String,
    val label: String,
    val unit: String,
    /** Revealed once checked — how the value is worked out. */
    val formula: String,
    val result: EstimateResult,
)

/**
 * docs/LEARNING_EXPANSION_PLAN.md L6 (PLAN.md Round E9) — Capacity Lab. Judged with the
 * same [EstimateJudge] as the Drill's estimation step (M2). Nothing is stored — the
 * lab is practice, mastery is still only derived from drills (L5 §3-4).
 */
/** The rule-based engine never reads the session id; labs have none. */
private val LAB_SESSION_ID: java.util.UUID = java.util.UUID(0, 0)

/** A sweep draws at most this many points — enough for a curve, cheap to compute. */
private const val SWEEP_POINTS = 41

@Service
class LearningLabService(
    private val labRepository: LearningLabRepository,
    private val mapper: ObjectMapper,
    private val contentBlocks: ContentBlockService,
) {
    fun list(): List<LabSummary> = labRepository.findAllByOrderByDisplayOrderAsc().map {
        LabSummary(it.slug, it.kind, it.title, it.summary, it.riskKey, it.domain)
    }

    fun capacity(slug: String): CapacityLabView {
        val lab = requireLab(slug, "CAPACITY")
        val spec = capacitySpec(lab)
        return CapacityLabView(
            slug = lab.slug,
            title = lab.title,
            summary = lab.summary,
            variants = spec.variants.map { v -> CapacityVariantView(v.label, inputsFor(spec, v)) },
            asks = spec.asks.map { CapacityAskView(it.key, it.label, it.unit) },
        )
    }

    fun checkCapacity(slug: String, request: CapacityCheckRequest): CapacityCheckResult {
        val spec = capacitySpec(requireLab(slug, "CAPACITY"))
        val variant = spec.variants.getOrNull(request.variant) ?: throw BadRequestException("Unknown variant: ${request.variant}")
        val truths = truths(spec, variant)
        return CapacityCheckResult(
            variant = request.variant,
            results = spec.asks.map { ask ->
                CapacityAskResult(ask.key, ask.label, ask.unit, ask.formula, EstimateJudge.judge(ask.key, request.answers[ask.key], truths.getValue(ask.key)))
            },
        )
    }

    /** Each ask in order; later asks may reference earlier ones (e.g. peak = average × factor). */
    fun truths(spec: CapacitySpec, variant: CapacityVariant): Map<String, Double> {
        val vars = inputsFor(spec, variant).associate { it.key to it.value }.toMutableMap()
        spec.asks.forEach { ask -> vars[ask.key] = CapacityFormula.evaluate(ask.formula, vars) }
        return vars
    }

    private fun inputsFor(spec: CapacitySpec, variant: CapacityVariant): List<CapacityInput> =
        spec.inputs.map { input -> variant.overrides[input.key]?.let { input.copy(value = it) } ?: input }

    // ---- L5: ENGINE labs (ADR-0047) ----

    fun engine(slug: String): EngineLabView {
        val lab = requireLab(slug, "ENGINE")
        val spec = engineSpec(lab)
        val defaults = traitDefaults()
        return EngineLabView(
            slug = lab.slug,
            title = lab.title,
            summary = lab.summary,
            riskKey = lab.riskKey,
            domain = lab.domain ?: error("ENGINE lab ${lab.slug} has no domain"),
            knobs = spec.knobs.map { it.copy(default = defaults[it.trait]) },
            watch = spec.watch,
            predict = spec.predict,
            goals = spec.goals,
        )
    }

    /**
     * Runs the domain's rule-based formula with the lab's knobs applied — no session,
     * nothing stored. Only the lab's own knobs may be set, within their ranges; every
     * other trait stays at the incident's default, so the lab shows exactly what the
     * Drill's incident would.
     */
    fun run(slug: String, request: EngineRunRequest): SystemStateResponse {
        val lab = requireLab(slug, "ENGINE")
        val traits = labTraits(engineSpec(lab), request.traits)
        return SystemStateResponse.from(compute(lab.domain!!, traits, request.incidentActive))
    }

    /** L15 — the watched metrics across one numeric knob's whole range (at most [SWEEP_POINTS] points). */
    fun sweep(slug: String, request: EngineSweepRequest): EngineSweepResult {
        val lab = requireLab(slug, "ENGINE")
        val spec = engineSpec(lab)
        val knob = spec.knobs.firstOrNull { it.trait == request.knob && it.type == "number" }
            ?: throw BadRequestException("This lab has no numeric knob '${request.knob}'")
        val points = sweepValues(knob).map { value ->
            val traits = labTraits(spec, request.traits + (knob.trait to value))
            EngineSweepPoint(value, SystemStateResponse.from(compute(lab.domain!!, traits, request.incidentActive)))
        }
        return EngineSweepResult(knob.trait, points)
    }

    /**
     * L15 — the incident on the engine's clock (TelemetrySampler), once left alone and once per
     * [EngineReplayRequest.applyAt] with the lab's settings switched in at that second: applying late
     * shows what the wait cost (backlog piled up meanwhile). Same resolver as the timeline content block.
     */
    fun replay(slug: String, request: EngineReplayRequest): TimelineBlock {
        val lab = requireLab(slug, "ENGINE")
        val spec = engineSpec(lab)
        labTraits(spec, request.traits) // validates the knobs
        if (request.applyAt.isEmpty() || request.applyAt.size > 3 || request.applyAt.any { it !in 0..600 }) {
            throw BadRequestException("applyAt: one to three seconds between 0 and 600")
        }
        if (request.durationSeconds !in 60..900) throw BadRequestException("durationSeconds must be between 60 and 900")
        val settings = request.traits.filterValues { it != null }.mapValues { (trait, value) ->
            if (spec.knobs.first { it.trait == trait }.type == "number") (value as Number).toInt() else value!!
        }
        val sorted = request.applyAt.distinct().sorted()
        val block = TimelineBlock(
            domain = lab.domain!!,
            durationSeconds = request.durationSeconds,
            stepSeconds = if (request.durationSeconds > 300) 5 else 2,
            metrics = spec.watch,
            scenarios = sorted.mapIndexed { i, at ->
                TimelineBlock.Scenario(label = "${at}초에 적용", tone = if (i == 0) "good" else "bad", switchAt = at, switchTraits = settings)
            },
        )
        return contentBlocks.resolveTimeline(block)
    }

    /** Whether [goal] holds for these knob settings — the same check the lab page makes. */
    fun goalMet(slug: String, goal: LabGoal, settings: Map<String, Any?>): Boolean {
        val lab = requireLab(slug, "ENGINE")
        val spec = engineSpec(lab)
        val traits = labTraits(spec, settings)
        @Suppress("UNCHECKED_CAST")
        val state = mapper.convertValue(SystemStateResponse.from(compute(lab.domain!!, traits, goal.incident)), Map::class.java) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val traitMap = mapper.convertValue(traits, Map::class.java) as Map<String, Any?>
        return goal.conditions.all { c ->
            when {
                c.metric != null -> c.holds(state[c.metric] ?: error("Unknown metric '${c.metric}' in goal ${goal.id}"))
                c.trait != null -> c.holds(traitMap[c.trait] ?: error("Unknown trait '${c.trait}' in goal ${goal.id}"))
                else -> error("Goal ${goal.id} has a condition on neither a metric nor a trait")
            }
        }
    }

    fun sweepValues(knob: EngineKnob): List<Int> {
        val min = (knob.min ?: 0.0).toInt()
        val max = (knob.max ?: 100.0).toInt()
        val step = maxOf(1, (knob.step ?: 1.0).toInt())
        val all = (min..max step step).toList().let { if (it.last() != max) it + max else it }
        if (all.size <= SWEEP_POINTS) return all
        return (0 until SWEEP_POINTS).map { i -> all[(i * (all.size - 1)) / (SWEEP_POINTS - 1)] }.distinct()
    }

    /** The lab's knobs on top of the incident's defaults, each checked against the knob's type and range. */
    private fun labTraits(spec: EngineSpec, settings: Map<String, Any?>): DesignTraits {
        val knobs = spec.knobs.associateBy { it.trait }
        settings.forEach { (trait, value) ->
            val knob = knobs[trait] ?: throw BadRequestException("This lab has no knob '$trait'")
            when (knob.type) {
                "boolean" -> if (value !is Boolean) throw BadRequestException("'$trait' must be true or false")
                else -> {
                    val number = (value as? Number)?.toDouble() ?: throw BadRequestException("'$trait' must be a number")
                    if ((knob.min != null && number < knob.min) || (knob.max != null && number > knob.max)) {
                        throw BadRequestException("'$trait' must be between ${knob.min} and ${knob.max}")
                    }
                }
            }
        }
        val merged = traitDefaults() + settings.mapValues { (trait, value) ->
            if (knobs.getValue(trait).type == "number") (value as Number).toInt() else value
        }
        return mapper.convertValue(merged, DesignTraits::class.java)
    }

    private fun compute(domain: String, traits: DesignTraits, incident: Boolean) =
        RuleBasedSimulationEngine.computeState(SimulationSessionState(LAB_SESSION_ID, domain, incident, traits))

    @Suppress("UNCHECKED_CAST")
    private fun traitDefaults(): Map<String, Any?> = mapper.convertValue(DesignTraits(), Map::class.java) as Map<String, Any?>

    private fun engineSpec(lab: LearningLab): EngineSpec =
        mapper.readerFor(EngineSpec::class.java).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(lab.spec)

    fun engineSpecOf(slug: String): EngineSpec = engineSpec(requireLab(slug, "ENGINE"))

    fun capacitySpec(lab: LearningLab): CapacitySpec =
        mapper.readerFor(CapacitySpec::class.java).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(lab.spec)

    private fun requireLab(slug: String, kind: String): LearningLab =
        labRepository.findBySlug(slug)?.takeIf { it.kind == kind } ?: throw NotFoundException("Lab not found: $slug")
}
