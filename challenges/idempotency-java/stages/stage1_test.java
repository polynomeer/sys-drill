// Stage 1 — replay the stored result.
// 학습 포인트: 같은 멱등성 키로 다시 온 요청은 결제를 다시 하지 않고 처음 결과를 돌려준다.
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
        Callable<Map<String, Object>> charge = () -> {
            charges.add(1000);
            return Map.of("charge_id", "ch_" + charges.size(), "amount", 1000);
        };

        Map<String, Object> first = layer.execute("order-1", Map.of("amount", 1000), charge);
        Map<String, Object> retry = layer.execute("order-1", Map.of("amount", 1000), charge);
        check(charges.size() == 1, "a retry with the same key must not charge again, got " + charges.size() + " charges");
        check(first.equals(retry), "the retry should get the original result " + first + ", got " + retry);

        Map<String, Object> other = layer.execute("order-2", Map.of("amount", 1000), charge);
        check(charges.size() == 2, "a different key is a different request and should charge");
        check("ch_2".equals(other.get("charge_id")), "expected ch_2 for the new key, got " + other);
    }
}
