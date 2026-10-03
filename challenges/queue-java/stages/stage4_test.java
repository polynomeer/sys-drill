// Stage 4 — concurrency safety.
// 학습 포인트: 두 컨슈머가 같은 메시지를 동시에 받지 않음 (여러 스레드가
// 동시에 dequeue()해도 각 메시지는 정확히 한 번만 전달돼야 한다).
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
        Queue q = new Queue(5.0, 3);
        for (int i = 0; i < 20; i++) q.enqueue(i);

        List<Integer> received = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch startGate = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 5; t++) {
            threads.add(new Thread(() -> {
                try {
                    startGate.await();
                    while (true) {
                        Message msg = q.dequeue();
                        if (msg == null) break;
                        received.add((Integer) msg.payload());
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }));
        }
        for (Thread t : threads) t.start();
        startGate.countDown();
        for (Thread t : threads) t.join();
        if (failure.get() != null) throw failure.get();

        check(received.size() == 20, "expected 20 deliveries, got " + received.size());
        List<Integer> sorted = new ArrayList<>(received);
        Collections.sort(sorted);
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 20; i++) expected.add(i);
        check(sorted.equals(expected), "each message should be delivered exactly once across concurrent consumers");
    }
}
