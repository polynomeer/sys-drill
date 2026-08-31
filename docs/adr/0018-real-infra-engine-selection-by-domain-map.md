---
status: accepted
---

# Real-infra engine selection generalizes from a single coupon field to a domain→engine map

Step 21 hardcoded real-infra dispatch to one domain: `SimulationService.startIncident` rejected any domain other than `DOMAIN_COUPON`, and `engineFor` was a two-way ternary (`RuleBasedSimulationEngine` vs. one injected `realInfraCouponEngine` field). Step 27 adds a second real-infra domain (notification/Kafka), which this shape can't express without duplicating both checks per domain forever.

We replaced both with one `Map<String, SimulationEngine>` (`realInfraEngines`), keyed by domain, injected once in the constructor. `startIncident`'s guard becomes `domain !in realInfraEngines`, and `engineFor` becomes `realInfraEngines.getValue(state.domain)` — the map's key set IS now the single source of truth for "which domains support real-infra mode," rather than that fact being implicit in a chain of `when`/`if` branches that would otherwise grow by one clause per future domain.

The alternative — keep extending the ternary/`when` into a longer chain per domain — is a smaller diff for exactly one more domain, but was rejected because it repeats the same two-line special-case at every future real-infra domain forever, with no single place that answers "which domains are real-infra-eligible right now." The map answers that in one expression and keeps `startIncident`/`engineFor` unchanged in shape as a third, fourth, ... domain is added — only the map literal grows.
