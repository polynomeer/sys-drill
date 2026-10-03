import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record Event(String id, Object payload) {}

public class EventBus {
    private static final class Delivery {
        final Event event;
        int attempts;
        long visibleAtNanos;

        Delivery(Event event) {
            this.event = event;
        }
    }

    private static final class Subscriber {
        final Deque<Delivery> ready = new ArrayDeque<>();
        final Map<String, Delivery> inFlight = new LinkedHashMap<>();
    }

    private final long visibilityTimeoutNanos;
    private final int maxRetries;
    private final Map<String, List<String>> subscribersByTopic = new HashMap<>();
    private final Map<String, Subscriber> subscribers = new HashMap<>();

    public EventBus() {
        this(5.0, 3);
    }

    public EventBus(double visibilityTimeoutSeconds, int maxRetries) {
        this.visibilityTimeoutNanos = (long) (visibilityTimeoutSeconds * 1_000_000_000L);
        this.maxRetries = maxRetries;
    }

    public synchronized String subscribe(String topic) {
        String id = UUID.randomUUID().toString();
        subscribers.put(id, new Subscriber());
        subscribersByTopic.computeIfAbsent(topic, t -> new ArrayList<>()).add(id);
        return id;
    }

    public synchronized String publish(String topic, Object payload) {
        Event event = new Event(UUID.randomUUID().toString(), payload);
        for (String subId : subscribersByTopic.getOrDefault(topic, List.of())) {
            subscribers.get(subId).ready.addLast(new Delivery(event));
        }
        return event.id();
    }

    public synchronized Event poll(String subscriberId) {
        Subscriber sub = subscribers.get(subscriberId);
        if (sub == null) return null;
        requeueExpired(sub);
        Delivery next = sub.ready.pollFirst();
        if (next == null) return null;
        next.attempts++;
        next.visibleAtNanos = System.nanoTime() + visibilityTimeoutNanos;
        sub.inFlight.put(next.event.id(), next);
        return next.event;
    }

    public synchronized void ack(String subscriberId, String eventId) {
        Subscriber sub = subscribers.get(subscriberId);
        if (sub != null) sub.inFlight.remove(eventId);
    }

    // Timed-out deliveries go back to the ready queue until they've used up maxRetries attempts.
    private void requeueExpired(Subscriber sub) {
        long now = System.nanoTime();
        Iterator<Delivery> it = sub.inFlight.values().iterator();
        while (it.hasNext()) {
            Delivery d = it.next();
            if (d.visibleAtNanos - now <= 0) {
                it.remove();
                if (d.attempts < maxRetries) sub.ready.addLast(d);
            }
        }
    }
}
