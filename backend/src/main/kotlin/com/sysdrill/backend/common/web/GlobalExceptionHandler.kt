package com.sysdrill.backend.common.web

import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

data class ApiError(val status: Int, val message: String?)

/**
 * Extends [ResponseEntityExceptionHandler] (docs/COMMERCIALIZATION.md) so
 * the ~20 standard Spring MVC exceptions it already maps correctly (a
 * malformed JSON body, a failed `@Valid`, an unsupported HTTP method, ...)
 * keep their proper 4xx status -- only [handleExceptionInternal] below is
 * overridden, to reshape their response body into [ApiError] instead of
 * Spring's default. A first attempt at this file added a blanket
 * `@ExceptionHandler(Exception::class)` directly (no superclass), which
 * *also* intercepted `MethodArgumentNotValidException` ahead of Spring's
 * own resolver and silently turned validation 400s into 500s -- caught by
 * PasswordResetAndVerificationIntegrationTest failing during this round.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError(HttpStatus.NOT_FOUND.value(), ex.message))

    @ExceptionHandler(ConflictException::class, IllegalStateException::class)
    fun handleConflict(ex: RuntimeException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError(HttpStatus.CONFLICT.value(), ex.message))

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError(HttpStatus.BAD_REQUEST.value(), ex.message))

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError(HttpStatus.UNAUTHORIZED.value(), ex.message))

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError(HttpStatus.FORBIDDEN.value(), ex.message))

    @ExceptionHandler(TooManyRequestsException::class)
    fun handleTooManyRequests(ex: TooManyRequestsException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError(HttpStatus.TOO_MANY_REQUESTS.value(), ex.message))

    // docs/COMMERCIALIZATION.md -- the true last resort: DB constraint
    // violations, NPEs, and anything else not covered above or by
    // ResponseEntityExceptionHandler's own mappings. Without this, these
    // fell through to Spring Boot's opaque default /error response with
    // nothing logged by this app -- exactly what happened when a stale JWT
    // (user deleted from the DB after the token was issued) reached a
    // service layer and hit a FK-constraint violation.
    //
    // Sentry.captureException is called explicitly here rather than relying
    // on Sentry's own auto-configured HandlerExceptionResolver: that
    // resolver only ever sees exceptions nothing else has resolved, and this
    // @RestControllerAdvice's ExceptionHandlerExceptionResolver always
    // resolves first -- Sentry's resolver would never run for anything this
    // class handles. A no-op when sentry.dsn is unset (SDK's own behavior).
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiError> {
        log.error("Unhandled exception", ex)
        Sentry.captureException(ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."))
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> =
        ResponseEntity.status(statusCode).headers(headers).body(ApiError(statusCode.value(), ex.message))
}
