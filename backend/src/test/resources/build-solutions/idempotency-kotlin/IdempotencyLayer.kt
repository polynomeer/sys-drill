/*
 * SysDrill Build Mode — Build your own Idempotency Layer (Kotlin)
 *
 * Implement the classes below across 4 stages (see README.md).
 * Keep the class and member names as-is — the stage tests call them
 * directly. Submit by running ./submit.sh once you're ready.
 *
 * 채점 샌드박스는 이 파일과 테스트 파일을 함께 kotlinc로 컴파일합니다(Kotlin
 * 표준 라이브러리 + JDK만). 테스트가 같은 패키지에서 부르므로 package 선언은 넣지 마세요.
 */

class IdempotencyConflictException(message: String) : RuntimeException(message)

class IdempotencyInProgressException(message: String) : RuntimeException(message)

class IdempotencyLayer(
    private val ttlSeconds: Double = 86400.0,
) {
    /** done == false means the operation is still running (the key is claimed, no result yet). */
    private class Entry(val request: Any, val result: Any?, val done: Boolean, val expiresAtNanos: Long)

    private val ttlNanos = (ttlSeconds * 1_000_000_000L).toLong()
    private val lock = Any()
    private val entries = HashMap<String, Entry>()

    fun <T> execute(key: String, request: Any, operation: () -> T): T {
        synchronized(lock) {
            var entry = entries[key]
            if (entry != null && entry.done && entry.expiresAtNanos - System.nanoTime() <= 0) {
                entries.remove(key)
                entry = null
            }
            if (entry != null) {
                if (entry.request != request) {
                    throw IdempotencyConflictException("key $key was first used with a different request")
                }
                if (!entry.done) {
                    throw IdempotencyInProgressException("a request with key $key is still running")
                }
                @Suppress("UNCHECKED_CAST")
                return entry.result as T
            }
            // claim the key before running the operation, so a concurrent duplicate sees it in flight
            entries[key] = Entry(request, null, done = false, expiresAtNanos = 0)
        }
        val result = try {
            operation() // no lock held: a duplicate is rejected, not blocked
        } catch (e: Throwable) {
            synchronized(lock) { entries.remove(key) } // nothing stored — a retry runs the operation again
            throw e
        }
        synchronized(lock) {
            entries[key] = Entry(request, result, done = true, expiresAtNanos = System.nanoTime() + ttlNanos)
        }
        return result
    }
}
