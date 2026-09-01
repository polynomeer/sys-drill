package com.sysdrill.backend.organization

/** No EXPIRED member — expiry is derived from [OrganizationInvitation.expiresAt] at read time (ADR-0011), never persisted. */
enum class OrganizationInvitationStatus { PENDING, ACCEPTED, REVOKED }
