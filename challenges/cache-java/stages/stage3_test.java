// Stage 3 — single-flight (cache stampede).
// 학습 포인트: hot key가 만료된 순간 동시에 몰린 miss가 전부 DB로 가면 DB가 무너진다 — 같은 키의 로드는 한 번만.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
        Cache c = new Cache(10);
        AtomicInteger loads = new AtomicInteger();
        Supplier<Object> slowLoader = () -> {
            loads.incrementAndGet();
            try {
                Thread.sleep(200); // a slow DB query
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "product-42";
        };

        List<Object> results = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch startGate = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            threads.add(new Thread(() -> {
                try {
                    startGate.await();
                    results.add(c.getOrLoad("hot", slowLoader, 5.0));
                } catch (Throwable e) { // a crash in a worker thread would otherwise vanish silently
                    failure.compareAndSet(null, e);
                }
            }));
        }
        for (Thread t : threads) t.start();
        startGate.countDown();
        for (Thread t : threads) t.join();
        if (failure.get() != null) throw failure.get();

        check(loads.get() == 1, "8 concurrent misses on the same key should trigger exactly 1 load, got " + loads.get());
        check(results.equals(Collections.nCopies(8, "product-42")), "every caller should get the loaded value, got " + results);
        check("product-42".equals(c.getOrLoad("hot", slowLoader, 5.0)), "expected product-42 from the cache");
        check(loads.get() == 1, "once loaded, the value should come from the cache, not the loader");
    }
}
