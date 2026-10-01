---
status: accepted
---

# JWT secret fail-fast is keyed on a Dockerfile-set deployment-mode flag, not a Spring profile

`sysdrill.auth.jwt-secret`'s insecure default (`dev-only-insecure-secret-change-me`) is deliberately fine for `bootRun` and every `@SpringBootTest` (ADR-0003's trust boundary) — but nothing stopped a real deployment from silently inheriting it too. The natural fix is "fail fast if this is production and the secret is still the default," but this repo has no Spring profile system at all (no `@Profile`, no `spring.profiles.active` set anywhere) — introducing one just for this check would be a bigger change than the problem warrants, and a profile still has to be remembered and set correctly by whoever deploys, the same forgettable step as the secret itself.

Decided to key the check on something this repo already does unconditionally: `Dockerfile` is the only artifact that represents a real deployment today, so it now sets `SYSDRILL_DEPLOYMENT_MODE=container`, and `JwtSecretStartupCheck` refuses to start only when that flag is present *and* the secret is still the default. `bootRun` and tests never set it, so they're untouched.

If this repo later gains a real profile system (or a different deployment target that doesn't go through `Dockerfile`), this check needs to move to whatever new signal replaces it — don't assume `SYSDRILL_DEPLOYMENT_MODE` keeps meaning "production" forever.
