package com.sysdrill.backend.notification

import java.time.Instant

enum class NotificationType { EVALUATION_READY, BUILD_GRADED, ORGANIZATION_INVITATION, DISCUSSION_MESSAGE }

data class NotificationItem(
    val type: NotificationType,
    val title: String,
    val body: String?,
    /** Frontend route the item opens. */
    val href: String,
    val at: Instant,
    val unseen: Boolean,
)

data class NotificationFeed(
    val unseenCount: Int,
    val items: List<NotificationItem>,
)
