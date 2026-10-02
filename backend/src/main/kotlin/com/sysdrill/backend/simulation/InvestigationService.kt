package com.sysdrill.backend.simulation

import com.sysdrill.backend.session.SessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * docs/OBSERVABILITY_UI_PLAN.md O0-b (PLAN.md Round E17) — records what the learner looked at.
 * The same kind+target within [DEBOUNCE] collapses into one row (tab ping-pong isn't signal).
 * Nothing is recorded after completion — the sandbox rule of ADR-0046 applies to looks too.
 */
@Service
class InvestigationService(
    private val repository: InvestigationEventRepository,
    private val sessionRepository: SessionRepository,
) {
    @Transactional
    fun record(sessionId: UUID, kind: InvestigationKind, target: String?, now: Instant = Instant.now()) {
        val session = sessionRepository.findById(sessionId).orElse(null) ?: return
        if (session.completedAt != null) return
        val normalized = target?.trim()?.take(200)?.ifBlank { null }
        val last = repository.findFirstBySessionIdAndKindAndTargetOrderByCreatedAtDesc(sessionId, kind.name, normalized)
        if (last?.createdAt != null && Duration.between(last.createdAt, now) < DEBOUNCE) return
        repository.save(InvestigationEvent(sessionId = sessionId, kind = kind.name, target = normalized))
    }

    fun list(sessionId: UUID): List<InvestigationEvent> {
        val completedAt = sessionRepository.findById(sessionId).map { it.completedAt }.orElse(null)
        return repository.findBySessionIdOrderByCreatedAtAsc(sessionId)
            .filter { completedAt == null || !it.createdAt!!.isAfter(completedAt) }
    }

    private companion object {
        val DEBOUNCE: Duration = Duration.ofSeconds(30)
    }
}
