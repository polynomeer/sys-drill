// Stage 1 — basic FIFO enqueue/dequeue.
// 학습 포인트: 큐의 기본 순서 보장 (먼저 넣은 메시지가 먼저 나온다).
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

    static void run() {
        Queue q = new Queue(5.0, 3);
        q.enqueue("a");
        q.enqueue("b");
        q.enqueue("c");
        Message msg1 = q.dequeue();
        Message msg2 = q.dequeue();
        Message msg3 = q.dequeue();
        check("a".equals(msg1.payload()), "expected a, got " + msg1.payload());
        check("b".equals(msg2.payload()), "expected b, got " + msg2.payload());
        check("c".equals(msg3.payload()), "expected c, got " + msg3.payload());
        check(q.dequeue() == null, "queue should be empty");
    }
}
