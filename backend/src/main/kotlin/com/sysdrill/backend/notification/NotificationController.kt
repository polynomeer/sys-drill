package com.sysdrill.backend.notification

import com.sysdrill.backend.auth.AuthenticatedUserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Under /me (authenticated, AuthWebConfig) — always the caller's own feed. */
@RestController
class NotificationController(private val notificationService: NotificationService) {

    @GetMapping("/me/notifications")
    fun feed(@AuthenticatedUserId userId: UUID): NotificationFeed = notificationService.feed(userId)

    /** Opening the list marks everything up to now as seen. */
    @PostMapping("/me/notifications/seen")
    fun markSeen(@AuthenticatedUserId userId: UUID): ResponseEntity<Void> {
        notificationService.markSeen(userId)
        return ResponseEntity.noContent().build()
    }
}
