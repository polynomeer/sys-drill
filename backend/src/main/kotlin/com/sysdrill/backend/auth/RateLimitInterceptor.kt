package com.sysdrill.backend.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/** docs/COMMERCIALIZATION.md — caps request volume per client IP on the public auth endpoints [AuthWebConfig] registers this on, ahead of [AuthInterceptor] gating identity-bearing paths. */
@Component
class RateLimitInterceptor(
    private val rateLimiter: RateLimiter,
    @Value("\${sysdrill.auth.rate-limit.max-requests-per-minute}") private val maxRequestsPerMinute: Long,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method == "OPTIONS") return true

        val clientIp = request.getHeader("X-Forwarded-For")?.substringBefore(",")?.trim() ?: request.remoteAddr
        val key = "${request.requestURI}:$clientIp"
        if (!rateLimiter.tryAcquire(key, maxRequestsPerMinute, Duration.ofMinutes(1))) {
            response.status = 429
            response.contentType = "application/json"
            response.writer.write("""{"status":429,"message":"Too many requests -- please try again later"}""")
            return false
        }
        return true
    }
}
