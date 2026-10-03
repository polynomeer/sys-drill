// Stage 2 — the relay.
// 학습 포인트: 별도의 relay가 outbox를 읽어 브로커로 보내고, 보낸 것을 표시한다 — 발행은 커밋된 사실만 따라간다.
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

    static void run() {
        Database db = new Database();
        Broker broker = new Broker();
        OrderService service = new OrderService(db, broker);
        OutboxRelay relay = new OutboxRelay(db, broker);
        int[] amounts = {3, 1, 4};
        for (int i = 0; i < amounts.length; i++) service.placeOrder("o-" + (i + 1), amounts[i]);

        int published = relay.runOnce();
        check(published == 3, "runOnce should report 3 published events, got " + published);
        List<String> ids = new ArrayList<>();
        List<Object> orderIds = new ArrayList<>();
        for (Event e : broker.published()) {
            ids.add(e.id());
            orderIds.add(e.payload().get("order_id"));
        }
        check(ids.equals(List.of("evt-1", "evt-2", "evt-3")), "events should be published oldest first, got " + ids);
        check(orderIds.equals(List.of("o-1", "o-2", "o-3")), "events should carry their orders oldest first, got " + orderIds);
        check(db.pendingEvents(100).isEmpty(), "published events should be marked, nothing left pending");

        check(relay.runOnce() == 0, "a second run with nothing pending should publish nothing");
        check(broker.published().size() == 3, "already-published events must not be sent again");
    }
}
