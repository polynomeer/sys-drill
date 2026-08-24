package com.sysdrill.backend.session

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SessionRepository : JpaRepository<Session, UUID>

interface SessionPhaseRepository : JpaRepository<SessionPhase, UUID>
