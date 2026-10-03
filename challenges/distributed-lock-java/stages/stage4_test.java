// Stage 4 — concurrency.
// 학습 포인트: 여러 요청이 동시에 acquire를 시도해도 정확히 하나만 성공.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
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
        LockStore store = new LockStore();
        AtomicInteger successes = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch startGate = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String ownerId = "owner-" + i;
            threads.add(new Thread(() -> {
                try {
                    DistributedLock lock = new DistributedLock("resource-1", store, 5.0);
                    startGate.await();
                    if (lock.acquire(ownerId) != null) successes.incrementAndGet();
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }));
        }
        for (Thread t : threads) t.start();
        startGate.countDown();
        for (Thread t : threads) t.join();
        if (failure.get() != null) throw failure.get();
        check(successes.get() == 1,
                "expected exactly 1 successful acquire among 20 concurrent attempts, got " + successes.get());
    }
}
