// Stage 2 — ack / visibility timeout.
// 학습 포인트: at-least-once, 미확인 메시지 재전달 (ack 전에는 다른 컨슈머에게
// 보이지 않다가, visibility timeout이 지나면 다시 전달된다).
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
        Queue q = new Queue(0.3, 3);
        q.enqueue("x");
        Message msg = q.dequeue();
        check(msg != null, "expected a message");
        check(q.dequeue() == null, "in-flight message should not be immediately re-deliverable");
        Thread.sleep(400);
        Message redelivered = q.dequeue();
        check(redelivered != null, "message should be redelivered after visibility timeout without ack");
        check("x".equals(redelivered.payload()), "expected x, got " + redelivered.payload());
        q.ack(redelivered.id());
        check(q.dequeue() == null, "acked message should not be redelivered");
    }
}
