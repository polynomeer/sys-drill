// Stage 4 — re-trip when the HALF_OPEN trial fails.
// 학습 포인트: 복구 판단이 틀렸을 때의 대응 (시험 호출이 실패하면 다시 OPEN으로 가고
// recovery timeout을 지금부터 다시 센다).
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
        Thread.sleep(400);
        check(cb.state() == State.HALF_OPEN, "expected HALF_OPEN after the recovery timeout elapsed, got " + cb.state());

        try {
            cb.call(fail);
        } catch (IllegalArgumentException e) {
            // the trial call's own failure — expected
        }
        check(cb.state() == State.OPEN, "a failed HALF_OPEN trial should return to OPEN, got " + cb.state());

        try {
            cb.call(() -> "should not run");
            check(false, "expected CircuitOpenException immediately after a failed trial (timeout must reset)");
        } catch (CircuitOpenException e) {
            // fail fast — expected
        }
    }
}
