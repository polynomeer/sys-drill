---
status: accepted
---

# Redis job queues are only ever pushed to after the enqueuing transaction commits

Both the evaluation pipeline (`Submission` → `EvaluationQueue` → `EvaluationWorker`) and the Build pipeline (`BuildSubmission` → `BuildJobQueue` → `BuildRunnerWorker`) hand a freshly-saved row off to an async worker. The first implementation of each pushed directly to Redis inside the `@Transactional` method that saved the row — and both times this caused the same intermittent bug: the worker, in a separate DB transaction, would dequeue the job and call `findById` before the enqueuing transaction had actually committed, find nothing, and silently drop the job.

The rule now is: never call `queue.enqueue(...)` directly inside a `@Transactional` service method. Publish a Spring application event from within the transaction instead, and let a separate `@TransactionalEventListener(phase = AFTER_COMMIT)` component do the actual `enqueue()` call. This guarantees the row a worker looks up is always visible by the time the job appears in the queue.

The bug was independently rediscovered in two separate pipelines (evaluation, then Build) before this became the standing rule. This ADR exists so a third pipeline doesn't rediscover it again — any new "save a row, then hand it to a background worker" flow should follow this pattern from the start.
