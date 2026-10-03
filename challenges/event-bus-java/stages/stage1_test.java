// Stage 1 — pub/sub fan-out.
// 학습 포인트: 하나의 publish가 해당 topic의 모든 구독자에게 전달됨.
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
        EventBus bus = new EventBus();
        String subA = bus.subscribe("orders");
        String subB = bus.subscribe("orders");
        String subC = bus.subscribe("payments");

        bus.publish("orders", "order-created");

        Event msgA = bus.poll(subA);
        Event msgB = bus.poll(subB);
        Event msgC = bus.poll(subC);

        check(msgA != null && "order-created".equals(msgA.payload()), "sub_a should receive the event");
        check(msgB != null && "order-created".equals(msgB.payload()), "sub_b should receive the event (fan-out)");
        check(msgC == null, "a subscriber to a different topic should not receive the event");
    }
}
