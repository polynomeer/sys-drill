// Stage 3 — concurrency safety.
// 학습 포인트: atomicity (여러 스레드가 동시에 호출해도 capacity를 넘기면 안 된다).
// 제공된 InMemoryStore.incr()는 읽기와 쓰기 사이에 네트워크 왕복을 흉내 낸
// 지연이 있어 원자적이지 않다 — allow()에 동시성 제어가 없으면 capacity를 넘긴다.
import java.util.ArrayList;
import java.util.List;
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
        RateLimiter rl = new RateLimiter(50, 5.0);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 10; t++) {
            threads.add(new Thread(() -> {
                try {
                    for (int i = 0; i < 20; i++) rl.allow("shared-key");
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }));
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        if (failure.get() != null) throw failure.get();
        long allowed = rl.metrics().allowed();
        check(allowed <= 50, "concurrent access let " + allowed + " requests through, expected <= 50");
    }
}
