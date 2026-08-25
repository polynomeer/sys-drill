---
status: accepted
---

# Interpreted/derived values are computed at read time, never stored

Two independent parts of the backend follow the same rule: `SimulationEngine`'s `SystemState` (traffic, p95 latency, error rate, cache hit ratio, ...) is always recomputed from the small set of inputs Redis actually stores (`incidentActive`, `DesignTraits`) — never persisted itself. `SkillProfileController`'s domain-grouped weaknesses, trend direction (IMPROVING/DECLINING/STABLE), and recommended-next-domain are all computed from the flat `weaknesses`/`trend` values `SkillProfileService` writes — the write path never changed to store these interpretations directly.

In both cases, storing the derived form would risk it drifting out of sync with the raw data it's derived from (a stored "IMPROVING" trend flag could go stale the moment new scores arrive without a corresponding update; a cached `SystemState` could go stale the moment `DesignTraits` changes). Recomputing on every read costs some CPU, but at this scale that's cheaper than a class of staleness bugs. If a derived value ever becomes expensive enough to need caching, cache it explicitly with invalidation tied to its inputs — don't fold the computed form into the source-of-truth write path.
