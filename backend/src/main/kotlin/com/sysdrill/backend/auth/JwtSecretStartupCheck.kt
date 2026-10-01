package com.sysdrill.backend.auth

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * docs/COMMERCIALIZATION.md — `sysdrill.auth.jwt-secret`'s insecure default
 * (`application.yml`) is deliberately fine for `bootRun`/tests (ADR-0003's
 * trust boundary), so this can't just check the value everywhere -- that
 * would break every `@SpringBootTest`. [deploymentMode] is the one signal
 * this repo actually has for "this is a real deployment, not a dev/test
 * run": `Dockerfile` sets `SYSDRILL_DEPLOYMENT_MODE=container`, which
 * nothing else (bootRun, tests) ever sets. Fails fast at context startup
 * rather than letting the app serve traffic signed with a secret anyone can
 * read in this repo's source.
 */
@Component
class JwtSecretStartupCheck(
    @Value("\${sysdrill.auth.jwt-secret}") private val jwtSecret: String,
    @Value("\${sysdrill.deployment-mode:}") private val deploymentMode: String,
) {
    @PostConstruct
    fun verify() {
        check(!(deploymentMode == "container" && jwtSecret == INSECURE_DEFAULT)) {
            "sysdrill.auth.jwt-secret is still the insecure default in a container deployment -- " +
                "set SYSDRILL_AUTH_JWT_SECRET to a real secret."
        }
    }

    private companion object {
        const val INSECURE_DEFAULT = "dev-only-insecure-secret-change-me"
    }
}
