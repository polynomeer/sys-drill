"""Stage 3 — a duplicate arrives while the first is still running.
학습 포인트: 결과가 저장되기 전(처리 중)에 온 중복 요청도 막아야 한다 — 여기서 이중 결제가 가장 많이 난다.
"""
import threading
from idempotency import IdempotencyInProgressError, IdempotencyLayer


def main():
    layer = IdempotencyLayer()
    charges = []
    started = threading.Event()
    release = threading.Event()

    def slow_charge():  # the payment gateway takes a while to answer
        charges.append("slow")
        started.set()
        release.wait(timeout=5)
        return "ch_1"

    def fast_charge():
        charges.append("fast")
        return "ch_dup"

    first_result = []
    first = threading.Thread(target=lambda: first_result.append(layer.execute("order-1", {"amount": 1000}, slow_charge)))
    first.start()
    assert started.wait(timeout=5), "the first request never started running"

    outcome = []

    def duplicate():
        try:
            outcome.append(layer.execute("order-1", {"amount": 1000}, fast_charge))
        except IdempotencyInProgressError:
            outcome.append("in-progress")
        except Exception as e:
            outcome.append(e)

    dup = threading.Thread(target=duplicate)
    dup.start()
    dup.join(timeout=2)
    blocked = dup.is_alive()
    release.set()
    first.join(timeout=5)
    dup.join(timeout=5)

    assert not blocked, "the duplicate request blocked while the first was running — don't hold a lock while fn runs; reject it instead"
    assert charges == ["slow"], f"a duplicate arriving mid-flight must not charge again, charges were {charges}"
    assert outcome == ["in-progress"], f"the duplicate should get IdempotencyInProgressError, got {outcome}"
    assert first_result == ["ch_1"], f"the first request should still finish normally, got {first_result}"
    assert layer.execute("order-1", {"amount": 1000}, fast_charge) == "ch_1", "once finished, the key should replay ch_1"


if __name__ == "__main__":
    try:
        main()
        print("RESULT:PASS")
    except AssertionError as e:
        print(f"RESULT:FAIL:{e}")
        raise SystemExit(1)
    except NotImplementedError:
        print("RESULT:FAIL:not implemented")
        raise SystemExit(1)
    except Exception as e:
        print(f"RESULT:FAIL:unexpected error: {e}")
        raise SystemExit(1)
