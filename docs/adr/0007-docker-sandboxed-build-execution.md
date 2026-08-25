---
status: accepted
---

# Build Mode grades submissions inside network-isolated, resource-capped Docker containers

Build Mode runs untrusted user-submitted code (rate limiter and queue implementations) against grading scripts, which needs real isolation, not just a subprocess call. Each (submission, stage) pair runs as its own container: `docker run --rm --network none --cpus 0.5 --memory 128m --pids-limit 64`, one container per grading run.

`--network none` prevents submitted code from exfiltrating data or calling out; the CPU/memory/PID caps bound the blast radius of a runaway or fork-bombing submission; `--rm` plus a fresh container per run means no state leaks between submissions or stages. One container per (submission, stage) trades container-startup latency for simplicity and complete isolation between stages — acceptable at MVP scale. Heavier isolation (gVisor/Firecracker, per docs/ARCHITECTURE.md's infra table) is the upgrade path if security requirements increase; the container-per-stage shape doesn't need to change for that.
