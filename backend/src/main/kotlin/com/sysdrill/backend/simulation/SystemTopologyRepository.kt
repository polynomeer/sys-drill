package com.sysdrill.backend.simulation

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SystemTopologyRepository : JpaRepository<SystemTopology, UUID> {
    fun findBySessionId(sessionId: UUID): SystemTopology?
}
