// Stage 3 — broker failure.
// 학습 포인트: 브로커가 실패하면 그 자리에서 멈춘다 — 건너뛰면 순서가 깨지고, 보내기 전에 표시하면 이벤트를 잃는다.
package main

import (
	"errors"
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

func must(err error) {
	if err != nil {
		panic(err)
	}
}

func stage() {
	db, broker := NewDatabase(), NewBroker()
	service, relay := NewOrderService(db, broker), NewOutboxRelay(db, broker)
	must(service.PlaceOrder("o-1", 3))
	n, err := relay.RunOnce(100)
	must(err)
	expect(n == 1, "the first run should publish 1 event, got %d", n)

	must(service.PlaceOrder("o-2", 1))
	must(service.PlaceOrder("o-3", 4))
	broker.FailNextPublish() // evt-2 hits a broker hiccup
	published, err := relay.RunOnce(100)
	expect(!errors.Is(err, ErrBroker), "RunOnce should stop and return when the broker fails, not return an error")
	must(err)
	got := ids(broker.Published())
	expect(reflect.DeepEqual(got, []string{"evt-1"}), "the relay must stop at the failed event, not skip ahead — broker has %v", got)
	expect(published == 0, "nothing was published in that run, got %d", published)
	pending := ids(db.PendingEvents(100))
	expect(reflect.DeepEqual(pending, []string{"evt-2", "evt-3"}), "the failed event must stay pending (never mark before publishing), pending is %v", pending)

	n, err = relay.RunOnce(100)
	must(err)
	expect(n == 2, "once the broker recovers, the next run should publish the rest")
	got = ids(broker.Published())
	expect(reflect.DeepEqual(got, []string{"evt-1", "evt-2", "evt-3"}), "order must survive the failure, broker has %v", got)
}
