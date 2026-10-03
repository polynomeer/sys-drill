"""Stage 4 — failures and key expiry.
학습 포인트: 실패한 요청을 저장하면 재시도가 영원히 실패를 재생한다. 키도 영원히 보관할 수 없다(보존 기간).
"""
import time
from idempotency import IdempotencyLayer


def main():
    layer = IdempotencyLayer(ttl=0.3)
    attempts = []

    def flaky_charge():
        attempts.append(1)
        if len(attempts) == 1:
            raise ConnectionError("gateway timeout")
        return "ch_1"

    try:
        layer.execute("order-1", {"amount": 1000}, flaky_charge)
        assert False, "the operation's own error should propagate to the caller"
    except ConnectionError:
        pass
    result = layer.execute("order-1", {"amount": 1000}, flaky_charge)
    assert result == "ch_1", f"a retry after a failure should run the operation again, got {result}"
    assert len(attempts) == 2, f"expected 2 attempts (the failure is not stored), got {len(attempts)}"

    time.sleep(0.4)
    again = layer.execute("order-1", {"amount": 5000}, lambda: "ch_new")
    assert again == "ch_new", f"after the ttl the key is forgotten and may be reused, got {again}"


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
