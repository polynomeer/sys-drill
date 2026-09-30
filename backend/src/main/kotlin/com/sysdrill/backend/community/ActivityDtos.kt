package com.sysdrill.backend.community

import java.time.Instant

/** One recent completer of a scenario — nickname and when, nothing about how well. */
data class RecentCompletionResponse(
    val nickname: String,
    val completedAt: Instant,
)

/** A public-profile timeline entry: which official scenario was completed, and when. No score, no failures. */
data class ActivityEntryResponse(
    val scenarioTitle: String,
    val domain: String,
    val completedAt: Instant,
)

data class UserActivityResponse(
    val nickname: String,
    /** True when the user turned ranking visibility off — the timeline is then empty on purpose, not because they did nothing. */
    val hidden: Boolean,
    val entries: List<ActivityEntryResponse>,
)
