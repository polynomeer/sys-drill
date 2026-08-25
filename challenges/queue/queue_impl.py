"""
SysDrill Build Mode — Build your own Queue

Implement the Queue class below across 4 stages (see README.md). Keep the
class and method names as-is — the stage tests import this module directly.
Submit by running ./submit.sh once you're ready.
"""


class Queue:
    """An at-least-once message queue with visibility timeouts (like SQS),
    not a plain FIFO: a dequeued message stays invisible to other
    dequeue() calls until it's ack()'d or the visibility timeout expires,
    at which point it's redelivered — up to max_retries times before it
    moves to the dead_letter_queue.
    """

    def __init__(self, visibility_timeout: float = 5.0, max_retries: int = 3):
        # TODO(stage 1): store config and set up whatever storage you need.
        raise NotImplementedError

    def enqueue(self, payload) -> str:
        # TODO(stage 1): add a message, return its message id.
        raise NotImplementedError

    def dequeue(self) -> dict | None:
        # TODO(stage 1): pop the oldest *visible* message (FIFO), or None
        # if nothing is visible. Return {"id": ..., "payload": ...}.
        # TODO(stage 2): once returned, the message must stay invisible to
        # other dequeue() calls until ack()'d or visibility_timeout elapses.
        # TODO(stage 3): if a message's attempts reach max_retries without
        # being ack'd, move it to dead_letter_queue instead of redelivering.
        # TODO(stage 4): make this safe when called concurrently from
        # multiple threads — no two callers may receive the same message.
        raise NotImplementedError

    def ack(self, message_id: str) -> None:
        # TODO(stage 2): permanently remove the message so it's never redelivered.
        raise NotImplementedError

    @property
    def dead_letter_queue(self) -> list:
        # TODO(stage 3): messages that exceeded max_retries without being ack'd.
        raise NotImplementedError
