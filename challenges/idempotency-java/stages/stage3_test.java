// Stage 3 — a duplicate arrives while the first is still running.
// 학습 포인트: 결과가 저장되기 전(처리 중)에 온 중복 요청도 막아야 한다 — 여기서 이중 결제가 가장 많이 난다.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    static void run() throws Throwable {
        IdempotencyLayer layer = new IdempotencyLayer();
        List<String> charges = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Callable<String> slowCharge = () -> { // the payment gateway takes a while to answer
            charges.add("slow");
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "ch_1";
        };
        Callable<String> fastCharge = () -> {
            charges.add("fast");
            return "ch_dup";
        };

        List<Object> firstResult = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try {
                firstResult.add(layer.execute("order-1", Map.of("amount", 1000), slowCharge));
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
                started.countDown(); // don't make the main thread wait for a start that won't come
            }
        });
        first.start();
        check(started.await(5, TimeUnit.SECONDS), "the first request never started running");
        if (failure.get() != null) throw failure.get();

        List<Object> outcome = Collections.synchronizedList(new ArrayList<>());
        Thread dup = new Thread(() -> {
            try {
                outcome.add(layer.execute("order-1", Map.of("amount", 1000), fastCharge));
            } catch (IdempotencyInProgressException e) {
                outcome.add("in-progress");
            } catch (Exception e) {
                outcome.add(e);
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            }
        });
        dup.start();
        dup.join(2000);
        boolean blocked = dup.isAlive();
        release.countDown();
        first.join(5000);
        dup.join(5000);
        if (failure.get() != null) throw failure.get();

        check(!blocked, "the duplicate request blocked while the first was running — don't hold a lock while the operation runs; reject it instead");
        check(charges.equals(List.of("slow")), "a duplicate arriving mid-flight must not charge again, charges were " + charges);
        check(outcome.equals(List.of("in-progress")), "the duplicate should get IdempotencyInProgressException, got " + outcome);
        check(firstResult.equals(List.of("ch_1")), "the first request should still finish normally, got " + firstResult);
        check("ch_1".equals(layer.execute("order-1", Map.of("amount", 1000), fastCharge)), "once finished, the key should replay ch_1");
    }
}
