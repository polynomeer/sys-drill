package com.sysdrill.backend.community

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.common.web.ForbiddenException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.content.ContentItemRepository
import com.sysdrill.backend.organization.AssessmentSessions
import com.sysdrill.backend.scenario.ScenarioRepository
import com.sysdrill.backend.scenario.ScenarioVersionRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class MyDrillActivity(
    val scenarioId: UUID,
    val title: String,
    val domain: String,
    /** Other people's writeups now public. */
    val writeups: Int,
    val newWriteups: Int,
    val newDiscussions: Int,
)

data class ActiveDiscussionPost(val id: UUID, val kind: String, val excerpt: String?, val spoilerLocked: Boolean)

data class ActiveDiscussion(val scenarioId: UUID, val title: String, val postsThisWeek: Int, val latest: List<ActiveDiscussionPost>)

data class NotableWriteup(
    val sessionId: UUID,
    val scenarioId: UUID,
    val scenarioTitle: String,
    /** Null when anonymous or [locked]. */
    val authorNickname: String?,
    val reviewCount: Int,
    /** The viewer hasn't completed the scenario — title and count only (ADR-0041). */
    val locked: Boolean,
)

data class CommunityHome(
    val myDrills: List<MyDrillActivity>,
    val activeDiscussions: List<ActiveDiscussion>,
    val notableWriteups: List<NotableWriteup>,
)

/**
 * docs/COMMUNITY_EXPANSION_PLAN.md C11 (PLAN.md Round E23) — the Community home: what's new in the
 * Drills I did, which threads are busy this week, and which writeups drew the most reviews.
 * "New" is relative to [since], the client's last visit (kept in localStorage — a convenience,
 * so nothing about it is stored on the server).
 */
@Service
class CommunityHomeService(
    private val sessionRepository: SessionRepository,
    private val scenarioRepository: ScenarioRepository,
    private val scenarioVersionRepository: ScenarioVersionRepository,
    private val contentItemRepository: ContentItemRepository,
    private val discussionRepository: ScenarioDiscussionRepository,
    private val writeupService: WriteupService,
    private val commentService: WriteupCommentService,
    private val assessmentSessions: AssessmentSessions,
) {
    fun home(viewerId: UUID, since: Instant?, now: Instant = Instant.now()): CommunityHome {
        val weekAgo = now.minus(Duration.ofDays(7))
        val cutoff = since ?: weekAgo
        val publicScenarios = scenarioRepository.findByOrganizationIdIsNull().filter { it.visibility == "PUBLIC" }
        val titles = contentItemRepository.findAllById(publicScenarios.map { it.contentId }).associate { it.id to it.title }
        val titleOf = publicScenarios.associate { it.id!! to (titles[it.contentId] ?: it.domain) }
        val versions = scenarioVersionRepository.findByScenarioIdIn(publicScenarios.mapNotNull { it.id })
        val scenarioOfVersion = versions.associate { it.id!! to it.scenarioId }

        // ---- 내 Drill에서 ----
        val mine = sessionRepository.findByUserIdOrderByStartedAtDesc(viewerId).filter { it.status == SessionStatus.COMPLETED }
        val assessments = assessmentSessions.among(mine.mapNotNull { it.id })
        val myScenarioIds = mine.filterNot { it.id in assessments }.mapNotNull { scenarioOfVersion[it.scenarioVersionId] }.distinct()
        val recentPosts = discussionRepository.findByScenarioVersionIdInAndCreatedAtAfter(versions.mapNotNull { it.id }, minOf(cutoff, weekAgo))
            .filter { it.hiddenAt == null }
        val myDrills = myScenarioIds.take(MAX_MY_DRILLS).mapNotNull { scenarioId ->
            val scenario = publicScenarios.firstOrNull { it.id == scenarioId } ?: return@mapNotNull null
            val list = runCatching { writeupService.listForScenario(scenarioId, viewerId, sort = "recent") }.getOrNull() ?: return@mapNotNull null
            val others = list.writeups.filterNot { it.mine }
            MyDrillActivity(
                scenarioId = scenarioId,
                title = titleOf[scenarioId] ?: scenario.domain,
                domain = scenario.domain,
                writeups = others.size,
                newWriteups = others.count { (it.sharedAt ?: it.completedAt)?.isAfter(cutoff) == true },
                newDiscussions = recentPosts.count {
                    scenarioOfVersion[it.scenarioVersionId] == scenarioId && it.authorUserId != viewerId && it.createdAt?.isAfter(cutoff) == true
                },
            )
        }

        // ---- 이번 주 활발한 토론 ----
        val completedScenarios = myScenarioIds.toSet()
        val active = recentPosts.filter { it.createdAt?.isAfter(weekAgo) == true }
            .groupBy { scenarioOfVersion[it.scenarioVersionId] }
            .filterKeys { it != null }
            .entries.sortedByDescending { it.value.size }
            .take(MAX_ACTIVE)
            .map { (scenarioId, posts) ->
                ActiveDiscussion(
                    scenarioId = scenarioId!!,
                    title = titleOf[scenarioId] ?: "",
                    postsThisWeek = posts.size,
                    latest = posts.sortedByDescending { it.createdAt }.take(2).map { post ->
                        // A spoiler post shows nothing but its kind to someone who hasn't done the Drill (C7).
                        val locked = post.containsSpoiler && scenarioId !in completedScenarios && post.authorUserId != viewerId
                        ActiveDiscussionPost(post.id!!, post.kind, if (locked) null else post.body.lineSequence().first().take(EXCERPT), locked)
                    },
                )
            }

        // ---- 주목할 풀이 ----
        val publicSessionIds = sessionRepository.findByScenarioVersionIdInAndVisibilityOrderBySharedAtDesc(versions.mapNotNull { it.id }, "PUBLIC")
            .mapNotNull { it.id }
        val notable = commentService.countsBySession(publicSessionIds).entries.sortedByDescending { it.value }.take(MAX_NOTABLE * 2)
            .mapNotNull { (sessionId, count) ->
                val scenarioId = writeupService.scenarioIdOf(sessionId) ?: return@mapNotNull null
                try {
                    val detail = writeupService.detail(sessionId, viewerId)
                    if (detail.mine) return@mapNotNull null
                    NotableWriteup(sessionId, scenarioId, detail.scenarioTitle, detail.authorNickname, count, locked = false)
                } catch (_: ForbiddenException) {
                    NotableWriteup(sessionId, scenarioId, titleOf[scenarioId] ?: "", null, count, locked = true)
                } catch (_: NotFoundException) {
                    null // went private, or not shareable
                }
            }
            .take(MAX_NOTABLE)

        return CommunityHome(myDrills, active, notable)
    }

    private companion object {
        const val MAX_MY_DRILLS = 7
        const val MAX_ACTIVE = 5
        const val MAX_NOTABLE = 5
        const val EXCERPT = 80
    }
}

@RestController
class CommunityHomeController(private val service: CommunityHomeService) {
    /** [since] — the client's last visit (ISO-8601); omitted → the last 7 days. */
    @GetMapping("/community/home")
    fun home(@AuthenticatedUserId userId: UUID, @RequestParam(required = false) since: Instant?): CommunityHome =
        service.home(userId, since)
}
