package com.sysdrill.backend.auth

import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * PLAN.md step 30 — wires [AuthInterceptor] onto exactly `POST /sessions`
 * (the literal path `/sessions` matches only the session-creation endpoint,
 * not `/sessions/{id}` or its sub-paths — see [com.sysdrill.backend.session.SessionController]).
 * Every other user-scoped endpoint still trusts a client-supplied userId
 * until PLAN.md 31단계 migrates them too — this is the intentionally narrow
 * first slice, not an oversight.
 */
@Configuration
class AuthWebConfig(
    private val authInterceptor: AuthInterceptor,
    private val authenticatedUserIdArgumentResolver: AuthenticatedUserIdArgumentResolver,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/sessions")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(authenticatedUserIdArgumentResolver)
    }
}
