import java.util.HashMap;
import java.util.Map;

class LockStore {
    private record Lease(String ownerId, long token, long expiresAtNanos) {
        boolean isExpired(long now) {
            return now - expiresAtNanos >= 0;
        }
    }

    private final Map<String, Lease> leases = new HashMap<>();
    // Shared across keys and never reset, so a token is always greater than every earlier one.
    private long lastToken = 0;

    public LockStore() {}

    /** Returns a fencing token, or null if the lock wasn't acquired. */
    public synchronized Long tryAcquire(String key, String ownerId, double leaseSeconds) {
        long now = System.nanoTime();
        Lease current = leases.get(key);
        if (current != null && !current.isExpired(now)) return null;
        long token = ++lastToken;
        leases.put(key, new Lease(ownerId, token, now + (long) (leaseSeconds * 1_000_000_000L)));
        return token;
    }

    public synchronized boolean tryRelease(String key, String ownerId, long token) {
        Lease current = leases.get(key);
        if (current == null || current.isExpired(System.nanoTime())) return false;
        if (!current.ownerId().equals(ownerId) || current.token() != token) return false;
        leases.remove(key);
        return true;
    }

    public synchronized boolean isLocked(String key) {
        Lease current = leases.get(key);
        return current != null && !current.isExpired(System.nanoTime());
    }
}

public class DistributedLock {
    private final String key;
    private final LockStore store;
    private final double leaseSeconds;

    public DistributedLock(String key) {
        this(key, new LockStore(), 5.0);
    }

    public DistributedLock(String key, LockStore store) {
        this(key, store, 5.0);
    }

    public DistributedLock(String key, LockStore store, double leaseSeconds) {
        this.key = key;
        this.store = store;
        this.leaseSeconds = leaseSeconds;
    }

    /** Returns a fencing token, or null if the lock wasn't acquired. */
    public Long acquire(String ownerId) {
        // Check-and-set happens atomically inside the store, like SET NX PX in Redis.
        return store.tryAcquire(key, ownerId, leaseSeconds);
    }

    public boolean release(String ownerId, long fencingToken) {
        return store.tryRelease(key, ownerId, fencingToken);
    }

    public boolean isLocked() {
        return store.isLocked(key);
    }
}
