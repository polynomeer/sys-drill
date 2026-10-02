package com.sysdrill.backend.community

import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.simulation.SimulationService
import com.sysdrill.backend.simulation.SystemTopologyService
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** docs/COMMUNITY_EXPANSION_PLAN.md C10 (PLAN.md Round E22) — a review pinned to a node, a timeline step, or the whole writeup. */
@Entity
@Table(name = "writeup_comments")
class WriteupComment(
    @Id @GeneratedValue(strategy = GenerationType.UUID) var id: UUID? = null,
    @Column(name = "session_id", nullable = false) var sessionId: UUID,
    @Column(name = "author_user_id", nullable = false) var authorUserId: UUID,
    @Column(name = "anchor_type", nullable = false) var anchorType: String,
    @Column(name = "anchor_ref") var anchorRef: String? = null,
    /** What the anchor was called when the comment was written — survives later canvas edits. */
    @Column(name = "anchor_label") var anchorLabel: String? = null,
    @Column(nullable = false) var kind: String,
    @Column(nullable = false) var body: String,
    @Column(name = "hidden_at") var hiddenAt: Instant? = null,
    @Column(name = "hidden_by_user_id") var hiddenByUserId: UUID? = null,
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) var createdAt: Instant? = null,
)

@Entity
@Table(name = "writeup_comment_reports")
class WriteupCommentReport(
    @Id @GeneratedValue(strategy = GenerationType.UUID) var id: UUID? = null,
    @Column(name = "comment_id", nullable = false) var commentId: UUID,
    @Column(name = "reporter_user_id", nullable = false) var reporterUserId: UUID,
    var reason: String? = null,
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) var createdAt: Instant? = null,
)

interface WriteupCommentRepository : JpaRepository<WriteupComment, UUID> {
    fun findBySessionIdOrderByCreatedAtAsc(sessionId: UUID): List<WriteupComment>
    fun findBySessionIdInAndHiddenAtIsNull(sessionIds: Collection<UUID>): List<WriteupComment>
    fun findByAuthorUserId(authorUserId: UUID): List<WriteupComment>
}

interface WriteupCommentReportCount {
    fun getCommentId(): UUID
    fun getReportCount(): Long
}

interface WriteupCommentReportRepository : JpaRepository<WriteupCommentReport, UUID> {
    fun existsByCommentIdAndReporterUserId(commentId: UUID, reporterUserId: UUID): Boolean
    fun countByCommentId(commentId: UUID): Long
    fun findByReporterUserIdAndCommentIdIn(reporterUserId: UUID, commentIds: Collection<UUID>): List<WriteupCommentReport>

    @org.springframework.data.jpa.repository.Query(
        "select r.commentId as commentId, count(r) as reportCount from WriteupCommentReport r group by r.commentId"
    )
    fun countsByComment(): List<WriteupCommentReportCount>
}

enum class AnchorType { NODE, TIMELINE, NONE }

/** C10 — review kinds instead of free "좋네요": 질문 · 리스크 · 제안 · 대안 · 트레이드오프. */
enum class ReviewKind { QUESTION, RISK, SUGGESTION, ALTERNATIVE, TRADEOFF }

data class PostWriteupCommentRequest(
    val anchorType: AnchorType = AnchorType.NONE,
    @field:Size(max = 100) val anchorRef: String? = null,
    val kind: ReviewKind,
    @field:NotBlank @field:Size(max = 2000) val body: String,
)

data class CommentAnchor(val type: AnchorType, val ref: String, val label: String)

data class WriteupCommentView(
    val id: UUID,
    val anchorType: AnchorType,
    val anchorRef: String?,
    val anchorLabel: String?,
    val kind: ReviewKind,
    val body: String,
    val authorNickname: String,
    val mine: Boolean,
    val reportedByMe: Boolean,
    val createdAt: Instant?,
    /** PLAN.md Round E32 (C14). */
    val reactions: ReactionSummary? = null,
)

data class WriteupCommentsResponse(val comments: List<WriteupCommentView>, val anchors: List<CommentAnchor>)

data class ReportedWriteupComment(
    val id: UUID,
    val sessionId: UUID,
    val scenarioId: UUID?,
    val authorNickname: String,
    val anchorLabel: String?,
    val kind: String,
    val body: String,
    val reportCount: Long,
    val hidden: Boolean,
    val createdAt: Instant?,
)

