// Stage 1 — one transaction, no dual write.
// 학습 포인트: 주문 저장과 이벤트 발행을 따로 하면 둘 중 하나만 성공한다 — 이벤트를 같은 트랜잭션의 outbox 테이블에 쓴다.
import java.util.List;
import java.util.Map;

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

        broker.failNextPublish(); // the broker being down must not matter when placing an order
        try {
            service.placeOrder("o-1", 3);
        } catch (BrokerException e) {
            throw new AssertionError("placeOrder must not publish to the broker itself (that's the dual write) — with the broker down, no order could be placed");
        }
        check(db.orders().equals(Map.of("o-1", 3)), "the order should be saved, orders are " + db.orders());
        List<Event> outbox = db.outbox();
        check(outbox.size() == 1, "expected exactly 1 outbox event, got " + outbox);
        Event event = outbox.get(0);
        check("OrderPlaced".equals(event.type()), "expected an OrderPlaced event, got " + event.type());
        check(event.payload().equals(Map.of("order_id", "o-1", "amount", 3)), "unexpected payload " + event.payload());
        check(db.commits() == 1, "the order and its event must be written in ONE transaction, saw " + db.commits() + " commits");
        check(broker.published().isEmpty(), "placeOrder must not publish to the broker itself (that's the dual write)");
        // With the broker up too — a dual write that swallows the broker error would hide behind the outage above.
        Database healthyDb = new Database();
        Broker healthyBroker = new Broker();
        new OrderService(healthyDb, healthyBroker).placeOrder("o-9", 1);
        check(healthyBroker.published().isEmpty(), "placeOrder must not publish to the broker itself (that's the dual write)");

        db.failNextCommit();
        try {
            service.placeOrder("o-2", 5);
            check(false, "a failed commit should surface to the caller as DatabaseException");
        } catch (DatabaseException e) {
            // expected
        }
        check(!db.orders().containsKey("o-2"), "after a failed commit the order must not exist");
        for (Event e : db.outbox()) {
            check(!"o-2".equals(e.payload().get("order_id")), "after a failed commit its event must not exist either");
        }
    }
}
