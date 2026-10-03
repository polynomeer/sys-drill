// Stage 4 — failures and key expiry.
// 학습 포인트: 실패한 요청을 저장하면 재시도가 영원히 실패를 재생한다. 키도 영원히 보관할 수 없다(보존 기간).
import java.net.ConnectException;
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
        IdempotencyLayer layer = new IdempotencyLayer(0.3);
        List<Integer> attempts = new ArrayList<>();
        Callable<String> flakyCharge = () -> {
            attempts.add(1);
            if (attempts.size() == 1) throw new ConnectException("gateway timeout");
            return "ch_1";
        };

        try {
            layer.execute("order-1", Map.of("amount", 1000), flakyCharge);
            check(false, "the operation's own error should propagate to the caller");
        } catch (ConnectException e) {
            // expected
        }
        String result = layer.execute("order-1", Map.of("amount", 1000), flakyCharge);
        check("ch_1".equals(result), "a retry after a failure should run the operation again, got " + result);
        check(attempts.size() == 2, "expected 2 attempts (the failure is not stored), got " + attempts.size());

        Thread.sleep(400);
        String again = layer.execute("order-1", Map.of("amount", 5000), () -> "ch_new");
        check("ch_new".equals(again), "after the ttl the key is forgotten and may be reused, got " + again);
    }
}
