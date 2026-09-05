package com.sysdrill.backend.support

import com.sysdrill.backend.auth.GoogleOAuthClient
import com.sysdrill.backend.auth.GoogleUserInfo
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** PLAN.md step 37 — Google's own servers can't be exercised in a test, so this substitutes a controllable fake for [GoogleOAuthClient] via ordinary Spring DI (not a mocking framework — this codebase uses none). Import into a test class with `@Import(FakeGoogleOAuthConfig::class)`. */
class FakeGoogleOAuthClient : GoogleOAuthClient {
    var nextUserInfo: GoogleUserInfo = GoogleUserInfo(email = "fake@example.com", emailVerified = true, name = "Fake User")

    override fun exchangeCodeForAccessToken(code: String): String = "fake-access-token-for-$code"

    override fun fetchUserInfo(accessToken: String): GoogleUserInfo = nextUserInfo
}

@TestConfiguration
class FakeGoogleOAuthConfig {
    @Bean
    @Primary
    fun googleOAuthClient(): GoogleOAuthClient = FakeGoogleOAuthClient()
}
