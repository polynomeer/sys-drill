// Stage 1 — TTL get/set.
// 학습 포인트: 캐시 값은 영원하지 않다 — TTL이 지나면 원본을 다시 읽어야 한다.
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
        Cache c = new Cache(10);
        check(c.get("missing") == null, "a key that was never set should be a miss (null)");
        c.set("p1", "price=100", 0.3);
        Object p1 = c.get("p1");
        check("price=100".equals(p1), "expected price=100 before the TTL, got " + p1);
        c.set("p2", "price=200", 5.0);
        Thread.sleep(400);
        check(c.get("p1") == null, "p1 should have expired after its 0.3s TTL");
        check("price=200".equals(c.get("p2")), "p2 has a 5s TTL and should still be cached");
    }
}
