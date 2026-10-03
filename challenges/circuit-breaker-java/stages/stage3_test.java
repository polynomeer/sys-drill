// Stage 3 — HALF_OPEN recovery after the recovery timeout.
// 학습 포인트: 언제, 어떻게 재시도를 허용할지 (timeout이 지나면 HALF_OPEN으로 넘어가고,
// 시험 호출이 성공하면 CLOSED로 복구한다).
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
        Callable<String> fail = () -> {
            throw new IllegalArgumentException("boom");
        };

        CircuitBreaker cb = new CircuitBreaker(1, 0.3);
        try {
            cb.call(fail);
        } catch (IllegalArgumentException e) {
            // expected
        }
        check(cb.state() == State.OPEN, "expected OPEN after 1 failure, got " + cb.state());

        Thread.sleep(400);
        check(cb.state() == State.HALF_OPEN, "expected HALF_OPEN after the recovery timeout elapsed, got " + cb.state());

        Object result = cb.call(() -> "recovered");
        check("recovered".equals(result), "expected \"recovered\", got " + result);
        check(cb.state() == State.CLOSED, "a successful HALF_OPEN trial should recover to CLOSED, got " + cb.state());
    }
}
