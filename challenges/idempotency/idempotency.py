"""
SysDrill Build Mode — Build your own Idempotency Layer

Implement the classes below across 4 stages (see README.md). Keep the
class and method names as-is — the stage tests import this module directly.
Submit by running ./submit.sh once you're ready.
"""


class IdempotencyConflictError(Exception):
    """Raised by execute() when a key is reused with a different request."""


class IdempotencyInProgressError(Exception):
    """Raised by execute() when a request with the same key is still running."""


class IdempotencyLayer:
    """Wraps a side-effecting operation (e.g. charging a card through a payment
    gateway) so that retries carrying the same idempotency key — a client
    timing out and resending, a double-clicked button — perform it at most
    once and get the original result back.
    """

    def __init__(self, ttl: float = 86400.0):
        # TODO(stage 1): store config and set up whatever storage you need.
        # TODO(stage 4): keys are kept for `ttl` seconds, then forgotten.
        raise NotImplementedError

    def execute(self, key: str, request, fn):
        # TODO(stage 1): the first call for `key` runs fn() and stores its
        # result; every later call with the same key returns that stored
        # result WITHOUT calling fn again.
        # TODO(stage 2): remember the `request` each key was first used with.
        # The same key with a *different* request (compare by value) is a
        # client bug — raise IdempotencyConflictError instead of replaying.
        # TODO(stage 3): while fn() for a key is still running, another call
        # with that key must neither run fn nor wait — raise
        # IdempotencyInProgressError right away (the client retries later).
        # Don't hold a lock while fn() runs.
        # TODO(stage 4): if fn() raises, store nothing for the key (let the
        # exception propagate) so a retry can try again. Stored keys expire
        # `ttl` seconds after they were stored.
        raise NotImplementedError
