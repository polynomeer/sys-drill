package com.sysdrill.backend.learning

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.sysdrill.backend.simulation.DesignTraits
import com.sysdrill.backend.simulation.RuleBasedSimulationEngine
import com.sysdrill.backend.simulation.SimulationActionType
import com.sysdrill.backend.simulation.SimulationSessionState
import com.sysdrill.backend.simulation.SystemState
import com.sysdrill.backend.simulation.SystemStateResponse
import com.sysdrill.backend.simulation.TelemetrySampler
import com.sysdrill.backend.simulation.TimedAction
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

/**
 * docs/LEARNING_DEEPENING_PLAN.md L12 — the rich content a concept, failure pattern or design
 * guide carries beyond its fixed fields: an ordered list of typed blocks, stored as JSONB and
 * seeded by migration (ADR-0002, ADR-0039).
 *
 * Two block types hold no numbers of their own. [NumbersBlock] and [SystemBlock] store only
 * which domain, whether the incident is on, and which [DesignTraits] to override — the values
 * are computed by the rule engine when the page is read, so the prose can never drift from what
 * the Drill and the labs show (the same stance as ADR-0047). A number block's [NumbersBlock.claims]
 * are what its prose asserts ("DB read load goes down"); ContentBlocksTest holds every seeded
 * claim against the engine, so an engine change that breaks a sentence fails CI instead of
 * leaving the sentence quietly wrong.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(TextBlock::class, name = "text"),
    JsonSubTypes.Type(DiagramBlock::class, name = "diagram"),
    JsonSubTypes.Type(StepsBlock::class, name = "steps"),
    JsonSubTypes.Type(CalloutBlock::class, name = "callout"),
    JsonSubTypes.Type(CompareBlock::class, name = "compare"),
    JsonSubTypes.Type(NumbersBlock::class, name = "numbers"),
    JsonSubTypes.Type(SystemBlock::class, name = "system"),
    JsonSubTypes.Type(TimelineBlock::class, name = "timeline"),
)
sealed interface ContentBlock

/** Paragraphs separated by blank lines; `**bold**`, `` `code` `` and lines starting with "- " as a list. */
data class TextBlock(val body: String) : ContentBlock

/** A Mermaid diagram. [alt] is read out instead of the picture, so it must say what the picture shows. */
data class DiagramBlock(val mermaid: String, val caption: String = "", val alt: String = "") : ContentBlock

data class StepsBlock(val title: String = "", val items: List<Step> = emptyList()) : ContentBlock {
    data class Step(val title: String, val body: String = "")
}

/** [tone]: `tip`, `warning` or `tradeoff`. */
data class CalloutBlock(val tone: String = "tip", val title: String = "", val body: String) : ContentBlock

/** The problem and the fix side by side — two diagrams, each with an optional line of prose. */
data class CompareBlock(val before: Pane, val after: Pane, val caption: String = "") : ContentBlock {
    data class Pane(val label: String, val mermaid: String, val body: String = "", val alt: String = "")
}

/**
 * "Change this, and these metrics move like this" — [base] traits on [domain] (incident on or off)
 * versus [base] + [change]. [resolved] is filled by [ContentBlockService]; never seeded.
 */
data class NumbersBlock(
    val title: String = "",
    val domain: String,
    val incident: Boolean = true,
    val base: Map<String, Any> = emptyMap(),
    val change: Map<String, Any>,
    val changeLabel: String,
    val metrics: List<String>,
    val claims: List<Claim> = emptyList(),
    val resolved: Map<String, MetricPair>? = null,
) : ContentBlock {
    /** [direction]: `UP`, `DOWN` or `SAME` — the same 2% band the labs use. */
    data class Claim(val metric: String, val direction: String)
    data class MetricPair(val before: Double, val after: Double)
}

/** The domain's system drawn and coloured by the engine's state for these traits. [state] is filled when read. */
data class SystemBlock(
    val domain: String,
    val incident: Boolean = true,
    val traits: Map<String, Any> = emptyMap(),
    val caption: String = "",
    val state: SystemStateResponse? = null,
) : ContentBlock

