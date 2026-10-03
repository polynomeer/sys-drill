// Stage 2 — the relay.
// 학습 포인트: 별도의 relay가 outbox를 읽어 브로커로 보내고, 보낸 것을 표시한다 — 발행은 커밋된 사실만 따라간다.
package main

import (
	"fmt"
	"os"
	"reflect"
)

func main() {
	defer func() {
		if r := recover(); r != nil {
			switch {
			case r == "not implemented":
				fmt.Println("RESULT:FAIL:not implemented")
			default:
				if f, ok := r.(failure); ok {
					fmt.Printf("RESULT:FAIL:%s\n", string(f))
				} else {
					fmt.Printf("RESULT:FAIL:unexpected error: %v\n", r)
				}
			}
			os.Exit(1)
		}
	}()
	stage()
	fmt.Println("RESULT:PASS")
}

type failure string

func expect(condition bool, format string, args ...any) {
	if !condition {
		panic(failure(fmt.Sprintf(format, args...)))
	}
}

func ids(events []Event) []string {
	ids := []string{}
	for _, e := range events {
		ids = append(ids, e.ID)
	}
	return ids
}

func mustRun(relay *OutboxRelay) int {
	n, err := relay.RunOnce(100)
	if err != nil {
		panic(err)
	}
	return n
}

func stage() {
	db, broker := NewDatabase(), NewBroker()
	service, relay := NewOrderService(db, broker), NewOutboxRelay(db, broker)
	for i, amount := range []int{3, 1, 4} {
		if err := service.PlaceOrder(fmt.Sprintf("o-%d", i+1), amount); err != nil {
			panic(err)
		}
	}

	published := mustRun(relay)
	expect(published == 3, "RunOnce should report 3 published events, got %d", published)
	got := ids(broker.Published())
	expect(reflect.DeepEqual(got, []string{"evt-1", "evt-2", "evt-3"}), "events should be published oldest first, got %v", got)
	orderIDs := []any{}
	for _, e := range broker.Published() {
		orderIDs = append(orderIDs, e.Payload["order_id"])
	}
	expect(reflect.DeepEqual(orderIDs, []any{"o-1", "o-2", "o-3"}), "events should carry their orders oldest first, got %v", orderIDs)
	expect(len(db.PendingEvents(100)) == 0, "published events should be marked, nothing left pending")

	expect(mustRun(relay) == 0, "a second run with nothing pending should publish nothing")
	expect(len(broker.Published()) == 3, "already-published events must not be sent again")
}
