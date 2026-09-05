package com.sysdrill.backend.organization

import com.sysdrill.backend.identity.UserRepository
import java.util.UUID
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/**
 * PLAN.md step 38 — appends and reads organization admin-action audit
 * entries. [record] is called from within the same @Transactional method as
 * the action itself (e.g. OrganizationService.inviteMember), never from a
 * separate event/queue path — the entry must always be atomically consistent
 * with the action it describes (docs/adr/0029).
 */
@Service
class OrganizationAuditLogService(
    private val repository: OrganizationAuditLogRepository,
    private val userRepository: UserRepository,
    private val accessGuard: OrganizationAccessGuard,
    private val objectMapper: ObjectMapper,
) {

    fun record(orgId: UUID, actorUserId: UUID, action: OrganizationAuditAction, detail: Map<String, Any?> = emptyMap()) {
        repository.save(
            OrganizationAuditLogEntry(
                organizationId = orgId,
                actorUserId = actorUserId,
                action = action,
                detail = detail.takeIf { it.isNotEmpty() }?.let { objectMapper.writeValueAsString(it) },
            )
        )
    }

    fun list(orgId: UUID, adminUserId: UUID): List<AuditLogEntryResponse> {
        accessGuard.requireAdmin(orgId, adminUserId)
        val entries = repository.findTop200ByOrganizationIdOrderByCreatedAtDesc(orgId)
        val actorsById = userRepository.findAllById(entries.map { it.actorUserId }.distinct()).associateBy { it.id }
        return entries.map { entry ->
            val actor = actorsById[entry.actorUserId]
            AuditLogEntryResponse(
                id = entry.id!!,
                actorNickname = actor?.nickname ?: "알 수 없음",
                actorEmail = actor?.email ?: "",
                action = entry.action,
                detail = entry.detail?.let { objectMapper.readValue(it, Any::class.java) },
                createdAt = entry.createdAt,
            )
        }
    }
}