/**
 * docs/LEARNING_DEEPENING_PLAN.md L13 — how an incident unfolds over time and how it recovers.
 * The engine's own clock ([TelemetrySampler]: 90 s ramp, actions taking effect from their second)
 * samples [domain] with [traits] from 30 s before the incident to [durationSeconds] after it,
 * once with nothing done and once per [scenarios] entry (a timed list of Drill actions — a Bad Fix
 * or the right mitigation). [alerts] are thresholds; the resolved block says when each first
 * fired with nothing done, which is "which signal moved first". A scenario's [Scenario.claims]
 * compare its last point with the do-nothing series' last point and are tested like a numbers
 * block's. [resolved] is filled when read; never seeded.
 */
data class TimelineBlock(
    val title: String = "",
    val domain: String,
    val traits: Map<String, Any> = emptyMap(),
    val durationSeconds: Long = 300,
    val stepSeconds: Long = 5,
    val metrics: List<String>,
    val alerts: List<Alert> = emptyList(),
    val scenarios: List<Scenario> = emptyList(),
    val caption: String = "",
    val resolved: Resolved? = null,
) : ContentBlock {
    /** Fires when [metric] goes [op] (`ABOVE` / `BELOW`) [threshold], in the engine's units (ratios are 0–1). */
    data class Alert(val metric: String, val op: String, val threshold: Double, val label: String)

    /** [tone]: `bad` (a plausible wrong fix) or `good` (the mitigation). */
    data class Scenario(val label: String, val tone: String = "good", val actions: List<At> = emptyList(), val claims: List<NumbersBlock.Claim> = emptyList())
    data class At(val second: Long, val action: SimulationActionType)

    data class Resolved(val seconds: List<Long>, val series: List<Series>, val firstAlerts: List<AlertHit>)

    /** One run — the first is always "nothing done". [values] per metric, aligned with [Resolved.seconds]; [status] per point. */
    data class Series(val label: String, val tone: String, val values: Map<String, List<Double>>, val status: List<String>, val actions: List<At>)

    /** [second] is null when the alert never fired within the window. */
    data class AlertHit(val label: String, val metric: String, val second: Long?)
}

enum class Direction {
    UP, SAME, DOWN;

    companion object {
        /** Within 2% of the previous value counts as "about the same" — mirrors the labs' predict step. */
        fun of(before: Double, after: Double): Direction {
            val change = (after - before) / maxOf(abs(before), 1e-9)
            return when {
                abs(change) < 0.02 -> SAME
                change > 0 -> UP
                else -> DOWN
            }
        }
    }
}

@Service
class ContentBlockService(private val mapper: ObjectMapper) {

    /** Parses stored blocks and fills in every engine-computed value. Throws on a malformed block. */
    fun resolve(raw: List<Map<String, Any?>>): List<ContentBlock> = parse(raw).map { block ->
        when (block) {
            is NumbersBlock -> resolveNumbers(block)
            is SystemBlock -> block.copy(state = SystemStateResponse.from(state(block.domain, block.incident, block.traits)))
            is TimelineBlock -> resolveTimeline(block)
            else -> block
        }
    }

    /** [block] with its before/after metrics computed by the engine. */
    fun resolveNumbers(block: NumbersBlock): NumbersBlock {
        val before = metricsOf(state(block.domain, block.incident, block.base))
        val after = metricsOf(state(block.domain, block.incident, block.base + block.change))
        return block.copy(resolved = block.metrics.associateWith { NumbersBlock.MetricPair(before.metric(it), after.metric(it)) })
    }

