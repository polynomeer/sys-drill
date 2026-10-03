class LockStore {
    private data class Lease(val ownerId: String, val token: Long, val expiresAtNanos: Long) {
        fun isExpired(now: Long) = now - expiresAtNanos >= 0
    }

    private val leases = HashMap<String, Lease>()

    // Shared across keys and never reset, so a token is always greater than every earlier one.
    private var lastToken = 0L

    @Synchronized
    fun tryAcquire(key: String, ownerId: String, leaseSeconds: Double): Long? {
        val now = System.nanoTime()
        val current = leases[key]
        if (current != null && !current.isExpired(now)) return null
        val token = ++lastToken
        leases[key] = Lease(ownerId, token, now + (leaseSeconds * 1_000_000_000L).toLong())
        return token
    }

    @Synchronized
    fun tryRelease(key: String, ownerId: String, token: Long): Boolean {
        val current = leases[key] ?: return false
        if (current.isExpired(System.nanoTime())) return false
        if (current.ownerId != ownerId || current.token != token) return false
        leases.remove(key)
        return true
    }

    @Synchronized
    fun isLocked(key: String): Boolean {
        val current = leases[key] ?: return false
        return !current.isExpired(System.nanoTime())
    }
}

class DistributedLock(
    private val key: String,
    private val store: LockStore = LockStore(),
    private val leaseSeconds: Double = 5.0,
) {
    // Check-and-set happens atomically inside the store, like SET NX PX in Redis.
    fun acquire(ownerId: String): Long? = store.tryAcquire(key, ownerId, leaseSeconds)

    fun release(ownerId: String, fencingToken: Long): Boolean = store.tryRelease(key, ownerId, fencingToken)

    fun isLocked(): Boolean = store.isLocked(key)
}
