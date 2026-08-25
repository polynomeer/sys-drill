"""
SysDrill Build Mode — Build your own Retry/Backoff Middleware

Implement the classes below across 4 stages (see README.md). Keep the
class/method names as-is — the stage tests import this module directly.
Submit by running ./submit.sh once you're ready.
"""


class RetryExhaustedError(Exception):
    """Raised by execute() when every attempt failed."""


class RetryBudget:
    """A shared token bucket that caps the *total* number of retries across
    every RetryPolicy that shares it — protects a downstream dependency from
    a retry storm even when many independent callers are each individually
    retrying. Pass the same RetryBudget instance to multiple RetryPolicy
    instances to share it (stage 4).
    """

    def __init__(self, capacity: int = 10):
        # TODO(stage 4): store the starting capacity.
        raise NotImplementedError

    def try_consume(self) -> bool:
        # TODO(stage 4): if a token is available, consume it and return True.
        # If the budget is exhausted, return False (and consume nothing).
        raise NotImplementedError


class RetryPolicy:
    def __init__(
        self,
        max_attempts: int = 5,
        base_delay: float = 0.01,
        max_delay: float = 1.0,
        budget: RetryBudget | None = None,
        sleep_fn=None,
    ):
        # TODO(stage 1): store config. Default sleep_fn to time.sleep if None
        # (tests pass their own sleep_fn so they don't have to actually wait).
        raise NotImplementedError

    def execute(self, fn, *args, **kwargs):
        # TODO(stage 1): call fn(*args, **kwargs). On success, return its
        # result immediately. On failure, retry up to max_attempts total
        # calls, then raise RetryExhaustedError.
        # TODO(stage 3): between attempts, call self.sleep_fn(delay) where
        # delay grows exponentially with the attempt number (base_delay *
        # 2**attempt, capped at max_delay) *with jitter* — don't use the
        # exact exponential value, pick randomly within [0, capped_value]
        # ("full jitter") so many simultaneous retriers don't all retry at
        # the exact same moment (thundering herd).
        # TODO(stage 4): if a budget was provided, call budget.try_consume()
        # before each retry (not before the first attempt). If it returns
        # False, stop retrying immediately (raise RetryExhaustedError) even
        # if max_attempts hasn't been reached yet.
        raise NotImplementedError
