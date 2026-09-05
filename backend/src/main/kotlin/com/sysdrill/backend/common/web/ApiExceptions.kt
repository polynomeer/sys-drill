package com.sysdrill.backend.common.web

class NotFoundException(message: String) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)

class BadRequestException(message: String) : RuntimeException(message)

/** PLAN.md step 30 — wrong email/password on login, or a missing/invalid Authorization token. */
class UnauthorizedException(message: String) : RuntimeException(message)

/** PLAN.md step 35 — an authenticated caller lacking a required platform role. Distinct from [NotFoundException]: the resource guards (Session/OrganizationAccessGuard) use 404 to hide whether a specific instance exists, but a role gate on an endpoint's whole surface has nothing to hide. */
class ForbiddenException(message: String) : RuntimeException(message)
