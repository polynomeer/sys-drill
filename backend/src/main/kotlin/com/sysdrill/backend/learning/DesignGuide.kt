package com.sysdrill.backend.learning

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.simulation.SystemStateResponse
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/**
 * docs/LEARNING_DEEPENING_PLAN.md L14 — a domain's design guide: what the service has to carry,
 * the base architecture, and a scenario chain where each step is
 * situation → signal → diagnosis → action (tune a value or change the design) → result and its
 * cost → the problem left over, which becomes the next step. Seeded by migration (ADR-0002).
 *
 * A step stores the design it starts from (`traits`) and the change it makes — never numbers.
 * The before/after states are computed by the rule engine when read, and each step's claims are
 * held against it by DesignGuideTest (ADR-0054). Steps chain: a step starts from the previous
 * step's design plus its change, which the test also enforces.
 */
@Entity
@Table(name = "design_guides")
class DesignGuide(
    @Id
    var domain: String = "",
    var title: String = "",
    var summary: String = "",
    @Column(name = "display_order") var displayOrder: Int = 0,
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var requirements: List<Map<String, Any?>> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var architecture: List<Map<String, Any?>> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var steps: List<Map<String, Any?>> = emptyList(),
    /** `[{item, where}]` — `where` is `requirements`, `architecture` or `step-N` (1-based). */
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var checklist: List<Map<String, String>> = emptyList(),
)

interface DesignGuideRepository : JpaRepository<DesignGuide, String> {
    fun findAllByOrderByDisplayOrderAsc(): List<DesignGuide>
}

/** One scenario step as seeded. */
data class GuideStepSpec(
    val title: String,
    val situation: String,
    val incident: Boolean = true,
    /** The design this step starts from — the previous step's traits plus its change. */
    val traits: Map<String, Any> = emptyMap(),
    val signal: String,
    val diagnosis: String,
    val concepts: List<String> = emptyList(),
    val action: Action,
    val metrics: List<String>,
    val claims: List<NumbersBlock.Claim>,
    val tradeoff: String = "",
    /** Extra content blocks for this step (diagrams, callouts…). */
    val blocks: List<Map<String, Any?>> = emptyList(),
    val links: Links = Links(),
) {
    /** [kind]: `TUNE` (change a value) or `REDESIGN` (change the design). */
    data class Action(val kind: String, val label: String, val change: Map<String, Any>, val why: String)
    data class Links(val labs: List<String> = emptyList(), val challenges: List<String> = emptyList(), val failurePattern: String? = null)
}

data class DesignGuideSummary(val domain: String, val title: String, val summary: String, val stepCount: Int)

data class GuideStepView(
    val title: String,
    val situation: String,
    val incident: Boolean,
    val signal: String,
    val diagnosis: String,
    val concepts: List<RelatedConceptLink>,
    val action: GuideStepSpec.Action,
    val metrics: Map<String, NumbersBlock.MetricPair>,
    val claims: List<NumbersBlock.Claim>,
    val before: SystemStateResponse,
    val after: SystemStateResponse,
    val tradeoff: String,
    val blocks: List<ContentBlock>,
    val links: GuideStepSpec.Links,
    /** Traits before and after the change — the page presets the lab with them. */
    val traitsBefore: Map<String, Any>,
    val traitsAfter: Map<String, Any>,
)

data class DesignGuideView(
    val domain: String,
    val title: String,
    val summary: String,
    val requirements: List<ContentBlock>,
    val architecture: List<ContentBlock>,
    val steps: List<GuideStepView>,
    val checklist: List<Map<String, String>>,
    /** The domain's failure-pattern Bad Fixes — the guide's "common mistakes". */
    val pitfalls: List<BadFix>,
)

@Service
class DesignGuideService(
    private val repository: DesignGuideRepository,
    private val conceptRepository: LearningConceptRepository,
    private val failurePatternRepository: FailurePatternRepository,
    private val blocks: ContentBlockService,
    private val mapper: ObjectMapper,
) {
    fun list(): List<DesignGuideSummary> =
        repository.findAllByOrderByDisplayOrderAsc().map { DesignGuideSummary(it.domain, it.title, it.summary, it.steps.size) }

    fun detail(domain: String): DesignGuideView {
        val guide = repository.findById(domain).orElseThrow { NotFoundException("No design guide for $domain") }
        val labels = conceptRepository.findAll().associate { it.riskKey to it.label }
        return DesignGuideView(
            domain = guide.domain,
            title = guide.title,
            summary = guide.summary,
            requirements = blocks.resolve(guide.requirements),
            architecture = blocks.resolve(guide.architecture),
            steps = steps(guide).map { step -> view(guide.domain, step, labels) },
            checklist = guide.checklist,
            pitfalls = failurePatternRepository.findById(domain).map { p -> p.badFixes.map { BadFix(it["fix"].orEmpty(), it["why"].orEmpty()) } }.orElse(emptyList()),
        )
    }

    fun steps(guide: DesignGuide): List<GuideStepSpec> = guide.steps.map { mapper.convertValue(it, GuideStepSpec::class.java) }

    /** The step's numbers as a resolved [NumbersBlock] — the same computation and claim check the content blocks use. */
    fun numbers(domain: String, step: GuideStepSpec): NumbersBlock = blocks.resolveNumbers(
        NumbersBlock(
            domain = domain,
            incident = step.incident,
            base = step.traits,
            change = step.action.change,
            changeLabel = step.action.label,
            metrics = step.metrics,
            claims = step.claims,
        ),
    )

    private fun view(domain: String, step: GuideStepSpec, labels: Map<String, String>): GuideStepView {
        val after = step.traits + step.action.change
        return GuideStepView(
            title = step.title,
            situation = step.situation,
            incident = step.incident,
            signal = step.signal,
            diagnosis = step.diagnosis,
            concepts = step.concepts.mapNotNull { key -> labels[key]?.let { RelatedConceptLink(key, it, "RELATED") } },
            action = step.action,
            metrics = numbers(domain, step).resolved.orEmpty(),
            claims = step.claims,
            before = SystemStateResponse.from(blocks.state(domain, step.incident, step.traits)),
            after = SystemStateResponse.from(blocks.state(domain, step.incident, after)),
            tradeoff = step.tradeoff,
            blocks = blocks.resolve(step.blocks),
            links = step.links,
            traitsBefore = step.traits,
            traitsAfter = after,
        )
    }
}
