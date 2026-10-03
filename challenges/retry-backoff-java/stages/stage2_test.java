// Stage 2 — retries exhausted.
// 학습 포인트: maxAttempts를 넘기면 RetryExhaustedException, 그 이상 시도하지 않음.
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

    static void run() {
        int[] attempts = {0};
        RetryPolicy policy = new RetryPolicy(4, 0.001, d -> {});
        try {
            policy.execute(() -> {
                attempts[0]++;
                throw new IllegalArgumentException("boom");
            });
            check(false, "expected RetryExhaustedException once maxAttempts is exceeded");
        } catch (RetryExhaustedException e) {
            // expected
        }
        check(attempts[0] == 4, "expected exactly 4 attempts (maxAttempts), got " + attempts[0]);
    }
}
