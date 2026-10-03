"""Stage 1 — replay the stored result.
학습 포인트: 같은 멱등성 키로 다시 온 요청은 결제를 다시 하지 않고 처음 결과를 돌려준다.
"""
from idempotency import IdempotencyLayer


def main():
    layer = IdempotencyLayer()
    charges = []

    def charge():
        charges.append(1000)
        return {"charge_id": f"ch_{len(charges)}", "amount": 1000}

    first = layer.execute("order-1", {"amount": 1000}, charge)
    retry = layer.execute("order-1", {"amount": 1000}, charge)
    assert len(charges) == 1, f"a retry with the same key must not charge again, got {len(charges)} charges"
    assert retry == first, f"the retry should get the original result {first}, got {retry}"

    other = layer.execute("order-2", {"amount": 1000}, charge)
    assert len(charges) == 2, "a different key is a different request and should charge"
    assert other["charge_id"] == "ch_2", f"expected ch_2 for the new key, got {other}"


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
