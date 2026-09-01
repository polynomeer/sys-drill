---
status: accepted
---

# Organization invitation tokens are opaque DB-stored codes, not signed JWTs

`OrganizationInvitation.token` is a plain `UUID.randomUUID().toString()`, stored in the `organization_invitations` table and looked up directly by equality — not a signed JWT like [JwtService](../../backend/src/main/kotlin/com/sysdrill/backend/auth/JwtService.kt) issues for login sessions.

`JwtService`'s signing exists because a login token must be verified statelessly on every request without a database round-trip. An invitation token has no such requirement: every redemption path (`GET /organizations/invitations/{token}`, `POST /organizations/invitations/{token}/accept`) already does a DB lookup to fetch the invitation's organization, role, and status, so a signature would verify nothing a lookup doesn't already confirm — it would just be complexity with no payoff. This mirrors `SessionService.startSession`'s existing `seed = UUID.randomUUID().toString()` idiom (an unguessable string with no verification needs beyond a database row existing) rather than inventing a second, JWT-based token scheme for a fundamentally different kind of token.
