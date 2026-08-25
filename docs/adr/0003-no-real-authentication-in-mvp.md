---
status: accepted — revisit before wider release
---

# MVP ships with a nickname-only guest profile, not real authentication

`POST /users` creates a `User` row with a nickname and no password; there is no login, session token, or credential check anywhere in the API. Every client request carries a bare `userId` that the frontend already has in `localStorage`. Real authentication (password/session-based login) is out of scope for the MVP.

This was flagged as an open gap starting when sessions were first built and left unresolved through the MVP-completion self-check — a deliberate scope cut to prioritize the core Build/Design/Wargame loop, not an oversight. It's a hard boundary, not a cosmetic one: nothing prevents one browser's `localStorage` `userId` from acting as any other user, and there's no cross-device continuation. Revisit before any multi-device feature, account recovery, or exposing the API outside a trusted local/demo environment.
