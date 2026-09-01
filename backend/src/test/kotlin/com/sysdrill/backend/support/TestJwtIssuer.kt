package com.sysdrill.backend.support

import com.sysdrill.backend.auth.JwtService
import org.springframework.stereotype.Component

/**
 * PLAN.md step 30 — `SessionTestSupport.startSession` is a bare top-level
 * extension function on `MockMvc`, so it has no way for Spring to inject a
 * [JwtService] into it directly. This component (picked up by every
 * `@SpringBootTest`'s component scan since it lives under
 * `com.sysdrill.backend` on the test classpath) captures the real
 * `JwtService` bean once the `ApplicationContext` is up, so `startSession`
 * can issue a valid token synchronously — without changing any of its
 * existing callers, which is the whole point of centralizing this here
 * instead of threading a `JwtService` through every test class.
 *
 * A static holder is normally something to avoid, but this codebase's tests
 * run sequentially in one JVM (no parallel test execution configured), so
 * there's no cross-context race to worry about.
 */
@Component
class TestJwtIssuer(jwtService: JwtService) {
    init {
        current = jwtService
    }

    companion object {
        lateinit var current: JwtService
    }
}
