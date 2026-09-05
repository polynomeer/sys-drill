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
 * not the browser, and k6 has no user JWT to send. `/scenarios` stays
 * unauthenticated on purpose — it's public reference data (org-scoped
 * scenarios are filtered out of it, PLAN.md step 34). `/organizations`
 * (PLAN.md step 32) was added the same way — every sub-path, including
 * invitation preview/accept, requires a caller identity even where
 * [OrganizationAccessGuard] doesn't additionally require membership.
 * `/admin/prompt-templates` (PLAN.md step 35) additionally requires the
 * platform-admin role via [PlatformAccessGuard], checked explicitly in
 * the controller rather than here.
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
                "/organizations", "/organizations/**",
                "/admin/prompt-templates", "/admin/prompt-templates/**",
            )
            .excludePathPatterns("/sessions/*/simulation/realinfra/coupon/**")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(authenticatedUserIdArgumentResolver)
    }
}
