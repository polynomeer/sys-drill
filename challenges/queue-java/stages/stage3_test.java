// Stage 3 — max retries + dead-letter queue.
// 학습 포인트: poison message 격리 (계속 처리에 실패하는 메시지를 무한히
// 재전달하지 않고 DLQ로 옮긴다).
import java.util.List;

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

    static void run() throws InterruptedException {
        Queue q = new Queue(0.2, 2);
        q.enqueue("y");
        for (int i = 0; i < 2; i++) {
            Message msg = q.dequeue();
            check(msg != null, "expected a message");
            Thread.sleep(300);
        }
        check(q.dequeue() == null, "message should no longer be deliverable after exceeding maxRetries");
        List<Message> dlq = q.deadLetterQueue();
        check(dlq.size() == 1, "expected 1 message in DLQ, got " + dlq.size());
        check("y".equals(dlq.get(0).payload()), "expected y in DLQ, got " + dlq.get(0).payload());
    }
}
