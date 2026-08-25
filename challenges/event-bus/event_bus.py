"""
SysDrill Build Mode — Build your own Event Bus

Implement the EventBus class below across 4 stages (see README.md). Keep
the class/method names as-is — the stage tests import this module
directly. Submit by running ./submit.sh once you're ready.
"""


class EventBus:
    """A topic-based pub/sub bus with at-least-once delivery: publish(topic,
    payload) fans out a copy of the event to every current subscriber of
    that topic. Each subscriber pulls its own copy via poll() — like
    Build your own Queue, a polled event stays invisible to that same
    subscriber's later poll() calls until it's ack()'d or the visibility
    timeout expires, at which point it's redelivered (up to max_retries).
    """

    def __init__(self, visibility_timeout: float = 5.0, max_retries: int = 3):
        # TODO(stage 1): store config and set up whatever storage you need.
        raise NotImplementedError

    def subscribe(self, topic: str) -> str:
        # TODO(stage 1): register a new subscriber for `topic`, return a
        # subscriber id used by poll()/ack(). Only events published *after*
        # subscribe() need to reach this subscriber.
        raise NotImplementedError

    def publish(self, topic: str, payload) -> str:
        # TODO(stage 1): deliver a copy of this event to every subscriber
        # currently subscribed to `topic` (fan-out) — subscribers of other
        # topics must not receive it. Return an event id.
        raise NotImplementedError

    def poll(self, subscriber_id: str) -> dict | None:
        # TODO(stage 1): pop this subscriber's oldest *visible* event (FIFO
        # per subscriber), or None if nothing is visible. Return
        # {"id": ..., "payload": ...}.
        # TODO(stage 2): once returned, the event must stay invisible to
        # this subscriber's other poll() calls until ack()'d or
        # visibility_timeout elapses (then it's redelivered).
        # TODO(stage 3): events for one subscriber must come out in the
        # same order they were published to its topic.
        # TODO(stage 4): make this safe when called concurrently from
        # multiple threads for the same subscriber — no event may be
        # delivered twice or lost.
        raise NotImplementedError

    def ack(self, subscriber_id: str, event_id: str) -> None:
        # TODO(stage 2): permanently remove the event so it's never redelivered.
        raise NotImplementedError
