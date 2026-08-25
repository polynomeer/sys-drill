"""
SysDrill Build Mode — Build your own Circuit Breaker

Implement the CircuitBreaker class below across 4 stages (see README.md).
Keep the class/method names as-is — the stage tests import this module
directly. Submit by running ./submit.sh once you're ready.
"""


class CircuitOpenError(Exception):
    """Raised by call() when the breaker is OPEN — the wrapped function must not run."""


class CircuitBreaker:
    """Wraps calls to a possibly-failing function (e.g. an external API) and
    stops calling it once it's clearly broken, instead of letting every
    caller wait out its own timeout.
    """

    def __init__(self, failure_threshold: int = 3, recovery_timeout: float = 5.0):
        # TODO(stage 1): store config and start CLOSED.
        raise NotImplementedError

    @property
    def state(self) -> str:
        # TODO(stage 1): "CLOSED" | "OPEN" | "HALF_OPEN".
        # TODO(stage 3): once OPEN and recovery_timeout has elapsed since the
        # trip, reading state should report "HALF_OPEN" (a real trial call
        # hasn't necessarily happened yet — this is a state *transition*,
        # not just a label).
        raise NotImplementedError

    def call(self, fn, *args, **kwargs):
        # TODO(stage 1): while CLOSED, call fn(*args, **kwargs) and return its result.
        # TODO(stage 2): count consecutive failures; once failure_threshold is
        # reached, trip to OPEN. While OPEN, raise CircuitOpenError immediately
        # WITHOUT calling fn — that's the whole point (fail fast).
        # TODO(stage 3): once state has moved to HALF_OPEN (see the `state`
        # property), the next call() is a *trial*: run fn for real, and if it
        # succeeds, recover to CLOSED (reset the failure count too).
        # TODO(stage 4): if the HALF_OPEN trial call fails, go back to OPEN
        # and restart the recovery_timeout countdown from now.
        raise NotImplementedError
