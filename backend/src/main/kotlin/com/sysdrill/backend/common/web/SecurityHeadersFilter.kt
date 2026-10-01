package com.sysdrill.backend.common.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * docs/COMMERCIALIZATION.md — this is a JSON API with no Spring Security
 * filter chain (auth is the custom [com.sysdrill.backend.auth.AuthInterceptor]/
 * [com.sysdrill.backend.auth.JwtService]), so there's nowhere these headers
 * already get added. CSP doesn't apply here the way it does on the frontend
 * (no script-execution context to restrict) -- this is the smaller set that
 * does: don't let a browser guess a response's content type, don't let this
 * ever be framed, don't leak the full request URL as a Referer header.
 */
@Component
class SecurityHeadersFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("X-Frame-Options", "DENY")
        response.setHeader("Referrer-Policy", "no-referrer")
        filterChain.doFilter(request, response)
    }
}
