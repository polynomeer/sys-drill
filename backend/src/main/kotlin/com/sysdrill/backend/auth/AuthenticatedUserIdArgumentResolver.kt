package com.sysdrill.backend.auth

import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

/** PLAN.md step 30 — fills a `@AuthenticatedUserId userId: UUID` controller parameter from what [AuthInterceptor] verified, so controllers never see a client-supplied userId for the paths this is wired onto. */
@Component
class AuthenticatedUserIdArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(AuthenticatedUserId::class.java) && parameter.parameterType == UUID::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        // AuthInterceptor already rejected the request with 401 before this
        // method could ever run if this attribute weren't set — a missing
        // attribute here means the interceptor isn't wired onto this path,
        // which is a wiring bug, not a normal "unauthenticated" case.
        return webRequest.getAttribute(AUTHENTICATED_USER_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) as? UUID
            ?: error("@AuthenticatedUserId used on a path AuthInterceptor isn't registered for")
    }
}
