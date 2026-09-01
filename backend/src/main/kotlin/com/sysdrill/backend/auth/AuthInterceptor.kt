package com.sysdrill.backend.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/** Request attribute [AuthInterceptor] stores the verified userId under — read by [AuthenticatedUserIdArgumentResolver]. */
const val AUTHENTICATED_USER_ID_ATTRIBUTE = "authenticatedUserId"

/**
 * PLAN.md step 30 — gates whichever paths [AuthWebConfig] registers this on
 * (starting with just `POST /sessions`, per the "점진적" scope this step
 * chose — see PLAN.md 31단계 for migrating the rest). Rejects with 401
 * before the controller method ever runs if the `Authorization: Bearer
 * <token>` header is missing or the token doesn't verify.
 */
@Component
class AuthInterceptor(private val jwtService: JwtService) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // CORS preflight requests never carry Authorization (browsers strip it),
        // so gating them here would make every cross-origin POST /sessions fail
        // before the real request is even sent. CorsConfig handles OPTIONS.
        if (request.method == "OPTIONS") return true

        val header = request.getHeader("Authorization")
        val token = header?.removePrefix("Bearer ")?.takeIf { header.startsWith("Bearer ") }
        val userId = token?.let { jwtService.verify(it) }

        if (userId == null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("""{"status":401,"message":"missing or invalid Authorization token"}""")
            return false
        }

        request.setAttribute(AUTHENTICATED_USER_ID_ATTRIBUTE, userId)
        return true
    }
}
