package org.sableos.reader.engine.comic

/**
 * Least-recently-used cache bounded by total bytes, not by entry count: decoded pages vary from a thumbnail to
 * several megabytes, so only a byte budget actually bounds memory. Every value that leaves the cache (evicted,
 * replaced, cleared, or too large to keep) is passed to [onRelease] exactly once, so bitmaps are freed
 * deterministically.
 */
class ByteBudgetCache<K : Any, V : Any>(
    val maxBytes: Long,
    private val sizeOf: (V) -> Long,
    private val onRelease: (K, V) -> Unit = { _, _ -> },
) {
    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
    }

    private val entries = LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, true)

    var totalBytes: Long = 0
        private set

    val size: Int get() = entries.size

    operator fun get(key: K): V? = entries[key]

    fun keys(): Set<K> = entries.keys.toSet()

    /** Stores [value]; returns false (after releasing it) when it alone exceeds the whole budget. */
    fun put(key: K, value: V): Boolean {
        remove(key)
        val bytes = sizeOf(value)
        if (bytes > maxBytes) {
            onRelease(key, value)
            return false
        }
        entries[key] = value
        totalBytes += bytes
        trim(protect = key)
        return true
    }

    fun remove(key: K) {
        entries.remove(key)?.let {
            totalBytes -= sizeOf(it)
            onRelease(key, it)
        }
    }

    /** Releases everything whose key is not accepted by [keep]. */
    fun retain(keep: (K) -> Boolean) {
        entries.keys.filterNot(keep).forEach(::remove)
    }

    fun clear() {
        entries.keys.toList().forEach(::remove)
    }

    private fun trim(protect: K) {
        while (totalBytes > maxBytes) {
            val eldest = entries.keys.firstOrNull { it != protect } ?: return
            remove(eldest)
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
