---
status: accepted
---

# Real auth rollout protects only `POST /sessions` for now, and fully replaces the guest flow with no account-linking path

[0003](0003-no-real-authentication-in-mvp.md) flagged real authentication as a deliberate MVP scope cut, to be revisited before any multi-device feature or exposure outside a trusted local/demo environment — Phase 4 (Team/B2B) is exactly that trigger, since organizations require client-supplied `userId` to no longer be trusted. This step introduces JWT + BCrypt auth (`POST /auth/signup`, `POST /auth/login`) but deliberately protects only the single most sensitive write endpoint, `POST /sessions`, via a `HandlerInterceptor` scoped to that one path pattern. The other 15+ endpoints that read a client-supplied `userId` (`GET /users/{userId}/sessions`, skill-profile, build submissions, etc.) are left unmigrated on purpose, so the same fake-`userId` trust boundary ADR-0003 already accepted continues to hold there until PLAN.md 31단계 migrates them one by one — going endpoint-by-endpoint keeps each change small and independently testable rather than one large, riskier cutover.

Separately, the old nickname-only guest endpoint (`POST /users`) is removed outright rather than kept alongside signup with an account-linking/migration path. Since no real production users exist yet, building a linking mechanism would be speculative complexity with nothing to migrate — if real guest data existed at rollout time, this trade-off would need revisiting.
