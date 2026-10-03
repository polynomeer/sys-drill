/*
 * SysDrill Build Mode — Build your own Circuit Breaker (Java)
 *
 * Implement `CircuitBreaker` below across 4 stages (see README.md).
 * Keep the class and method names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 javac로 컴파일합니다(Java 25,
 * 표준 라이브러리만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

import java.util.concurrent.Callable;

enum State { CLOSED, OPEN, HALF_OPEN }

/** Thrown by call() when the breaker is OPEN — the wrapped function must not run. */
class CircuitOpenException extends RuntimeException {
    CircuitOpenException(String message) {
        super(message);
    }
}

/**
 * Wraps calls to a possibly-failing function (e.g. an external API) and
 * stops calling it once it's clearly broken, instead of letting every
 * caller wait out its own timeout.
 */
public class CircuitBreaker {

    public CircuitBreaker() {
        this(3, 5.0);
    }

    public CircuitBreaker(int failureThreshold, double recoveryTimeoutSeconds) {
        // TODO(stage 1): store config and start CLOSED.
        throw new UnsupportedOperationException("not implemented");
    }

    public State state() {
        // TODO(stage 1): CLOSED | OPEN | HALF_OPEN.
        // TODO(stage 3): once OPEN and recoveryTimeoutSeconds has elapsed since the
        // trip, reading state should report HALF_OPEN (a real trial call
        // hasn't necessarily happened yet — this is a state *transition*,
        // not just a label).
        throw new UnsupportedOperationException("not implemented");
    }

    public <T> T call(Callable<T> fn) throws Exception {
        // TODO(stage 1): while CLOSED, call fn and return its result.
        // TODO(stage 2): count consecutive failures; once failureThreshold is
        // reached, trip to OPEN. While OPEN, throw CircuitOpenException immediately
        // WITHOUT calling fn — that's the whole point (fail fast).
        // TODO(stage 3): once state has moved to HALF_OPEN (see state()),
        // the next call() is a *trial*: run fn for real, and if it
        // succeeds, recover to CLOSED (reset the failure count too).
        // TODO(stage 4): if the HALF_OPEN trial call fails, go back to OPEN
        // and restart the recoveryTimeoutSeconds countdown from now.
        throw new UnsupportedOperationException("not implemented");
    }
}
