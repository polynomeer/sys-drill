package com.sysdrill.backend.simulation

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.session.SessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Service
class SystemTopologyService(
    private val sessionRepository: SessionRepository,
    private val topologyRepository: SystemTopologyRepository,
    private val objectMapper: ObjectMapper,
) {

    fun get(sessionId: UUID): SystemTopologyResponse {
        if (!sessionRepository.existsById(sessionId)) {
            throw NotFoundException("Session not found: $sessionId")
        }
        val saved = topologyRepository.findBySessionId(sessionId)
        return SystemTopologyResponse(
            sessionId = sessionId,
            saved = saved != null,
            graph = saved?.graph ?: EMPTY_GRAPH,
            updatedAt = saved?.updatedAt,
        )
    }

    /** No session-status gate (unlike [com.sysdrill.backend.postmortem.PostmortemService.save]'s COMPLETED requirement) — this is a live draft that saves continuously while the user is still designing, not a post-hoc narrative. */
    @Transactional
    fun save(sessionId: UUID, request: SaveSystemTopologyRequest): SystemTopologyResponse {
        if (!sessionRepository.existsById(sessionId)) {
            throw NotFoundException("Session not found: $sessionId")
        }
        val entity = topologyRepository.findBySessionId(sessionId)
            ?: SystemTopology(sessionId = sessionId, graph = request.graph)
        entity.graph = request.graph
        topologyRepository.save(entity)
        return get(sessionId)
    }

    /**
     * ADR-0037 next slice — the engine reading per-node topology directly
     * instead of a flat client-sent [DesignTraits]. `null` means "no topology
     * saved for this session" (caller should fall back to whatever traits it
     * already has, e.g. Slice 1's client-sent value); a non-null result IS
     * authoritative even if every field lands on its default — a
     * deliberately empty canvas is still a valid, complete input.
     */
    fun deriveDesignTraits(sessionId: UUID, domain: String): DesignTraits? {
        val connectedNodes = resolveConnectedNodes(sessionId) ?: return null
        var traits = DesignTraits()
        val kindFields = TOPOLOGY_FIELDS[domain] ?: return traits
        for ((kind, fields) in kindFields) {
            for (field in fields) {
                val values = connectedNodes.asSequence()
                    .filter { it.data.kind == kind }
                    .mapNotNull { it.data.traitValues[field.key] }
                    .toList()
                if (values.isEmpty()) continue // no connected node of this kind on the canvas — leave the default
                val aggregated = if (field.aggregation == Aggregation.SUM) values.sum() else values.last()
                traits = applyField(traits, field.key, aggregated)
            }
        }
        return traits
    }

    /** PLAN.md Round E22 (C10) — the saved canvas's nodes, as anchors a review comment can point at. */
    fun nodes(sessionId: UUID): List<CanvasNodeRef> {
        val saved = topologyRepository.findBySessionId(sessionId) ?: return emptyList()
        val graph = objectMapper.readValue(saved.graph, TopologyGraph::class.java)
        return graph.nodes.mapNotNull { n -> n.id?.let { CanvasNodeRef(it, n.data.label?.ifBlank { null } ?: n.data.kind ?: it, n.data.kind) } }
    }

    /**
     * docs/COMMUNITY_EXPANSION_PLAN.md C8 (PLAN.md Round E15) — the comparable shape of a saved
     * design: which node kinds it uses (connected or not — a drawn queue is a design choice even
     * if it was left dangling) and the domain's topology-driven trait values. Null with no canvas.
     */
    fun designProfile(sessionId: UUID, domain: String): DesignProfile? {
        val saved = topologyRepository.findBySessionId(sessionId) ?: return null
        val graph = objectMapper.readValue(saved.graph, TopologyGraph::class.java)
        val kinds = graph.nodes.mapNotNull { it.data.kind }.groupingBy { it }.eachCount()
        val traits = deriveDesignTraits(sessionId, domain) ?: DesignTraits()
        val defaults = DesignTraits()
        val fieldKeys = TOPOLOGY_FIELDS[domain].orEmpty().values.flatten().map { it.key }
        return DesignProfile(
            nodeKinds = kinds,
            traits = fieldKeys.associateWith { traitValue(traits, it) },
            defaults = fieldKeys.associateWith { traitValue(defaults, it) },
        )
    }

    private fun traitValue(traits: DesignTraits, key: String): Int = when (key) {
        "cacheTtlSeconds" -> traits.cacheTtlSeconds
        "dbPoolSize" -> traits.dbPoolSize
        "consumerCount" -> traits.consumerCount
        "readReplicaCount" -> traits.readReplicaCount
        "dispatcherWorkers" -> traits.dispatcherWorkers
        "holdTimeoutSeconds" -> traits.holdTimeoutSeconds
        "chunkSize" -> traits.chunkSize
        "podReplicas" -> traits.podReplicas
        "canaryStartPercent" -> traits.canaryStartPercent
        "autoRollbackErrorPct" -> traits.autoRollbackErrorPct
        else -> 0
    }

    /**
     * Whether [domain]'s saved topology has at least one connected node
     * explicitly setting [fieldKey] — [deriveDesignTraits]'s return value
     * alone can't distinguish "user explicitly set this to the rule-based
     * default value" from "never touched, stayed at the default," which
     * matters only where that distinction has a real consequence: real-infra
     * mode's deliberately-undersized starting traits (see the real-infra
     * branches in [SimulationService.startIncident]) must not silently
     * override a value the user genuinely chose, even if it happens to
     * collide with [DesignTraits]'s own default for that field.
     */
    fun wasFieldExplicitlySet(sessionId: UUID, domain: String, fieldKey: String): Boolean {
        val connectedNodes = resolveConnectedNodes(sessionId) ?: return false
        val kindFields = TOPOLOGY_FIELDS[domain] ?: return false
        return kindFields.any { (kind, fields) ->
            fields.any { it.key == fieldKey } &&
                connectedNodes.any { it.data.kind == kind && it.data.traitValues.containsKey(fieldKey) }
        }
    }

    // Edge recognition (dependency-graph slice) — a node only participates in the
    // aggregation above if it has at least one edge attaching it to something else
    // on the canvas. This drops decorative/orphaned nodes (drawn but never wired
    // up) without needing a notion of "entry point" or edge direction, which don't
    // cleanly generalize across this app's 7 domain-specific node-kind conventions
    // (unlike a single canonical client -> ... -> db chain, this canvas has no
    // fixed shape).
    private fun resolveConnectedNodes(sessionId: UUID): List<TopologyNode>? {
        val saved = topologyRepository.findBySessionId(sessionId) ?: return null
        val graph = objectMapper.readValue(saved.graph, TopologyGraph::class.java)
        val connectedIds = graph.edges.asSequence().flatMap { sequenceOf(it.source, it.target) }.filterNotNull().toSet()
        return graph.nodes.filter { it.id != null && it.id in connectedIds }
    }

    private fun applyField(traits: DesignTraits, key: String, value: Double): DesignTraits = when (key) {
        "cacheTtlSeconds" -> traits.copy(cacheTtlSeconds = value.toInt())
        "dbPoolSize" -> traits.copy(dbPoolSize = value.toInt())
        "consumerCount" -> traits.copy(consumerCount = value.toInt())
        "readReplicaCount" -> traits.copy(readReplicaCount = value.toInt())
        "dispatcherWorkers" -> traits.copy(dispatcherWorkers = value.toInt())
        "holdTimeoutSeconds" -> traits.copy(holdTimeoutSeconds = value.toInt())
        "chunkSize" -> traits.copy(chunkSize = value.toInt())
        "podReplicas" -> traits.copy(podReplicas = value.toInt())
        "canaryStartPercent" -> traits.copy(canaryStartPercent = value.toInt().coerceIn(1, 100))
        "autoRollbackErrorPct" -> traits.copy(autoRollbackErrorPct = value.toInt().coerceIn(0, 100))
        else -> traits
    }

    companion object {
        const val EMPTY_GRAPH = """{"nodes":[],"edges":[]}"""
    }
}

