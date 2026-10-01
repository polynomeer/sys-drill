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
)

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

@Service
class LearningLabService(
    private val labRepository: LearningLabRepository,
    private val mapper: ObjectMapper,
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
        val spec = engineSpec(lab)
        val knobs = spec.knobs.associateBy { it.trait }
        request.traits.forEach { (trait, value) ->
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
        val merged = traitDefaults() + request.traits.mapValues { (trait, value) ->
            if (knobs.getValue(trait).type == "number") (value as Number).toInt() else value
        }
        val traits = mapper.convertValue(merged, DesignTraits::class.java)
        val state = RuleBasedSimulationEngine.computeState(
            SimulationSessionState(LAB_SESSION_ID, lab.domain!!, request.incidentActive, traits),
        )
        return SystemStateResponse.from(state)
    }

    @Suppress("UNCHECKED_CAST")
    private fun traitDefaults(): Map<String, Any?> = mapper.convertValue(DesignTraits(), Map::class.java) as Map<String, Any?>

    private fun engineSpec(lab: LearningLab): EngineSpec =
        mapper.readerFor(EngineSpec::class.java).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(lab.spec)

    fun capacitySpec(lab: LearningLab): CapacitySpec =
        mapper.readerFor(CapacitySpec::class.java).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(lab.spec)

    private fun requireLab(slug: String, kind: String): LearningLab =
        labRepository.findBySlug(slug)?.takeIf { it.kind == kind } ?: throw NotFoundException("Lab not found: $slug")
}
