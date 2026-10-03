// Stage 2 — same key, different request.
// 학습 포인트: 키를 재사용했는데 요청 내용이 다르면 재생이 아니라 클라이언트 버그다 — 조용히 옛 결과를 주면 안 된다.
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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

    static void run() throws Exception {
        IdempotencyLayer layer = new IdempotencyLayer();
        List<Integer> charges = new ArrayList<>();
        Callable<String> charge = () -> {
            charges.add(1);
            return "ch_" + charges.size();
        };

        layer.execute("order-1", Map.of("amount", 1000, "currency", "KRW"), charge);
        // a new map with the same contents is the same request
        String replay = layer.execute("order-1", Map.of("amount", 1000, "currency", "KRW"), charge);
        check("ch_1".equals(replay), "an equal request should be replayed, got " + replay);

        try {
            layer.execute("order-1", Map.of("amount", 2000, "currency", "KRW"), charge);
            check(false, "reusing a key with a different request should throw IdempotencyConflictException");
        } catch (IdempotencyConflictException e) {
            // expected
        }
        check(charges.size() == 1, "a conflicting request must not charge, got " + charges.size() + " charges");
    }
}
