// Stage 3 — ordering.
// 학습 포인트: 같은 topic에 발행된 이벤트는 구독자별로 발행 순서대로 전달.
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

    static void run() {
        EventBus bus = new EventBus();
        String sub = bus.subscribe("orders");
        bus.publish("orders", "a");
        bus.publish("orders", "b");
        bus.publish("orders", "c");

        Event msg1 = bus.poll(sub);
        Event msg2 = bus.poll(sub);
        Event msg3 = bus.poll(sub);

        List<Object> got = List.of(msg1.payload(), msg2.payload(), msg3.payload());
        check(got.equals(List.of("a", "b", "c")), "expected FIFO order [a, b, c], got " + got);
    }
}
