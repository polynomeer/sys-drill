// Stage 3 — broker failure.
// 학습 포인트: 브로커가 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가 깨지고, 보내기 전에 표시하면 이벤트를 잃는다.
import java.util.ArrayList;
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

    static List<String> ids(List<Event> events) {
        List<String> ids = new ArrayList<>();
        for (Event e : events) ids.add(e.id());
        return ids;
    }

    static void run() {
        Database db = new Database();
        Broker broker = new Broker();
        OrderService service = new OrderService(db, broker);
        OutboxRelay relay = new OutboxRelay(db, broker);
        service.placeOrder("o-1", 3);
        check(relay.runOnce() == 1, "the first run should publish 1 event");

        service.placeOrder("o-2", 1);
        service.placeOrder("o-3", 4);
        broker.failNextPublish(); // evt-2 hits a broker hiccup
        int published;
        try {
            published = relay.runOnce();
        } catch (BrokerException e) {
            throw new AssertionError("runOnce should stop and return when the broker fails, not throw");
        }
        List<String> ids = ids(broker.published());
        check(ids.equals(List.of("evt-1")), "the relay must stop at the failed event, not skip ahead — broker has " + ids);
        check(published == 0, "nothing was published in that run, got " + published);
        List<String> pending = ids(db.pendingEvents(100));
        check(pending.equals(List.of("evt-2", "evt-3")), "the failed event must stay pending (never mark before publishing), pending is " + pending);

        check(relay.runOnce() == 2, "once the broker recovers, the next run should publish the rest");
        ids = ids(broker.published());
        check(ids.equals(List.of("evt-1", "evt-2", "evt-3")), "order must survive the failure, broker has " + ids);
    }
}
