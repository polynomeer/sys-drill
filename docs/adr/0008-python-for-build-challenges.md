---
status: accepted
---

# Build Mode challenges run in Python, independent of the backend's Kotlin/JVM stack

The backend is Kotlin on the JVM, but Build Mode challenge submissions and their grading scripts (Rate Limiter, Queue) are Python, run in a `python:3.12-slim` sandbox image regardless of the backend's own language. `sysdrill.build.sandbox-image` is a per-challenge config value, not hardcoded, so a future challenge can use a different image without changing the sandbox execution model.

The challenges test design/concurrency/failure-handling understanding, not JVM-specific skills, so the grading language didn't need to match the backend's. Python containers start faster than JVM ones, which matters when every grading run is a fresh `docker run`, and its standard library covers the concurrency/networking primitives each stage's test needs (`threading`, `socket`) without extra dependencies — keeping grading scripts short and dependency-free.

이후 TypeScript(V44)와 Java·Kotlin·Go(V72) 판이 rate-limiter에 추가됐다 — 언어별 판은 별도 챌린지로 두고, 채점 하니스도 그 언어로 새로 쓴다. 컴파일 언어의 이미지·자원 한도는 [ADR-0051](0051-compiled-language-sandboxes-get-own-images-and-limits.md).
