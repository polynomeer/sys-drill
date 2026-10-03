// Stage 4 — at-least-once, idempotent consumer.
// 학습 포인트: 보낸 뒤 표시하기 전에 죽으면 다시 보낸다(at-least-once) — 그래서 소비자는 이벤트 id로 중복을 걸러야 한다.
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
        service.placeOrder("o-1", 3);

        db.failNextMarkPublished(); // the relay crashes right after the broker took evt-1
        try {
            relay.runOnce();
        } catch (DatabaseException e) {
            // the crash
        }
        relay.runOnce(); // the restarted relay sends evt-1 again — it was never marked
        List<String> ids = new ArrayList<>();
        for (Event e : broker.published()) ids.add(e.id());
        check(ids.equals(List.of("evt-1", "evt-1")), "an event published but not marked should be sent again (at-least-once), broker has " + ids);

        InventoryConsumer consumer = new InventoryConsumer(10);
        for (Event event : broker.published()) consumer.handle(event);
        check(consumer.stock() == 7, "the duplicate evt-1 must reserve stock only once: expected 7 left, got " + consumer.stock());

        service.placeOrder("o-2", 2);
        relay.runOnce();
        List<Event> published = broker.published();
        consumer.handle(published.get(published.size() - 1));
        check(consumer.stock() == 5, "a new event should still be applied: expected 5 left, got " + consumer.stock());
    }
}
