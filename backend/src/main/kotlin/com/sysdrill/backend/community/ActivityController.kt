package com.sysdrill.backend.community

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.8. Under the /community prefix so both need a
 * signed-in viewer (AuthWebConfig) — unlike the public certification page,
 * activity is only shown to other members, never to anonymous visitors.
 */
@RestController
class ActivityController(private val activityService: ActivityService) {

    @GetMapping("/community/scenarios/{scenarioId}/recent-completions")
    fun recentCompletions(@PathVariable scenarioId: UUID): List<RecentCompletionResponse> =
        activityService.recentCompletions(scenarioId)

    @GetMapping("/community/users/{userId}/activity")
    fun userActivity(@PathVariable userId: UUID): UserActivityResponse = activityService.userActivity(userId)
}
