package com.sysdrill.backend.reporting

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReportRepository : JpaRepository<Report, UUID> {
    fun findFirstBySessionIdOrderByVersionDesc(sessionId: UUID): Report?
}
