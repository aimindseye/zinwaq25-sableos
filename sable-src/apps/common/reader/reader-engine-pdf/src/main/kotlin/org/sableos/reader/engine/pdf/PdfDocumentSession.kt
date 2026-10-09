package org.sableos.reader.engine.pdf

/**
 * Renders PDF pages into images of type [I] (an Android Bitmap in production). Implementations wrap the platform
 * renderer and must be safe to [close] more than once.
 */
interface PdfPageRenderer<I : Any> : AutoCloseable {
    val pageCount: Int

    fun pageSize(page: Int): PdfPageSize

    fun render(page: Int, size: PixelSize): I

    fun release(image: I)
}

/**
 * One open PDF document: bounded caching around a [PdfPageRenderer]. Closing the session releases every cached image
 * and closes the renderer exactly once; further use fails fast.
 */
class PdfDocumentSession<I : Any>(
    private val renderer: PdfPageRenderer<I>,
    cacheCapacity: Int = DEFAULT_CACHE_PAGES,
) : AutoCloseable {
    private val cache = BoundedPageCache<I>(cacheCapacity) { _, image -> renderer.release(image) }
    private var closed = false

    val pageCount: Int get() = renderer.pageCount
    val isClosed: Boolean get() = closed
    val cachedPages: Set<Int> get() = cache.pages()

    /** Returns the page image sized for the viewport, rendering it only on a cache miss. */
    fun page(page: Int, viewportWidth: Int, viewportHeight: Int): I {
        check(!closed) { "document is closed" }
        require(page in 0 until pageCount) { "page $page out of range 0 until $pageCount" }
        cache[page]?.let { return it }
        val size = PdfRenderSizing.fit(renderer.pageSize(page), viewportWidth, viewportHeight)
        val image = renderer.render(page, size)
        cache.put(page, image)
        return image
    }

    /** Keeps only [center] and its direct neighbours cached. */
    fun retainAround(center: Int) {
        if (!closed) cache.retainWindow(center, PdfNavigator.PREFETCH_RADIUS)
    }

    /** Drops every cached image, e.g. when the viewport size changes and cached sizes are stale. */
    fun invalidate() {
        if (!closed) cache.clear()
    }

    override fun close() {
        if (closed) return
        closed = true
        cache.clear()
        renderer.close()
    }

    companion object {
        /** Current page plus one neighbour on each side. */
        const val DEFAULT_CACHE_PAGES: Int = 3
    }
}
