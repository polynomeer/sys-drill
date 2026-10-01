package com.sysdrill.backend.notification

import com.sysdrill.backend.build.BuildChallengeRepository
import com.sysdrill.backend.build.BuildStageRepository
import com.sysdrill.backend.build.BuildSubmissionRepository
import com.sysdrill.backend.build.BuildSubmissionStatus
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.community.ScenarioDiscussionRepository
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.evaluation.EvaluationRepository
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.organization.OrganizationInvitationStatus
import com.sysdrill.backend.organization.OrganizationRepository
import com.sysdrill.backend.organization.OrganizationInvitationRepository
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.submission.SubmissionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.9 (PLAN.md Round B16) — the notification bell.
 *
 * Nothing is written when an event happens: the feed is **derived on read**
 * (ADR-0011) from rows that already exist — my graded design submissions, my
 * finished Build runs, invitations waiting for my email, and new messages by
 * others in discussion threads I've posted in. The only stored state is
 * `users.notifications_seen_at`, which splits the feed into new and seen.
 *
 * Bounded to the last [WINDOW] and [LIMIT] newest items, so the cost stays a
 * handful of indexed lookups per request.
 */
@Service
class NotificationService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val submissionRepository: SubmissionRepository,
    private val evaluationRepository: EvaluationRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val scenarioRepository: ScenarioRepository,
    private val contentItemRepository: ContentItemRepository,
    private val buildSubmissionRepository: BuildSubmissionRepository,
    private val buildChallengeRepository: BuildChallengeRepository,
    private val buildStageRepository: BuildStageRepository,
    private val invitationRepository: OrganizationInvitationRepository,
    private val organizationRepository: OrganizationRepository,
    private val discussionRepository: ScenarioDiscussionRepository,
) {

    fun feed(userId: UUID, now: Instant = Instant.now()): NotificationFeed {
        val user = load(userId)
        val since = now.minus(WINDOW)
        val raw = (evaluations(userId, since) + builds(userId, since) + invitations(user, now) + discussions(userId, since))
            .sortedByDescending { it.at }
            .take(LIMIT)
        val seenAt = user.notificationsSeenAt
        val items = raw.map { it.copy(unseen = seenAt == null || it.at.isAfter(seenAt)) }
        return NotificationFeed(unseenCount = items.count { it.unseen }, items = items)
    }

    @Transactional
    fun markSeen(userId: UUID, now: Instant = Instant.now()) {
        val user = load(userId)
        user.notificationsSeenAt = now
        userRepository.save(user)
    }

    private fun load(userId: UUID): User = userRepository.findById(userId).orElseThrow { NotFoundException("User not found: $userId") }

    private fun evaluations(userId: UUID, since: Instant): List<NotificationItem> {
        val sessions = sessionRepository.findByUserIdOrderByStartedAtDesc(userId)
        if (sessions.isEmpty()) return emptyList()
        val submissions = submissionRepository.findBySessionIdIn(sessions.mapNotNull { it.id }).associateBy { it.id }
        val titleByVersion = scenarioTitles(sessions.map { it.scenarioVersionId })
        val versionBySession = sessions.associate { it.id to it.scenarioVersionId }
        return evaluationRepository.findBySubmissionIdInAndIsActiveTrue(submissions.keys.filterNotNull())
            .filter { it.createdAt != null && it.createdAt!!.isAfter(since) }
            .mapNotNull { evaluation ->
                val submission = submissions[evaluation.submissionId] ?: return@mapNotNull null
                val title = titleByVersion[versionBySession[submission.sessionId]] ?: "Drill"
                NotificationItem(
                    type = NotificationType.EVALUATION_READY,
                    title = "$title · ${PHASE_LABELS[submission.phase] ?: submission.phase} 채점 완료",
                    body = evaluation.totalScore?.let { "${it}점" },
                    href = "/design/${submission.sessionId}",
                    at = evaluation.createdAt!!,
                    unseen = false,
                )
            }
    }

    private fun builds(userId: UUID, since: Instant): List<NotificationItem> {
        val finished = buildSubmissionRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .filter { it.status == BuildSubmissionStatus.COMPLETED && it.completedAt?.isAfter(since) == true }
        if (finished.isEmpty()) return emptyList()
        val challenges = buildChallengeRepository.findAllById(finished.map { it.challengeId }.toSet()).associateBy { it.id }
        val stageCounts = challenges.keys.filterNotNull().associateWith { buildStageRepository.findByChallengeIdOrderByStageOrderAsc(it).size }
        return finished.map { submission ->
            NotificationItem(
                type = NotificationType.BUILD_GRADED,
                title = "${challenges[submission.challengeId]?.title ?: "Build"} 채점 완료",
                body = "${submission.score ?: 0} / ${stageCounts[submission.challengeId] ?: 0} 통과",
                href = "/bridge",
                at = submission.completedAt!!,
                unseen = false,
            )
        }
    }

    /** Pending and unexpired only — an invitation stays in the feed until it's answered, regardless of the window. */
    private fun invitations(user: User, now: Instant): List<NotificationItem> {
        val pending = invitationRepository.findByInviteeEmailAndStatus(user.email.lowercase(), OrganizationInvitationStatus.PENDING)
            .filter { it.expiresAt.isAfter(now) && it.createdAt != null }
        if (pending.isEmpty()) return emptyList()
        val orgs = organizationRepository.findAllById(pending.map { it.organizationId }.toSet()).associateBy { it.id }
        return pending.map { invitation ->
            NotificationItem(
                type = NotificationType.ORGANIZATION_INVITATION,
                title = "${orgs[invitation.organizationId]?.name ?: "조직"} 초대",
                body = "초대를 수락하면 팀 커리큘럼과 대시보드를 함께 씁니다",
                href = "/organizations/invitations/${invitation.token}",
                at = invitation.createdAt!!,
                unseen = false,
            )
        }
    }

    /** Messages by others, posted after my first message in that thread, and not hidden by moderation. */
    private fun discussions(userId: UUID, since: Instant): List<NotificationItem> {
        val mine = discussionRepository.findByAuthorUserId(userId)
        if (mine.isEmpty()) return emptyList()
        val joinedAt = mine.filter { it.createdAt != null }.groupBy { it.scenarioVersionId }.mapValues { (_, posts) -> posts.minOf { it.createdAt!! } }
        val others = discussionRepository.findByScenarioVersionIdInAndCreatedAtAfter(joinedAt.keys, since)
            .filter { it.authorUserId != userId && it.hiddenAt == null && it.createdAt != null }
            .filter { it.createdAt!!.isAfter(joinedAt.getValue(it.scenarioVersionId)) }
        if (others.isEmpty()) return emptyList()
        val versions = scenarioVersionRepository.findAllById(others.map { it.scenarioVersionId }.toSet()).associateBy { it.id }
        val titles = scenarioTitles(versions.keys.filterNotNull())
        val nicknames = userRepository.findAllById(others.map { it.authorUserId }.toSet()).associate { it.id to it.nickname }
        return others.mapNotNull { message ->
            val scenarioId = versions[message.scenarioVersionId]?.scenarioId ?: return@mapNotNull null
            NotificationItem(
                type = NotificationType.DISCUSSION_MESSAGE,
                title = "${titles[message.scenarioVersionId] ?: "Drill"} 토론에 새 글",
                body = "${nicknames[message.authorUserId] ?: "누군가"}: ${message.body.take(BODY_PREVIEW)}",
                href = "/discussions/$scenarioId",
                at = message.createdAt!!,
                unseen = false,
            )
        }
    }

    private fun scenarioTitles(versionIds: Collection<UUID>): Map<UUID, String> {
        val versions = scenarioVersionRepository.findAllById(versionIds.toSet())
        val scenarios = scenarioRepository.findAllById(versions.map { it.scenarioId }.toSet()).associateBy { it.id }
        val contents = contentItemRepository.findAllById(scenarios.values.map { it.contentId }.toSet()).associateBy { it.id }
        return versions.mapNotNull { version ->
            val scenario = scenarios[version.scenarioId] ?: return@mapNotNull null
            version.id!! to (contents[scenario.contentId]?.title ?: scenario.domain)
        }.toMap()
    }

    companion object {
        val WINDOW: Duration = Duration.ofDays(30)
        const val LIMIT = 20
        private const val BODY_PREVIEW = 60
        private val PHASE_LABELS = mapOf("INITIAL" to "초기 설계", "FOLLOWUP" to "꼬리설계", "INCIDENT" to "장애 대응")
    }
}
