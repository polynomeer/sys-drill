// Stage 2 — at-least-once delivery.
// 학습 포인트: ack 없이 visibility timeout이 지나면 재전달.
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
        EventBus bus = new EventBus(0.3, 3);
        String sub = bus.subscribe("orders");
        bus.publish("orders", "x");

        Event msg = bus.poll(sub);
        check(msg != null, "expected an event");
        check(bus.poll(sub) == null, "in-flight event should not be immediately re-deliverable");

        Thread.sleep(400);
        Event redelivered = bus.poll(sub);
        check(redelivered != null, "event should be redelivered after visibility timeout without ack");
        check("x".equals(redelivered.payload()), "redelivered event should carry the original payload");
        bus.ack(sub, redelivered.id());
        check(bus.poll(sub) == null, "acked event should not be redelivered");
    }
}