data class CanvasNodeRef(val id: String, val label: String, val kind: String?)

/** C8 — a design's comparable shape: node kinds drawn, and the topology-driven traits next to their defaults. */
data class DesignProfile(
    val nodeKinds: Map<String, Int>,
    val traits: Map<String, Int>,
    val defaults: Map<String, Int>,
) {
    /**
     * 0 (same) … 1 (nothing in common): half from the node-kind sets (Jaccard distance), half from
     * the trait values (mean of |a−b| / max(a, b)). Deliberately simple — it only has to rank
     * "different from mine" above "like mine", not measure design quality.
     */
    fun distanceTo(other: DesignProfile): Double {
        val a = nodeKinds.keys
        val b = other.nodeKinds.keys
        val union = (a + b).size
        val kindDistance = if (union == 0) 0.0 else 1.0 - (a intersect b).size.toDouble() / union
        val keys = traits.keys intersect other.traits.keys
        val traitDistance = if (keys.isEmpty()) 0.0 else keys.map { k ->
            val x = traits.getValue(k).toDouble()
            val y = other.traits.getValue(k).toDouble()
            if (maxOf(x, y) == 0.0) 0.0 else kotlin.math.abs(x - y) / maxOf(x, y)
        }.average()
        return 0.5 * kindDistance + 0.5 * traitDistance
    }
}

