package com.sysdrill.backend.metrics

import com.sysdrill.backend.build.BuildChallengeRepository
import com.sysdrill.backend.build.BuildStageRepository
import com.sysdrill.backend.build.BuildStageResultRepository
import com.sysdrill.backend.build.BuildStageStatus
import com.sysdrill.backend.build.BuildSubmissionRepository
import com.sysdrill.backend.build.BuildSubmissionStatus
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

data class SuccessMetricsResponse(
    /** Users who signed up 7–90 days ago (old enough to have had 7 days). */
    val cohortSize: Int,
    /** Of those, the share with a COMPLETED Drill within 7 days of signing up (0–100, null if no cohort). */
    val firstDrillWithin7DaysPercent: Int?,
    /** Median minutes from a user's first Build submission to their first stage-1 pass (null if nobody passed yet). */
    val medianMinutesToFirstBuildPass: Long?,
    /** Learners per "highest stage passed in order" on their latest graded Build submission (0 = stuck at stage 1). */
    val buildProgressDistribution: Map<Int, Int>,
    /** Event counters over the last 30 days. */
    val events30d: Map<String, Long>,
    /** OVERVIEW_START / OVERVIEW_VIEW over the last 30 days (0–100, null if no views). */
    val overviewToStartPercent: Int?,
)

/**
 * docs/CODECRAFTERS_BENCHMARK.md §6 (PLAN.md Round B17). Everything that a
 * row already answers is computed from rows (read time, ADR-0011); only the
 * page-view funnel comes from [ProductEventService]'s anonymous counters.
 * Platform-operator view — individual users never appear in the output.
 */
@Service
class SuccessMetricsService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val buildSubmissionRepository: BuildSubmissionRepository,
    private val buildStageResultRepository: BuildStageResultRepository,
    private val buildStageRepository: BuildStageRepository,
    private val buildChallengeRepository: BuildChallengeRepository,
    private val productEventService: ProductEventService,
) {

    fun compute(now: Instant = Instant.now()): SuccessMetricsResponse {
        val cohort = userRepository.findAll().filter {
            val created = it.createdAt ?: return@filter false
            created.isBefore(now.minus(Duration.ofDays(7))) && created.isAfter(now.minus(Duration.ofDays(90)))
        }
        val activated = cohort.count { user ->
            val deadline = user.createdAt!!.plus(Duration.ofDays(7))
            sessionRepository.findByUserIdOrderByStartedAtDesc(user.id!!)
                .any { it.status == SessionStatus.COMPLETED && it.completedAt?.isBefore(deadline) == true }
        }

        val (medianMinutes, distribution) = buildMetrics()
        val events = productEventService.totals(30)
        val views = events.getValue(ProductEventService.OVERVIEW_VIEW)
        return SuccessMetricsResponse(
            cohortSize = cohort.size,
            firstDrillWithin7DaysPercent = if (cohort.isEmpty()) null else activated * 100 / cohort.size,
            medianMinutesToFirstBuildPass = medianMinutes,
            buildProgressDistribution = distribution,
            events30d = events,
            overviewToStartPercent = if (views == 0L) null else (events.getValue(ProductEventService.OVERVIEW_START) * 100 / views).toInt(),
        )
    }

    private fun buildMetrics(): Pair<Long?, Map<Int, Int>> {
        val stage1IdByChallenge = buildChallengeRepository.findAll().mapNotNull { challenge ->
            buildStageRepository.findByChallengeIdOrderByStageOrderAsc(challenge.id!!).firstOrNull()?.let { challenge.id!! to it.id!! }
        }.toMap()
        val stageOrderById = stage1IdByChallenge.keys.flatMap { buildStageRepository.findByChallengeIdOrderByStageOrderAsc(it) }
            .associate { it.id!! to it.stageOrder }
        val stageCountByChallenge = stage1IdByChallenge.keys.associateWith { buildStageRepository.findByChallengeIdOrderByStageOrderAsc(it).size }

        val minutesToPass = mutableListOf<Long>()
        val distribution = sortedMapOf<Int, Int>()
        buildSubmissionRepository.findAll().groupBy { it.userId to it.challengeId }.forEach { (key, submissions) ->
            val challengeId = key.second
            val ordered = submissions.filter { it.createdAt != null }.sortedBy { it.createdAt }
            val graded = ordered.filter { it.status == BuildSubmissionStatus.COMPLETED }
            val passedStagesOf = { submissionId: java.util.UUID ->
                buildStageResultRepository.findBySubmissionIdOrderByCreatedAtAsc(submissionId)
                    .filter { it.status == BuildStageStatus.PASSED }
                    .mapNotNull { stageOrderById[it.stageId] }
                    .toSet()
            }
            graded.firstOrNull { 1 in passedStagesOf(it.id!!) }?.let { firstPass ->
                minutesToPass += Duration.between(ordered.first().createdAt, firstPass.createdAt).toMinutes()
            }
            graded.lastOrNull()?.let { latest ->
                val passed = passedStagesOf(latest.id!!)
                val total = stageCountByChallenge[challengeId] ?: 0
                // "Highest stage passed in order" — the same notion of progress /bridge shows.
                val inOrder = (1..total).takeWhile { it in passed }.lastOrNull() ?: 0
                distribution[inOrder] = (distribution[inOrder] ?: 0) + 1
            }
        }
        val median = minutesToPass.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        return median to distribution
    }
}
