// Stage 5 — fail-open vs fail-closed.
// 학습 포인트: 가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는
// 설계 선택이지 정답이 없다 — 여기서는 두 모드 모두 올바르게 구현하는지 본다).
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
        RateLimiter openLimiter = new RateLimiter(1, 1.0, new FaultyStore(), FailMode.OPEN);
        check(openLimiter.allow("k"), "failMode=OPEN should admit requests when the store is unavailable");

        RateLimiter closedLimiter = new RateLimiter(1, 1.0, new FaultyStore(), FailMode.CLOSED);
        check(!closedLimiter.allow("k"), "failMode=CLOSED should reject requests when the store is unavailable");
    }
}
