---
status: accepted
---

# Plain UUID scalar fields instead of JPA relationships across aggregate boundaries

Every cross-aggregate reference in the backend (`Session.userId`, `Submission.sessionId`, `Evaluation.submissionId`, `BuildSubmission.challengeId`, and so on) is a plain `UUID` column with no `@ManyToOne`/`@OneToMany` JPA mapping, even though normal Hibernate associations would be idiomatic within a single aggregate. We chose this deliberately: aggregates reference each other by ID only, and any cross-module lookup is an explicit repository call.

This keeps module boundaries honest (a `Session` can't accidentally cascade-save or lazily fetch a `User` object graph), sidesteps Hibernate lazy-loading/N+1 surprises across what are meant to be independent modules, and keeps each module independently testable. The cost — `sessionRepository.findById(...)` instead of `submission.session.user.email` — is intentional, not an oversight; don't "fix" a scalar UUID field into a JPA association without re-reading this.
