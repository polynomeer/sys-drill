package com.sysdrill.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * PLAN.md step 30 — issues/verifies the HMAC-signed JWTs real auth uses.
 * Deliberately a thin wrapper around a well-audited library (com.auth0:java-jwt),
 * not hand-rolled signing — unlike most of this codebase's "low-tech on
 * purpose" choices (EvaluationQueue's Redis encoding, CouponLoadRunner's raw
 * docker CLI), token signing is exactly the kind of thing you don't
 * reimplement yourself.
 */
@Component
class JwtService(
    @Value("\${sysdrill.auth.jwt-secret}") secret: String,
    @Value("\${sysdrill.auth.token-ttl-days}") private val tokenTtlDays: Long,
) {
    private val algorithm = Algorithm.HMAC256(secret)
    private val verifier = JWT.require(algorithm).build()

    fun issue(userId: UUID): String =
        JWT.create()
            .withSubject(userId.toString())
            .withIssuedAt(Instant.now())
            .withExpiresAt(Instant.now().plus(tokenTtlDays, ChronoUnit.DAYS))
            .sign(algorithm)

    /** Null on any invalid/expired/tampered token — callers treat that as "not authenticated", not an error. */
    fun verify(token: String): UUID? = try {
        UUID.fromString(verifier.verify(token).subject)
    } catch (_: JWTVerificationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
