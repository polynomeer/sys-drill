---
status: accepted
---

# Scenario and Build challenge content ships as Flyway seed migrations, not admin CRUD

All three scenarios (coupon, notification, product-browsing) and both Build challenges (Rate Limiter, Queue) exist as Flyway migrations (`V2`, `V9`, `V11`, `V12`, `V13`) with fixed UUIDs, not through an admin content-management API. We deferred building `POST /admin/scenarios`-style CRUD; new content is added by writing a new seed migration.

With a handful of scenarios written by the same person building the platform, a migration is strictly less work than an admin UI and gets the same reproducibility (fixed UUIDs, identical across every environment) for free. This is a deliberate MVP scope cut, not an oversight — revisit once non-engineer content authors need to add scenarios without a code deploy.
