package com.sysdrill.backend.simulation

import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.session.SessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SystemTopologyService(
    private val sessionRepository: SessionRepository,
    private val topologyRepository: SystemTopologyRepository,
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

    companion object {
        const val EMPTY_GRAPH = """{"nodes":[],"edges":[]}"""
    }
}
