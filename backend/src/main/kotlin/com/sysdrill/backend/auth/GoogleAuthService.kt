package com.sysdrill.backend.auth

import com.sysdrill.backend.common.web.BadRequestException
import com.sysdrill.backend.common.web.UnauthorizedException
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class GoogleCallbackResult(val token: String, val nickname: String)

/**
 * PLAN.md step 37 — "Google로 계속하기": an alternative to email/password
 * signup/login (docs/adr/0027), not an organization-scoped enterprise SSO.
 * An email Google has verified is trusted as proof of that email's identity,
 * matching this app's existing trust boundary (docs/adr/0003 already accepts
 * unverified self-reported email at regular signup — a Google-verified email
 * is strictly stronger).
 */
@Service
class GoogleAuthService(
    private val googleOAuthClient: GoogleOAuthClient,
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val redisTemplate: StringRedisTemplate,
    @Value("\${sysdrill.auth.google.client-id}") private val clientId: String,
    @Value("\${sysdrill.auth.google.redirect-uri}") private val redirectUri: String,
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    fun buildAuthorizationUrl(): String {
        if (clientId.isBlank()) throw BadRequestException("Google 로그인이 이 서버에 설정되지 않았습니다.")
        val state = UUID.randomUUID().toString()
        redisTemplate.opsForValue().set(stateKey(state), "1", Duration.ofMinutes(5))
        val encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        return "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=$clientId" +
            "&redirect_uri=$encodedRedirectUri" +
            "&response_type=code" +
            "&scope=${URLEncoder.encode("openid email profile", StandardCharsets.UTF_8)}" +
            "&state=$state"
    }

    @Transactional
    fun handleCallback(code: String, state: String): GoogleCallbackResult {
        val consumed = redisTemplate.delete(stateKey(state))
        if (consumed != true) throw UnauthorizedException("Invalid or expired state")

        val accessToken = googleOAuthClient.exchangeCodeForAccessToken(code)
        val info = googleOAuthClient.fetchUserInfo(accessToken)
        if (!info.emailVerified) throw UnauthorizedException("Google email is not verified")

        val user = userRepository.findByEmail(info.email) ?: userRepository.save(
            User(
                email = info.email,
                // Nobody knows this value — password login for a Google-only
                // account is cryptographically impossible, not just disabled.
                passwordHash = passwordEncoder.encode(UUID.randomUUID().toString())!!,
                nickname = info.name?.takeIf { it.isNotBlank() } ?: info.email.substringBefore("@"),
            )
        )
        return GoogleCallbackResult(token = jwtService.issue(user.id!!), nickname = user.nickname)
    }

    private fun stateKey(state: String) = "sysdrill:auth:google:state:$state"
}
