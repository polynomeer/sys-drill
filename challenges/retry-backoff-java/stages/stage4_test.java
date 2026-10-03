// Stage 4 — retry budget.
// 학습 포인트: 여러 요청이 공유하는 재시도 예산으로 재시도 폭풍 억제.
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
        RetryBudget budget = new RetryBudget(2);

        int[] attemptsP1 = {0};
        RetryPolicy policy1 = new RetryPolicy(10, 0.001, budget, d -> {});
        try {
            policy1.execute(() -> {
                attemptsP1[0]++;
                throw new IllegalArgumentException("boom");
            });
        } catch (RetryExhaustedException e) {
            // expected
        }
        check(attemptsP1[0] < 10, "a shared retry budget should cut retries short before maxAttempts is reached");

        int[] attemptsP2 = {0};
        RetryPolicy policy2 = new RetryPolicy(10, 0.001, budget, d -> {});
        try {
            policy2.execute(() -> {
                attemptsP2[0]++;
                throw new IllegalArgumentException("boom");
            });
        } catch (RetryExhaustedException e) {
            // expected
        }
        check(attemptsP2[0] <= 1, "the budget should already be exhausted by policy1, so policy2 should not retry at all");
    }
}