private enum class Aggregation { SUM, LAST }

private data class TopologyField(val key: String, val aggregation: Aggregation)

/**
 * Mirrors frontend/src/app/design/[sessionId]/DiagramCanvas.tsx's
 * `NODE_TRAIT_CONFIG` (domain -> node kind -> [DesignTraits] field) — the two
 * must stay in sync by field name, the same caveat Slice 1 already carries
 * for [DesignTraits] itself. SUM fields are parallel-capacity units where
 * drawing more same-kind nodes should add up (pool size, replica/consumer/
 * worker/pod counts); LAST fields are single-component settings (TTL,
 * timeout, chunk size) that don't make sense to add across nodes — same
 * "last node wins" semantics `DiagramCanvas.tsx`'s `collectTraits()` already
 * used client-side, now computed here instead.
 */
private val TOPOLOGY_FIELDS: Map<String, Map<String, List<TopologyField>>> = mapOf(
    RuleBasedSimulationEngine.DOMAIN_COUPON to mapOf(
        "cache" to listOf(TopologyField("cacheTtlSeconds", Aggregation.LAST)),
        "db" to listOf(TopologyField("dbPoolSize", Aggregation.SUM)),
    ),
    RuleBasedSimulationEngine.DOMAIN_NOTIFICATION to mapOf(
        "queue" to listOf(TopologyField("consumerCount", Aggregation.SUM)),
    ),
    RuleBasedSimulationEngine.DOMAIN_PRODUCT_BROWSING to mapOf(
        "db" to listOf(TopologyField("readReplicaCount", Aggregation.SUM)),
    ),
    RuleBasedSimulationEngine.DOMAIN_PAYMENT to mapOf(
        "service" to listOf(TopologyField("dispatcherWorkers", Aggregation.SUM)),
    ),
    RuleBasedSimulationEngine.DOMAIN_RESERVATION to mapOf(
        "service" to listOf(TopologyField("holdTimeoutSeconds", Aggregation.LAST)),
    ),
    RuleBasedSimulationEngine.DOMAIN_BATCH_SETTLEMENT to mapOf(
        "service" to listOf(TopologyField("chunkSize", Aggregation.LAST)),
    ),
    RuleBasedSimulationEngine.DOMAIN_AUTOSCALING to mapOf(
        "service" to listOf(TopologyField("podReplicas", Aggregation.SUM)),
    ),
    // PLAN.md Round E30 — the rollout plan: the canary's first share on the service, the auto-rollback bar at the gateway.
    RuleBasedSimulationEngine.DOMAIN_DEPLOYMENT to mapOf(
        "service" to listOf(TopologyField("canaryStartPercent", Aggregation.LAST)),
        "gateway" to listOf(TopologyField("autoRollbackErrorPct", Aggregation.LAST)),
    ),
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TopologyGraph(val nodes: List<TopologyNode> = emptyList(), val edges: List<TopologyEdge> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TopologyNode(val id: String? = null, val data: TopologyNodeData = TopologyNodeData())

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TopologyNodeData(val kind: String? = null, val label: String? = null, val traitValues: Map<String, Double> = emptyMap())

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TopologyEdge(val source: String? = null, val target: String? = null)
