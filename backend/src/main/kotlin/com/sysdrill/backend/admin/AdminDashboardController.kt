package com.sysdrill.backend.admin

import com.sysdrill.backend.auth.AuthenticatedUserId
import com.sysdrill.backend.auth.PlatformAccessGuard
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.organization.OrganizationRepository
import com.sysdrill.backend.session.SessionRepository
import com.sysdrill.backend.session.SessionStatus
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** docs/COMMERCIALIZATION.md — minimal platform-operator visibility, gated the same way as [com.sysdrill.backend.evaluation.PromptTemplateController]'s `/admin/prompt-templates`. */
@RestController
@RequestMapping("/admin/dashboard")
class AdminDashboardController(
    private val accessGuard: PlatformAccessGuard,
    private val userRepository: UserRepository,
    private val organizationRepository: OrganizationRepository,
    private val sessionRepository: SessionRepository,
) {

    @GetMapping("/stats")
    fun stats(@AuthenticatedUserId userId: UUID): AdminDashboardStatsResponse {
        accessGuard.requirePlatformAdmin(userId)
        val startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant()
        return AdminDashboardStatsResponse(
            totalUsers = userRepository.count(),
            newUsersToday = userRepository.countByCreatedAtAfter(startOfToday),
            totalOrganizations = organizationRepository.count(),
            sessionsCompletedToday = sessionRepository.countByStatusAndCompletedAtAfter(SessionStatus.COMPLETED, startOfToday),
        )
    }
}
