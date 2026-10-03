// Stage 4 — invalidation + hit ratio.
// 학습 포인트: 원본이 바뀌면 캐시를 지워야 하고, hit ratio를 봐야 TTL·용량을 튜닝할 수 있다.
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
        Cache c = new Cache(10);
        c.set("p1", "price=100", 60);
        check("price=100".equals(c.get("p1")), "expected price=100"); // hit
        c.invalidate("p1"); // the price changed in the DB
        check(c.get("p1") == null, "an invalidated key should miss"); // miss
        check(c.get("p2") == null, "p2 was never set and should miss"); // miss
        c.set("p1", "price=120", 60);
        check("price=120".equals(c.get("p1")), "after invalidation, the new value should be served"); // hit
        c.invalidate("never-set"); // must not throw

        Stats s = c.stats();
        check(s.hits() == 2, "expected 2 hits, got " + s.hits());
        check(s.misses() == 2, "expected 2 misses, got " + s.misses());
        check(Math.abs(s.hitRatio() - 0.5) < 0.01, "expected hitRatio 0.5, got " + s.hitRatio());
    }
}
