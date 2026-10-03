/*
 * SysDrill Build Mode — Build your own Idempotency Layer (Java)
 *
 * Implement the classes below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.concurrent.Callable;

/** Thrown by execute() when a key is reused with a different request. */
class IdempotencyConflictException extends RuntimeException {
    IdempotencyConflictException(String message) {
        super(message);
    }
}

/** Thrown by execute() when a request with the same key is still running. */
class IdempotencyInProgressException extends RuntimeException {
    IdempotencyInProgressException(String message) {
        super(message);
    }
}

/**
 * Wraps a side-effecting operation (e.g. charging a card through a payment
 * gateway) so that retries carrying the same idempotency key — a client
 * timing out and resending, a double-clicked button — perform it at most
 * once and get the original result back.
 */
public class IdempotencyLayer {
    public IdempotencyLayer() {
        this(86400.0);
    }

    public IdempotencyLayer(double ttlSeconds) {
        // TODO(stage 1): store config and set up whatever storage you need.
        // TODO(stage 4): keys are kept for ttlSeconds, then forgotten.
        throw new UnsupportedOperationException("not implemented");
    }

    public <T> T execute(String key, Object request, Callable<T> operation) throws Exception {
        // TODO(stage 1): the first call for `key` runs operation.call() and
        // stores its result; every later call with the same key returns that
        // stored result WITHOUT calling the operation again.
        // TODO(stage 2): remember the `request` each key was first used with.
        // The same key with a *different* request (compare by value with
        // equals()) is a client bug — throw IdempotencyConflictException
        // instead of replaying.
        // TODO(stage 3): while the operation for a key is still running,
        // another call with that key must neither run it nor wait — throw
        // IdempotencyInProgressException right away (the client retries
        // later). Don't hold a lock while the operation runs.
        // TODO(stage 4): if the operation throws, store nothing for the key
        // (let the exception propagate) so a retry can try again. Stored keys
        // expire ttlSeconds after they were stored.
        throw new UnsupportedOperationException("not implemented");
    }
}
