---
status: accepted
---

# Notification's real-infra pilot drives Kafka with in-process clients, not an external load-gen container

PLAN.md step 27 adds a second real-infra domain (notification/Kafka) mirroring step 21's coupon/Postgres pilot's shape (ADR-0013: topic-per-session instead of broker-per-session, same reasoning as schema-per-session). The coupon pilot's load generation, though, shells out to a separate `grafana/k6` Docker container via `ProcessBuilder` (`CouponLoadRunner`) because the load itself is HTTP traffic against the app's own REST endpoints — k6 is a genuine load-testing tool for that job.

For Kafka, "the load" IS calling a client library — producing and consuming messages is exactly what `kafka-clients`' `KafkaProducer`/`KafkaConsumer` are for, with no HTTP layer in between. `NotificationLoadRunner` runs real producer/consumer threads directly inside the Spring Boot process instead of shelling out to an external Kafka load-gen tool (e.g. `kafka-producer-perf-test.sh`, or an xk6-kafka container). This avoids a second process-orchestration mechanism for a real dependency that's already a JVM-native client, and lets `DesignTraits.consumerCount` map directly onto real consumer thread counts within one process instead of coordinating a separate container's concurrency.

The trade-off: this couples load generation into the app's own JVM (a stalled consumer thread is a stalled app thread, unlike k6's fully isolated container), and it means the notification pilot's "real load" isn't independently reusable as a load-testing artifact the way a k6 script is. Accepted for this pilot's scope — the coupon pilot's container-based pattern remains the template for any future real-infra domain whose load is HTTP-shaped; a domain whose load is itself a client-library call should keep following this in-process pattern instead.
