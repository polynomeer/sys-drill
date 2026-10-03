// Stage 2 — trip to OPEN once the failure threshold is reached.
// 학습 포인트: fail fast — OPEN 상태에서는 실제 함수를 호출하지 않음.
import java.util.concurrent.Callable;

class RunTest {
    public static void main(String[] args) {
        try {
            run();
            System.out.println("RESULT:PASS");
        } catch (AssertionError e) {
            System.out.println("RESULT:FAIL:" + e.getMessage());
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("RESULT:FAIL:not implemented");
            System.exit(1);
        } catch (Throwable e) {
            System.out.println("RESULT:FAIL:unexpected error: " + e);
            System.exit(1);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run() throws Exception {
        int[] calls = {0};
        Callable<String> flaky = () -> {
            calls[0]++;
            throw new IllegalArgumentException("boom");
        };

        CircuitBreaker cb = new CircuitBreaker(3, 10.0);
        for (int i = 0; i < 3; i++) {
            try {
                cb.call(flaky);
            } catch (IllegalArgumentException e) {
                // the wrapped function's own failure — expected
            }
        }
        check(cb.state() == State.OPEN, "expected OPEN after 3 failures, got " + cb.state());
        check(calls[0] == 3, "expected the function to run 3 times, got " + calls[0]);

        try {
            cb.call(flaky);
            check(false, "expected CircuitOpenException while OPEN");
        } catch (CircuitOpenException e) {
            // fail fast — expected
        }
        check(calls[0] == 3, "the underlying function must not run while the circuit is OPEN (fail fast)");
    }
}
