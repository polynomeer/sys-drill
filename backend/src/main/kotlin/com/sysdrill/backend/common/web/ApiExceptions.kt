package com.sysdrill.backend.common.web

class NotFoundException(message: String) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)

class BadRequestException(message: String) : RuntimeException(message)

/** PLAN.md step 30 — wrong email/password on login, or a missing/invalid Authorization token. */
class UnauthorizedException(message: String) : RuntimeException(message)
