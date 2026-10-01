package com.sysdrill.backend.learning

import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.mission.EstimateJudge
import com.sysdrill.backend.mission.EstimateResult
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

    fun capacitySpec(lab: LearningLab): CapacitySpec =
        mapper.readerFor(CapacitySpec::class.java).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(lab.spec)

    private fun requireLab(slug: String, kind: String): LearningLab =
        labRepository.findBySlug(slug)?.takeIf { it.kind == kind } ?: throw NotFoundException("Lab not found: $slug")
}
