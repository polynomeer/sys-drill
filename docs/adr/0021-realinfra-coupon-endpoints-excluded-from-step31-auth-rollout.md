---
status: accepted
---

# RealInfraCouponController stays unauthenticated even after the rest of the API requires a token

[0020](0020-incremental-real-auth-rollout-guest-flow-fully-replaced.md)'s 30단계 protected only `POST /sessions`; this step (31단계) extends the same `AuthInterceptor` to every other session/submission/build-scoped endpoint — `/sessions/**`, `/submissions/**`, `/build-challenges/**`, `/build-submissions/**`, `/skill-profile` — with per-request ownership checks (`session.userId == caller`) reusing 404 rather than a new 403, to avoid disclosing whether a resource exists. `RealInfraCouponController`'s `GET /remaining`/`POST /claim` (PLAN.md 21단계) live under that same `/sessions/{sessionId}/simulation/realinfra/coupon/**` prefix but are deliberately carved out via `excludePathPatterns` and left open.

These two endpoints aren't called by the browser at all — they're the target `CouponLoadRunner`'s k6 Docker container hits directly to generate real load against a session's dedicated Postgres schema. k6 has no user identity and no way to obtain a JWT, so requiring `Authorization` here would break the real-infra coupon simulation outright, not just tighten it. The alternative (minting a service-to-service token for the k6 container) would add a second credential type for a single opt-in pilot feature — deferred until an actual security need for it appears (e.g. exposing this API outside a trusted local/demo environment, the same trigger [0003](0003-no-real-authentication-in-mvp.md) already named). A future reader adding auth to a `/sessions/**` sibling should not assume this one belongs alongside it.