@Service
class WriteupCommentService(
    private val repository: WriteupCommentRepository,
    private val reportRepository: WriteupCommentReportRepository,
    private val writeupService: WriteupService,
    private val systemTopologyService: SystemTopologyService,
    private val simulationService: SimulationService,
    private val userRepository: UserRepository,
    private val reactionService: ReactionService,
) {
    /** Same gate as reading the writeup (ADR-0041, private → 404, not completed → 403). */
    fun list(sessionId: UUID, viewerId: UUID): WriteupCommentsResponse {
        writeupService.detail(sessionId, viewerId)
        val comments = repository.findBySessionIdOrderByCreatedAtAsc(sessionId).filter { it.hiddenAt == null }
        val nicknames = userRepository.findAllById(comments.map { it.authorUserId }.distinct()).associate { it.id to it.nickname }
        val reported = reportRepository.findByReporterUserIdAndCommentIdIn(viewerId, comments.mapNotNull { it.id }).map { it.commentId }.toSet()
        val reactions = reactionService.summaries(ReactionTarget.WRITEUP_COMMENT, comments.mapNotNull { it.id }, viewerId)
        return WriteupCommentsResponse(
            comments = comments.map {
                WriteupCommentView(
                    id = it.id!!,
                    anchorType = AnchorType.valueOf(it.anchorType),
                    anchorRef = it.anchorRef,
                    anchorLabel = it.anchorLabel,
                    kind = ReviewKind.valueOf(it.kind),
                    body = it.body,
                    authorNickname = nicknames[it.authorUserId] ?: "(알 수 없음)",
                    mine = it.authorUserId == viewerId,
                    reportedByMe = it.id in reported,
                    createdAt = it.createdAt,
                    reactions = reactions[it.id],
                )
            },
            anchors = anchors(sessionId),
        )
    }

    @Transactional
    fun post(sessionId: UUID, authorId: UUID, request: PostWriteupCommentRequest): WriteupCommentsResponse {
        writeupService.detail(sessionId, authorId)
        val (ref, label) = when (request.anchorType) {
            AnchorType.NONE -> null to null
            else -> {
                val anchor = anchors(sessionId).firstOrNull { it.type == request.anchorType && it.ref == request.anchorRef }
                    ?: throw BadRequestException("이 풀이에 없는 위치입니다")
                anchor.ref to anchor.label
            }
        }
        repository.save(
            WriteupComment(
                sessionId = sessionId,
                authorUserId = authorId,
                anchorType = request.anchorType.name,
                anchorRef = ref,
                anchorLabel = label,
                kind = request.kind.name,
                body = request.body.trim(),
            )
        )
        return list(sessionId, authorId)
    }

    @Transactional
    fun report(commentId: UUID, reporterId: UUID, reason: String?) {
        val comment = repository.findById(commentId).orElseThrow { NotFoundException("Comment not found: $commentId") }
        writeupService.detail(comment.sessionId, reporterId)
        if (comment.authorUserId == reporterId) throw BadRequestException("자기 댓글은 신고할 수 없습니다")
        if (reportRepository.existsByCommentIdAndReporterUserId(commentId, reporterId)) return
        reportRepository.save(WriteupCommentReport(commentId = commentId, reporterUserId = reporterId, reason = reason?.trim()?.ifBlank { null }))
    }

    /** PLATFORM_ADMIN review list, most reported first — the discussion moderation shape. */
    fun reported(): List<ReportedWriteupComment> {
        val counts = reportRepository.countsByComment().associate { it.getCommentId() to it.getReportCount() }
        if (counts.isEmpty()) return emptyList()
        return repository.findAllById(counts.keys).map { toReported(it, counts[it.id] ?: 0) }
            .sortedWith(compareByDescending<ReportedWriteupComment> { it.reportCount }.thenBy { it.createdAt })
    }

    @Transactional
    fun setHidden(commentId: UUID, adminId: UUID, hidden: Boolean): ReportedWriteupComment {
        val comment = repository.findById(commentId).orElseThrow { NotFoundException("Comment not found: $commentId") }
        comment.hiddenAt = if (hidden) Instant.now() else null
        comment.hiddenByUserId = if (hidden) adminId else null
        repository.save(comment)
        return toReported(comment, reportRepository.countByCommentId(commentId))
    }

    /** C11's "주목할 풀이" — visible review counts per writeup. */
    fun countsBySession(sessionIds: Collection<UUID>): Map<UUID, Int> =
        if (sessionIds.isEmpty()) emptyMap()
        else repository.findBySessionIdInAndHiddenAtIsNull(sessionIds).groupingBy { it.sessionId }.eachCount()

    private fun toReported(c: WriteupComment, count: Long) = ReportedWriteupComment(
        id = c.id!!,
        sessionId = c.sessionId,
        scenarioId = runCatching { writeupService.scenarioIdOf(c.sessionId) }.getOrNull(),
        authorNickname = userRepository.findById(c.authorUserId).map { it.nickname }.orElse("(알 수 없음)"),
        anchorLabel = c.anchorLabel,
        kind = c.kind,
        body = c.body,
        reportCount = count,
        hidden = c.hiddenAt != null,
        createdAt = c.createdAt,
    )

    private fun anchors(sessionId: UUID): List<CommentAnchor> {
        val nodes = systemTopologyService.nodes(sessionId).map { CommentAnchor(AnchorType.NODE, it.id, it.label) }
        val timeline = simulationService.getTimeline(sessionId)
        val start = timeline.firstOrNull()?.appliedAt
        val steps = timeline.map { step ->
            val elapsed = start?.let { Duration.between(it, step.appliedAt).seconds } ?: 0
            // The action code, not the effect sentence — the UI turns codes into its action labels.
            val what = step.actionType?.takeIf { step.step > 0 } ?: "인시던트 시작"
            CommentAnchor(AnchorType.TIMELINE, "step:${step.step}", "+${elapsed / 60}분 ${elapsed % 60}초 $what")
        }
        return nodes + steps
    }
}
