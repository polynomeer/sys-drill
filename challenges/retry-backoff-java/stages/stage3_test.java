// Stage 3 — exponential backoff + jitter.
// 학습 포인트: 지수적으로 커지는 대기 시간과 thundering herd를 막는 지터.
import java.util.ArrayList;
import java.util.List;

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

    /** One full run of a policy that always fails: the 5 delays it asked to sleep between 6 attempts. */
    static List<Double> recordDelays() {
        List<Double> recordedDelays = new ArrayList<>();
        RetryPolicy policy = new RetryPolicy(6, 0.01, 10.0, d -> recordedDelays.add(d));
        try {
            policy.execute(() -> {
                throw new IllegalArgumentException("boom");
            });
        } catch (RetryExhaustedException e) {
            // expected
        }
        return recordedDelays;
    }

    static void run() {
        List<Double> first = recordDelays();
        List<Double> second = recordDelays();
        for (List<Double> recordedDelays : List.of(first, second)) {
            check(recordedDelays.size() == 5, "expected 5 delays between 6 attempts, got " + recordedDelays.size());
            for (int i = 0; i < recordedDelays.size(); i++) {
                double d = recordedDelays.get(i);
                double cap = Math.min(10.0, 0.01 * Math.pow(2, i));
                check(0 <= d && d <= cap, "delay " + i + " = " + d + " should be within [0, " + cap + "] (exponential backoff cap)");
            }
        }
        // Plain exponential delays (10ms, 20ms, 40ms, …) already all differ from each other, so
        // "they vary" proves nothing — jitter means two runs don't wait the same amounts.
        check(!first.equals(second), "jitter should randomize the delays, but two runs waited exactly the same: " + first);
    }
}
