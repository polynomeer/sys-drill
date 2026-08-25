---
status: accepted
---

# A new SimulationEngine domain gets a genuinely distinct mechanism, not a relabeled copy of an existing one

docs/PRD.md describes the payment domain's incident as "외부 결제 timeout, retry storm" — in isolation, that reads almost identically to the notification domain's "provider timeout → retry storm → consumer lag" (ADR-0010). Reusing notification's shape (external dependency degrades → per-worker throughput collapses → backlog grows) with payment-flavored labels would have been the cheaper path. We didn't: payment's incident is a connection-pool bulkhead problem — a growing outbox backlog polluting a *shared* DB connection pool that order-serving queries also use, distinct from notification's pure throughput-collapse math. The three payment actions (add dispatcher workers, enable idempotent PG retry, isolate the payment connection pool) each fix a different axis of *that* mechanism, and isolating the pool alone visibly improves the user-facing error rate without shrinking the backlog itself — a lesson notification's incident doesn't teach.

Two incidents sounding similar in one-line PRD prose doesn't mean their underlying mechanism is the same; check the actual bottleneck resource and failure propagation path before reusing a shape. This is the standing rule for every future domain (docs/ROADMAP.md Phase 2 still has 예약 시스템 and 배치/정산 to add): default to a new mechanism, and only reuse an existing one when the bottleneck resource and propagation path are *actually* identical, not just described in similar words.
