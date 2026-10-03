// Stage 4 — shared ("distributed") store.
// 학습 포인트: 네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다
// capacity가 따로 놀아서, 총 허용량이 의도한 것보다 훨씬 커진다).
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
        InMemoryStore sharedStore = new InMemoryStore();
        RateLimiter instanceA = new RateLimiter(5, 5.0, sharedStore);
        RateLimiter instanceB = new RateLimiter(5, 5.0, sharedStore);
        int totalAllowed = 0;
        for (int i = 0; i < 10; i++) {
            RateLimiter limiter = i % 2 == 0 ? instanceA : instanceB;
            if (limiter.allow("shared-key")) totalAllowed++;
        }
        check(totalAllowed == 5, "two instances sharing a store should still cap at 5 total, got " + totalAllowed);
    }
}
