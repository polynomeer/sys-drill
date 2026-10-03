// Stage 2 — window replenishment (sliding/token-bucket-style behavior).
// 학습 포인트: 정확도·메모리 비용 (윈도우가 지나면 용량이 자연스럽게 회복되는가).
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

    static void run() throws InterruptedException {
        RateLimiter rl = new RateLimiter(2, 0.5);
        check(rl.allow("k"), "expected the first request to be allowed");
        check(rl.allow("k"), "expected the second request to be allowed");
        check(!rl.allow("k"), "third request within the window should be rejected");
        Thread.sleep(700);
        check(rl.allow("k"), "after the window elapses, capacity should replenish");
    }
}
