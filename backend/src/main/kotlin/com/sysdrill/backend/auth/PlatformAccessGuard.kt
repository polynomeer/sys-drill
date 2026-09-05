package com.sysdrill.backend.auth

import com.sysdrill.backend.common.web.ForbiddenException
import com.sysdrill.backend.common.web.NotFoundException
import com.sysdrill.backend.identity.PlatformRole
import com.sysdrill.backend.identity.UserRepository
import java.util.UUID
import org.springframework.stereotype.Component

/**
 * PLAN.md step 35 — same shape as [com.sysdrill.backend.session.SessionAccessGuard]/
 * [com.sysdrill.backend.organization.OrganizationAccessGuard] (a plain
 * injectable component a controller calls explicitly as its first line), but
 * throws 403 rather than 404: there's no specific resource instance whose
 * existence needs hiding here, just a role gate on an endpoint's whole surface.
 */
@Component
class PlatformAccessGuard(private val userRepository: UserRepository) {

    fun requirePlatformAdmin(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found: $userId") }
        if (user.platformRole != PlatformRole.PLATFORM_ADMIN) {
            throw ForbiddenException("Platform admin role required")
        }
    }
}
