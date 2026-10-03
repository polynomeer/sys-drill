"""Stage 2 — same key, different request.
학습 포인트: 키를 재사용했는데 요청 내용이 다르면 재생이 아니라 클라이언트 버그다 — 조용히 옛 결과를 주면 안 된다.
"""
from idempotency import IdempotencyConflictError, IdempotencyLayer


def main():
    layer = IdempotencyLayer()
    charges = []

    def charge():
        charges.append(1)
        return f"ch_{len(charges)}"

    layer.execute("order-1", {"amount": 1000, "currency": "KRW"}, charge)
    # a new dict with the same contents is the same request
    replay = layer.execute("order-1", {"amount": 1000, "currency": "KRW"}, charge)
    assert replay == "ch_1", f"an equal request should be replayed, got {replay}"

    try:
        layer.execute("order-1", {"amount": 2000, "currency": "KRW"}, charge)
        assert False, "reusing a key with a different request should raise IdempotencyConflictError"
    except IdempotencyConflictError:
        pass
    assert len(charges) == 1, f"a conflicting request must not charge, got {len(charges)} charges"


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
