// Stage 2 — LRU eviction.
// 학습 포인트: 메모리는 유한하다 — 가득 차면 가장 오래 안 쓴 항목부터 내보낸다.
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
        Cache c = new Cache(3);
        c.set("a", 1, 60);
        c.set("b", 2, 60);
        c.set("c", 3, 60);
        check(Integer.valueOf(1).equals(c.get("a")), "a should still be cached (capacity is 3)"); // a is now the most recently used
        c.set("d", 4, 60); // over capacity: b is the least recently used
        check(c.get("b") == null, "b was the least recently used key and should have been evicted");
        check(Integer.valueOf(1).equals(c.get("a")), "a was read just before the insert, so it must survive");
        check(Integer.valueOf(3).equals(c.get("c")), "c should survive — only one key needed to go");
        check(Integer.valueOf(4).equals(c.get("d")), "the newly inserted key d should be cached");
    }
}
