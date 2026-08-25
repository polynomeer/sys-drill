---
status: accepted
---

# Build Mode challenges run in Python, independent of the backend's Kotlin/JVM stack

The backend is Kotlin on the JVM, but Build Mode challenge submissions and their grading scripts (Rate Limiter, Queue) are Python, run in a `python:3.12-slim` sandbox image regardless of the backend's own language. `sysdrill.build.sandbox-image` is a per-challenge config value, not hardcoded, so a future challenge can use a different image without changing the sandbox execution model.

The challenges test design/concurrency/failure-handling understanding, not JVM-specific skills, so the grading language didn't need to match the backend's. Python containers start faster than JVM ones, which matters when every grading run is a fresh `docker run`, and its standard library covers the concurrency/networking primitives each stage's test needs (`threading`, `socket`) without extra dependencies — keeping grading scripts short and dependency-free.
