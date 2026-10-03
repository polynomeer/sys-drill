// Stage 4 — concurrency.
// 학습 포인트: 한 구독자에 대해 여러 스레드가 동시에 poll해도 중복/유실 없음.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
        EventBus bus = new EventBus();
        String sub = bus.subscribe("orders");
        for (int i = 0; i < 1000; i++) bus.publish("orders", i);

        List<Object> received = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            threads.add(new Thread(() -> {
                try {
                    start.await();
                    while (true) {
                        Event msg = bus.poll(sub);
                        if (msg == null) break;
                        received.add(msg.payload());
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }));
        }
        for (Thread t : threads) t.start();
        start.countDown();
        for (Thread t : threads) t.join();
        if (failure.get() != null) throw failure.get();

        check(received.size() == 1000, "expected 1000 deliveries, got " + received.size());
        List<Integer> sorted = new ArrayList<>();
        for (Object p : received) sorted.add((Integer) p);
        Collections.sort(sorted);
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 1000; i++) expected.add(i);
        check(sorted.equals(expected), "each event should be delivered exactly once across concurrent pollers");
    }
}
