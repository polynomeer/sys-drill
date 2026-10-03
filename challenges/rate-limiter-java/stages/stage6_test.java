// Stage 6 — operational metrics.
// 학습 포인트: reject rate, latency, key skew 같은 운영 지표가 있어야 실제로
// 튜닝하고 대응할 수 있다.
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
        RateLimiter rl = new RateLimiter(2, 5.0);
        rl.allow("k");
        rl.allow("k");
        rl.allow("k");
        Metrics m = rl.metrics();
        check(m.allowed() == 2, "expected 2 allowed, got " + m.allowed());
        check(m.rejected() == 1, "expected 1 rejected, got " + m.rejected());
        check(Math.abs(m.rejectRate() - 1.0 / 3) < 0.01, "expected rejectRate ~0.333, got " + m.rejectRate());
    }
}
