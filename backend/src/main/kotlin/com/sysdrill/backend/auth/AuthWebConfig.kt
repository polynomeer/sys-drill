package com.sysdrill.backend.auth

import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * PLAN.md step 31 — wires [AuthInterceptor] onto every user-owned resource
 * path. The `/sessions` prefix with a trailing Ant wildcard covers session
 * CRUD plus everything nested under a session (simulation, report,
 * postmortem) since they're all sub-paths of `/sessions/{sessionId}/...`.
 * [com.sysdrill.backend.simulation.realinfra.RealInfraCouponController] is
 * carved out via `excludePathPatterns` even though it lives under that
 * prefix — it's hit directly by a k6 Docker container (PLAN.md step 21),
 * not the browser, and k6 has no user JWT to send. `/admin/prompt-templates`
 * and `/scenarios` stay unauthenticated on purpose: the former needs an
 * RBAC/role system this app doesn't have yet (PLAN.md 35단계+), the latter
 * is public reference data.
 */
@Configuration
class AuthWebConfig(
    private val authInterceptor: AuthInterceptor,
    private val authenticatedUserIdArgumentResolver: AuthenticatedUserIdArgumentResolver,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns(
                "/sessions", "/sessions/**",
                "/submissions/**",
                "/build-challenges/**", "/build-submissions/**",
                "/skill-profile",
            )
            .excludePathPatterns("/sessions/*/simulation/realinfra/coupon/**")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(authenticatedUserIdArgumentResolver)
    }
}