    /** [block] sampled on the engine's clock: nothing done, then each scenario. */
    fun resolveTimeline(block: TimelineBlock): TimelineBlock {
        val traits = traits(block.domain, block.traits)
        val start = TIMELINE_EPOCH
        fun run(label: String, tone: String, actions: List<TimelineBlock.At>): Pair<TimelineBlock.Series, List<Long>> {
            val timed = actions.map { TimedAction(start.plusSeconds(it.second), it.action) }
            val points = TelemetrySampler.sample(
                block.domain, traits, start, timed,
                start.minusSeconds(TIMELINE_LEAD_SECONDS), start.plusSeconds(block.durationSeconds), Duration.ofSeconds(block.stepSeconds),
            )
            val rows = points.map { metricsOf(it.state) }
            val series = TimelineBlock.Series(
                label = label,
                tone = tone,
                values = block.metrics.associateWith { m -> rows.map { it.metric(m) } },
                status = TelemetrySampler.classify(points, start).map { it.name },
                actions = actions,
            )
            return series to points.map { Duration.between(start, it.at).seconds }
        }
        val (incident, seconds) = run("아무것도 하지 않으면", "none", emptyList())
        val scenarios = block.scenarios.map { run(it.label, it.tone, it.actions).first }
        val firstAlerts = block.alerts.map { alert ->
            require(alert.metric in block.metrics) { "Alert on ${alert.metric}, which the timeline doesn't list in metrics" }
            val values = incident.values.getValue(alert.metric)
            val hit = seconds.indices.firstOrNull { i ->
                seconds[i] >= 0 && if (alert.op == "BELOW") values[i] < alert.threshold else values[i] > alert.threshold
            }
            TimelineBlock.AlertHit(alert.label, alert.metric, hit?.let { seconds[it] })
        }.sortedBy { it.second ?: Long.MAX_VALUE }
        return block.copy(resolved = TimelineBlock.Resolved(seconds, listOf(incident) + scenarios, firstAlerts))
    }

    fun parse(raw: List<Map<String, Any?>>): List<ContentBlock> = raw.map { mapper.convertValue(it, ContentBlock::class.java) }

    /** Claims in a resolved block that the engine contradicts, as human-readable lines. */
    fun brokenClaims(block: NumbersBlock): List<String> {
        val resolved = block.resolved ?: error("resolve the block first")
        return block.claims.mapNotNull { claim ->
            val pair = resolved[claim.metric] ?: return@mapNotNull "claim on ${claim.metric}, which the block doesn't list in metrics"
            val actual = Direction.of(pair.before, pair.after)
            if (actual.name == claim.direction) null else "${claim.metric}: claims ${claim.direction}, engine says $actual (${pair.before} -> ${pair.after})"
        }
    }

    /** Claims in a resolved timeline that the engine contradicts: each scenario's last point against the do-nothing run's. */
    fun brokenClaims(block: TimelineBlock): List<String> {
        val resolved = block.resolved ?: error("resolve the block first")
        val incident = resolved.series.first()
        return block.scenarios.zip(resolved.series.drop(1)).flatMap { (scenario, series) ->
            scenario.claims.mapNotNull { claim ->
                val before = incident.values[claim.metric]?.last() ?: return@mapNotNull "${scenario.label}: claim on ${claim.metric}, which the timeline doesn't list in metrics"
                val after = series.values.getValue(claim.metric).last()
                val actual = Direction.of(before, after)
                if (actual.name == claim.direction) null else "${scenario.label} ${claim.metric}: claims ${claim.direction}, engine says $actual ($before -> $after)"
            }
        }
    }

    /** The rule engine for [domain] with [overrides] on top of the default traits — no session, nothing stored. */
    fun state(domain: String, incident: Boolean, overrides: Map<String, Any>): SystemState =
        RuleBasedSimulationEngine.computeState(SimulationSessionState(CONTENT_SESSION_ID, domain, incident, traits(domain, overrides)))

    private fun traits(domain: String, overrides: Map<String, Any>): DesignTraits {
        require(domain in RuleBasedSimulationEngine.KNOWN_DOMAINS) { "Unknown domain '$domain' in a content block" }
        val defaults = traitDefaults()
        val unknown = overrides.keys - defaults.keys
        require(unknown.isEmpty()) { "Unknown traits $unknown in a content block" }
        return mapper.convertValue(defaults + overrides, DesignTraits::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    private fun metricsOf(state: SystemState): Map<String, Any?> =
        mapper.convertValue(SystemStateResponse.from(state), Map::class.java) as Map<String, Any?>

    private fun Map<String, Any?>.metric(name: String): Double =
        (this[name] as? Number)?.toDouble() ?: throw IllegalArgumentException("Unknown metric '$name' in a content block")

    @Suppress("UNCHECKED_CAST")
    private fun traitDefaults(): Map<String, Any?> = mapper.convertValue(DesignTraits(), Map::class.java) as Map<String, Any?>

    private companion object {
        val CONTENT_SESSION_ID: UUID = UUID(0, 1)
        val TIMELINE_EPOCH: Instant = Instant.parse("2026-01-01T00:00:00Z")
        const val TIMELINE_LEAD_SECONDS = 30L
    }
}
