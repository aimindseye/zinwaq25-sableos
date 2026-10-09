package org.sableos.reader.engine.pdf

/**
 * Bounded cache of rendered pages. At most [capacity] pages are retained; the least recently used page is evicted
 * first, and [retainWindow] drops everything outside the pages adjacent to the current one. Every evicted or cleared
 * value is handed to [onRelease] exactly once so bitmaps are freed deterministically.
 */
class BoundedPageCache<V : Any>(
    val capacity: Int,
    private val onRelease: (page: Int, value: V) -> Unit,
) {
    init {
        require(capacity >= 1) { "capacity must be at least 1" }
    }

    // Access-ordered: iteration starts at the least recently used entry.
    private val entries = LinkedHashMap<Int, V>(capacity + 1, 1f, true)

    val size: Int get() = entries.size

    fun pages(): Set<Int> = entries.keys.toSet()

    operator fun get(page: Int): V? = entries[page]

    fun put(page: Int, value: V) {
        val previous = entries.put(page, value)
        if (previous != null && previous !== value) onRelease(page, previous)
        while (entries.size > capacity) {
            val eldest = entries.entries.first { it.key != page }
            entries.remove(eldest.key)
            onRelease(eldest.key, eldest.value)
        }
    }

    /** Evicts every page farther than [radius] from [center]. */
    fun retainWindow(center: Int, radius: Int) {
        val doomed = entries.keys.filter { kotlin.math.abs(it - center) > radius }
        doomed.forEach { page -> entries.remove(page)?.let { onRelease(page, it) } }
    }

    fun clear() {
        val all = entries.toList()
        entries.clear()
        all.forEach { (page, value) -> onRelease(page, value) }
    }
}
