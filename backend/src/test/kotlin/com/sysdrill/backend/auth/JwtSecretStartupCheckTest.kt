package com.sysdrill.backend.auth

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Test

/**
 * docs/COMMERCIALIZATION.md — plain unit test, not @SpringBootTest: the
 * whole point is to assert a context FAILS to start under one combination
 * of inputs, and [JwtSecretStartupCheck] has no collaborators to mock, just
 * two String values -- a real Spring context boot would be slower and test
 * the same four-line branch no more precisely.
 */
class JwtSecretStartupCheckTest {

    @Test
    fun `a container deployment with the insecure default secret fails fast`() {
        assertThatThrownBy { JwtSecretStartupCheck(jwtSecret = "dev-only-insecure-secret-change-me", deploymentMode = "container").verify() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `a container deployment with a real secret starts fine`() {
        assertThatNoException().isThrownBy {
            JwtSecretStartupCheck(jwtSecret = "a-real-production-secret", deploymentMode = "container").verify()
        }
    }

    @Test
    fun `bootRun or tests (no deployment mode) are never blocked, even with the default secret`() {
        assertThatNoException().isThrownBy {
            JwtSecretStartupCheck(jwtSecret = "dev-only-insecure-secret-change-me", deploymentMode = "").verify()
        }
    }
}
