// Stage 1 — basic retry.
// 학습 포인트: 실패 시 재시도, 성공하면 즉시 반환.
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
        RetryPolicy policy = new RetryPolicy(5, 0.001, d -> {});
        String result = policy.execute(() -> {
            attempts[0]++;
            if (attempts[0] < 3) throw new IllegalArgumentException("boom");
            return "success";
        });
        check("success".equals(result), "expected success, got " + result);
        check(attempts[0] == 3, "expected exactly 3 attempts (2 failures + 1 success), got " + attempts[0]);
    }
}
