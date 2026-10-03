// Stage 1 — single-process fixed window.
// 학습 포인트: 경계 구간 burst 문제 (윈도우가 갓 리셋된 순간 몰리는 요청).
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
        RateLimiter rl = new RateLimiter(3, 10.0);
        int allowed = 0;
        for (int i = 0; i < 5; i++) if (rl.allow("user-a")) allowed++;
        check(allowed == 3, "expected exactly 3 allowed within the window, got " + allowed);
        check(rl.allow("user-b"), "a different key should not be affected by user-a's budget");
    }
}
