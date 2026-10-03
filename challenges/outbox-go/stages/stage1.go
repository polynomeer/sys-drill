// Stage 1 — one transaction, no dual write.
// 학습 포인트: 주문 저장과 이벤트 발행을 따로 하면 둘 중 하나만 성공한다 — 이벤트를 같은 트랜잭션의 outbox 테이블에 쓴다.
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

func stage() {
	db, broker := NewDatabase(), NewBroker()
	service := NewOrderService(db, broker)

	broker.FailNextPublish() // the broker being down must not matter when placing an order
	err := service.PlaceOrder("o-1", 3)
	expect(!errors.Is(err, ErrBroker), "PlaceOrder must not publish to the broker itself (that's the dual write) — with the broker down, no order could be placed")
	if err != nil {
		panic(err)
	}
	expect(reflect.DeepEqual(db.Orders(), map[string]int{"o-1": 3}), "the order should be saved, orders are %v", db.Orders())
	outbox := db.Outbox()
	expect(len(outbox) == 1, "expected exactly 1 outbox event, got %v", outbox)
	event := outbox[0]
	expect(event.Type == "OrderPlaced", "expected an OrderPlaced event, got %s", event.Type)
	expect(reflect.DeepEqual(event.Payload, map[string]any{"order_id": "o-1", "amount": 3}), "unexpected payload %v", event.Payload)
	expect(db.Commits() == 1, "the order and its event must be written in ONE transaction, saw %d commits", db.Commits())
	expect(len(broker.Published()) == 0, "PlaceOrder must not publish to the broker itself (that's the dual write)")
	// With the broker up too — a dual write that swallows the broker error would hide behind the outage above.
	healthyBroker := NewBroker()
	if err := NewOrderService(NewDatabase(), healthyBroker).PlaceOrder("o-9", 1); err != nil {
		panic(err)
	}
	expect(len(healthyBroker.Published()) == 0, "PlaceOrder must not publish to the broker itself (that's the dual write)")

	db.FailNextCommit()
	err = service.PlaceOrder("o-2", 5)
	expect(errors.Is(err, ErrDatabase), "a failed commit should surface to the caller as ErrDatabase, got %v", err)
	_, exists := db.Orders()["o-2"]
	expect(!exists, "after a failed commit the order must not exist")
	for _, e := range db.Outbox() {
		expect(e.Payload["order_id"] != "o-2", "after a failed commit its event must not exist either")
	}
}
