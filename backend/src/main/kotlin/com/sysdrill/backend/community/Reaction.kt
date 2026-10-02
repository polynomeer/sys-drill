package com.sysdrill.backend.community

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.SessionRepository
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "community_reactions")
class CommunityReaction(
    @Id @GeneratedValue(strategy = GenerationType.UUID) var id: UUID? = null,
    @Column(name = "target_type", nullable = false) var targetType: String,
    @Column(name = "target_id", nullable = false) var targetId: UUID,
    @Column(name = "user_id", nullable = false) var userId: UUID,
    @Column(nullable = false) var kind: String,
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) var createdAt: Instant? = null,
)

interface CommunityReactionRepository : JpaRepository<CommunityReaction, UUID> {
    fun findByTargetTypeAndTargetIdIn(targetType: String, targetIds: Collection<UUID>): List<CommunityReaction>
    fun findByTargetTypeAndTargetIdAndUserIdAndKind(targetType: String, targetId: UUID, userId: UUID, kind: String): CommunityReaction?
}

enum class ReactionTarget { DISCUSSION, WRITEUP_COMMENT }

/** C14 — 도움됨 · 통찰 · 좋은 트레이드오프. */
enum class ReactionKind { HELPFUL, INSIGHT, GOOD_TRADEOFF }

data class ReactionSummary(val counts: Map<String, Int>, val mine: List<String>)

data class ToggleReaction(val targetType: ReactionTarget, val targetId: UUID, val kind: ReactionKind)

data class DomainReputation(val domain: String, val helpful: Int, val insight: Int, val goodTradeoff: Int, val total: Int)

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C14 (PLAN.md Round E32) — typed reactions on discussion posts and
 * writeup reviews, summed per domain on the author's profile at read time. No ranking, no score:
 * kept apart from Drill Score (ADR-0042), which reflects training results only.
 */
@Service
class ReactionService(
    private val repository: CommunityReactionRepository,
    private val discussionRepository: ScenarioDiscussionRepository,
    private val commentRepository: WriteupCommentRepository,
    private val sessionRepository: SessionRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioRepository: ScenarioRepository,
    private val writeupService: WriteupService,
) {
    @Transactional
    fun toggle(userId: UUID, request: ToggleReaction): ReactionSummary {
        val author = when (request.targetType) {
            ReactionTarget.DISCUSSION -> discussionRepository.findById(request.targetId).orElse(null)?.takeIf { it.hiddenAt == null }?.authorUserId
            ReactionTarget.WRITEUP_COMMENT -> commentRepository.findById(request.targetId).orElse(null)?.takeIf { it.hiddenAt == null }?.also {
                writeupService.detail(it.sessionId, userId) // the writeup gate (ADR-0041) — can't react to what you can't read
            }?.authorUserId
        } ?: throw NotFoundException("Not found: ${request.targetId}")
        if (author == userId) throw BadRequestException("자기 글에는 반응할 수 없습니다")
        val existing = repository.findByTargetTypeAndTargetIdAndUserIdAndKind(request.targetType.name, request.targetId, userId, request.kind.name)
        if (existing != null) repository.delete(existing)
        else repository.save(CommunityReaction(targetType = request.targetType.name, targetId = request.targetId, userId = userId, kind = request.kind.name))
        return summaries(request.targetType, listOf(request.targetId), userId).getValue(request.targetId)
    }

    fun summaries(target: ReactionTarget, ids: Collection<UUID>, viewerId: UUID): Map<UUID, ReactionSummary> {
        val byTarget = if (ids.isEmpty()) emptyMap() else repository.findByTargetTypeAndTargetIdIn(target.name, ids).groupBy { it.targetId }
        return ids.associateWith { id ->
            val rows = byTarget[id].orEmpty()
            ReactionSummary(rows.groupingBy { it.kind }.eachCount(), rows.filter { it.userId == viewerId }.map { it.kind })
        }
    }

    /** Reactions received, per domain of the post's scenario. Hidden posts don't count. */
    fun reputation(userId: UUID): List<DomainReputation> {
        val posts = discussionRepository.findByAuthorUserId(userId).filter { it.hiddenAt == null }
        val comments = commentRepository.findByAuthorUserId(userId).filter { it.hiddenAt == null }
        val domainOfVersion = HashMap<UUID, String?>()
        fun versionDomain(versionId: UUID) = domainOfVersion.getOrPut(versionId) {
            scenarioVersionRepository.findById(versionId).orElse(null)?.let { scenarioRepository.findById(it.scenarioId).orElse(null)?.domain }
        }
        val domainByTarget = posts.associate { it.id!! to versionDomain(it.scenarioVersionId) } +
            comments.associate { c -> c.id!! to sessionRepository.findById(c.sessionId).orElse(null)?.let { versionDomain(it.scenarioVersionId) } }
        val reactions = repository.findByTargetTypeAndTargetIdIn(ReactionTarget.DISCUSSION.name, posts.mapNotNull { it.id }) +
            repository.findByTargetTypeAndTargetIdIn(ReactionTarget.WRITEUP_COMMENT.name, comments.mapNotNull { it.id })
        return reactions.groupBy { domainByTarget[it.targetId] }.filterKeys { it != null }.map { (domain, rows) ->
            fun n(kind: ReactionKind) = rows.count { it.kind == kind.name }
            DomainReputation(domain!!, n(ReactionKind.HELPFUL), n(ReactionKind.INSIGHT), n(ReactionKind.GOOD_TRADEOFF), rows.size)
        }.sortedByDescending { it.total }
    }
}

@RestController
class ReactionController(private val service: ReactionService) {
    @PostMapping("/community/reactions")
    fun toggle(@AuthenticatedUserId userId: UUID, @RequestBody request: ToggleReaction): ReactionSummary = service.toggle(userId, request)

    /** C14 — anyone signed in can see a member's per-domain reputation; it's what they wrote, not how they trained. */
    @GetMapping("/community/users/{userId}/reputation")
    fun reputation(@PathVariable userId: UUID): List<DomainReputation> = service.reputation(userId)
}
