package com.sysdrill.backend.auth

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** PLAN.md step 37 — "Google로 계속하기". Both endpoints are plain browser navigations (redirects), not fetch/XHR, so CORS never applies here. */
@RestController
@RequestMapping("/auth/google")
class GoogleAuthController(
    private val googleAuthService: GoogleAuthService,
    @Value("\${sysdrill.frontend-origin}") private val frontendOrigin: String,
) {

    @GetMapping("/login")
    fun login(): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND).location(URI.create(googleAuthService.buildAuthorizationUrl())).build()

    /** Token travels in the URL fragment, not the query string — browsers never send a fragment back to a server, so it never lands in access logs. */
    @GetMapping("/callback")
    fun callback(@RequestParam code: String, @RequestParam state: String): ResponseEntity<Void> {
        val result = googleAuthService.handleCallback(code, state)
        val encodedNickname = URLEncoder.encode(result.nickname, StandardCharsets.UTF_8)
        val destination = "$frontendOrigin/auth/google/complete#token=${result.token}&nickname=$encodedNickname"
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destination)).build()
    }
}
