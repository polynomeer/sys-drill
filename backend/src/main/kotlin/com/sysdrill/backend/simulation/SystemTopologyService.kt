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
        val saved = topologyRepository.findBySessionId(sessionId) ?: return null
        val graph = objectMapper.readValue(saved.graph, TopologyGraph::class.java)
        var traits = DesignTraits()
        val kindFields = TOPOLOGY_FIELDS[domain] ?: return traits
        for ((kind, fields) in kindFields) {
            for (field in fields) {
                val values = graph.nodes.asSequence()
                    .filter { it.data.kind == kind }
                    .mapNotNull { it.data.traitValues[field.key] }
                    .toList()
                if (values.isEmpty()) continue // no node of this kind on the canvas — leave the default
                val aggregated = if (field.aggregation == Aggregation.SUM) values.sum() else values.last()
                traits = applyField(traits, field.key, aggregated)
            }
        }
        return traits
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
        else -> traits
    }

    companion object {
        const val EMPTY_GRAPH = """{"nodes":[],"edges":[]}"""
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
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TopologyGraph(val nodes: List<TopologyNode> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TopologyNode(val data: TopologyNodeData = TopologyNodeData())

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TopologyNodeData(val kind: String? = null, val traitValues: Map<String, Double> = emptyMap())
