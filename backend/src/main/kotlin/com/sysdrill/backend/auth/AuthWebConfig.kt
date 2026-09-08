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
 * `/marketplace/scenarios` (Phase 5, docs/adr/0031) requires auth on every
 * sub-path including plain browsing — unlike `/scenarios`, which stays
 * public reference data. This doesn't cost discoverability: a published
 * marketplace scenario already appears on the public `/scenarios` list too
 * (both have organizationId == null), so `/marketplace/scenarios` is just
 * the dedicated hub for publishing/browsing/"my scenarios", not the only
 * way to find one.
 * `/certifications/me` (Phase 5, docs/adr/0032) is registered as this exact
 * literal path, deliberately not a wildcarded sub-path pattern — `GET
 * /certifications/{userId}` is a public verification page anyone can hit
 * without a token, the same reasoning `/scenarios` stays open.
 * `GET /organizations/assessments/{token}` (Phase 5, docs/adr/0033) is
 * excluded from the blanket sub-path gate above — unlike an invitation
 * recipient (an existing user the frontend sends straight to `/login`), an
 * assessment candidate is typically a first-time visitor who needs to see
 * what they're being asked to do (org name, scenario) before deciding to
 * sign up at all; gating the preview would make that impossible. `POST
 * .../start` stays gated (needs `@AuthenticatedUserId`) — the exclusion
 * pattern's single `*` matches only the token segment, not `/start`.
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
                "/marketplace/scenarios", "/marketplace/scenarios/**",
                "/certifications/me",
            )
            .excludePathPatterns("/sessions/*/simulation/realinfra/coupon/**", "/organizations/assessments/*")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(authenticatedUserIdArgumentResolver)
    }
}
