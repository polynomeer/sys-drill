package com.sysdrill.backend.auth

import com.sysdrill.backend.common.web.UnauthorizedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

data class GoogleUserInfo(val email: String, val emailVerified: Boolean, val name: String?)

/** PLAN.md step 37 — isolates Google's OAuth2 endpoints behind an interface so tests (which can't authenticate as a real Google account) substitute a fake implementation instead, the same reason [com.sysdrill.backend.evaluation.llm.LlmClient] exists. */
interface GoogleOAuthClient {
    fun exchangeCodeForAccessToken(code: String): String
    fun fetchUserInfo(accessToken: String): GoogleUserInfo
}

/**
 * Trusts Google's `userinfo` endpoint response rather than locally verifying
 * the ID token's JWT signature — the latter needs fetching and caching
 * Google's JWKS and handling key rotation, machinery this codebase has
 * avoided elsewhere in favor of direct, small implementations (see
 * docs/adr/0027). The access token itself is proof enough: only the real
 * Google can issue one that this endpoint accepts.
 */
@Component
class GoogleOAuthClientImpl(
    @Value("\${sysdrill.auth.google.client-id}") private val clientId: String,
    @Value("\${sysdrill.auth.google.client-secret}") private val clientSecret: String,
    @Value("\${sysdrill.auth.google.redirect-uri}") private val redirectUri: String,
) : GoogleOAuthClient {
    private val restClient = RestClient.create()

    override fun exchangeCodeForAccessToken(code: String): String {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("code", code)
            add("client_id", clientId)
            add("client_secret", clientSecret)
            add("redirect_uri", redirectUri)
            add("grant_type", "authorization_code")
        }
        val response = restClient.post()
            .uri("https://oauth2.googleapis.com/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map::class.java)
            ?: throw UnauthorizedException("Google token exchange returned no body")
        return response["access_token"] as? String
            ?: throw UnauthorizedException("Google token exchange did not return an access token")
    }

    override fun fetchUserInfo(accessToken: String): GoogleUserInfo {
        val response = restClient.get()
            .uri("https://www.googleapis.com/oauth2/v3/userinfo")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(Map::class.java)
            ?: throw UnauthorizedException("Google userinfo returned no body")
        val email = response["email"] as? String ?: throw UnauthorizedException("Google userinfo did not include an email")
        val emailVerified = when (val v = response["email_verified"]) {
            is Boolean -> v
            is String -> v.toBoolean()
            else -> false
        }
        return GoogleUserInfo(email = email, emailVerified = emailVerified, name = response["name"] as? String)
    }
}
