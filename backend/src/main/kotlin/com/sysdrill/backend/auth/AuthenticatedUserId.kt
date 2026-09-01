package com.sysdrill.backend.auth

/** PLAN.md step 30 — marks a controller method `UUID` parameter to be resolved from the verified `Authorization: Bearer` token, not trusted client input. See [AuthInterceptor]/[AuthenticatedUserIdArgumentResolver]. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthenticatedUserId
