// Stage 4 — at-least-once, idempotent consumer.
// 학습 포인트: 보낸 뒤 표시하기 전에 죽으면 다시 보낸다(at-least-once) — 그래서 소비자는 이벤트 id로 중복을 걸러야 한다.
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

	db.FailNextMarkPublished() // the relay crashes right after the broker took evt-1
	if _, err := relay.RunOnce(100); err != nil && !errors.Is(err, ErrDatabase) {
		panic(err)
	}
	_, err := relay.RunOnce(100) // the restarted relay sends evt-1 again — it was never marked
	must(err)
	got := ids(broker.Published())
	expect(reflect.DeepEqual(got, []string{"evt-1", "evt-1"}), "an event published but not marked should be sent again (at-least-once), broker has %v", got)

	consumer := NewInventoryConsumer(10)
	for _, event := range broker.Published() {
		consumer.Handle(event)
	}
	expect(consumer.Stock() == 7, "the duplicate evt-1 must reserve stock only once: expected 7 left, got %d", consumer.Stock())

	must(service.PlaceOrder("o-2", 2))
	_, err = relay.RunOnce(100)
	must(err)
	published := broker.Published()
	consumer.Handle(published[len(published)-1])
	expect(consumer.Stock() == 5, "a new event should still be applied: expected 5 left, got %d", consumer.Stock())
}
