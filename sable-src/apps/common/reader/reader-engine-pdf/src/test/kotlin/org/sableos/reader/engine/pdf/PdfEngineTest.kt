package org.sableos.reader.engine.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.sableos.reader.model.PageLocator
import org.sableos.reader.model.ReadingProgress

/** Test double that records every render, release and close so resource handling can be asserted exactly. */
private class FakeRenderer(
    override val pageCount: Int,
    private val size: PdfPageSize = PdfPageSize(612, 792),
) : PdfPageRenderer<FakeImage> {
    val rendered = mutableListOf<Int>()
    val released = mutableListOf<FakeImage>()
    var closeCalls = 0

    override fun pageSize(page: Int): PdfPageSize = size

    override fun render(page: Int, size: PixelSize): FakeImage {
        rendered += page
        return FakeImage(page, size)
    }

    override fun release(image: FakeImage) {
        released += image
    }

    override fun close() {
        closeCalls++
    }
}

private data class FakeImage(val page: Int, val size: PixelSize)

class PdfRenderSizingTest {
    @Test
    fun pageIsFittedToTheViewportKeepingAspectRatio() {
        val size = PdfRenderSizing.fit(PdfPageSize(612, 792), viewportWidth = 400, viewportHeight = 400)
        assertTrue(size.width <= 400 && size.height <= 400)
        assertEquals(400, size.height)
        assertEquals(309, size.width)
    }

    @Test
    fun hugePagesStayInsideTheBitmapBudget() {
        val size = PdfRenderSizing.fit(PdfPageSize(14400, 14400), viewportWidth = 8000, viewportHeight = 8000)
        assertTrue(size.width.toLong() * size.height <= PdfRenderSizing.MAX_BITMAP_PIXELS)
    }

    @Test
    fun degenerateViewportStillProducesAValidBitmapSize() {
        val size = PdfRenderSizing.fit(PdfPageSize(612, 792), viewportWidth = 0, viewportHeight = -5)
        assertTrue(size.width >= 1 && size.height >= 1)
    }

    @Test
    fun nonPositivePageSizeIsRejected() {
        try {
            PdfPageSize(0, 10)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("positive"))
        }
    }
}

class BoundedPageCacheTest {
    private fun cache(capacity: Int, released: MutableList<Int>) =
        BoundedPageCache<String>(capacity) { page, _ -> released += page }

    @Test
    fun neverExceedsCapacityAndEvictsLeastRecentlyUsed() {
        val released = mutableListOf<Int>()
        val c = cache(3, released)
        (0..9).forEach { c.put(it, "p$it") }
        assertEquals(3, c.size)
        assertEquals(setOf(7, 8, 9), c.pages())
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), released)
    }

    @Test
    fun readingAPageRefreshesItsRecency() {
        val released = mutableListOf<Int>()
        val c = cache(2, released)
        c.put(0, "a")
        c.put(1, "b")
        c[0]
        c.put(2, "c")
        assertEquals(setOf(0, 2), c.pages())
        assertEquals(listOf(1), released)
    }

    @Test
    fun theJustInsertedPageIsNeverEvicted() {
        val released = mutableListOf<Int>()
        val c = cache(1, released)
        c.put(5, "a")
        c.put(6, "b")
        assertEquals(setOf(6), c.pages())
    }

    @Test
    fun retainWindowDropsEverythingOutsideTheAdjacentPages() {
        val released = mutableListOf<Int>()
        val c = cache(10, released)
        (0..9).forEach { c.put(it, "p$it") }
        c.retainWindow(center = 5, radius = 1)
        assertEquals(setOf(4, 5, 6), c.pages())
        assertEquals(7, released.size)
    }

    @Test
    fun replacingAPageReleasesTheOldValueExactlyOnce() {
        val released = mutableListOf<Int>()
        val c = cache(3, released)
        c.put(1, "old")
        c.put(1, "new")
        assertEquals(listOf(1), released)
        assertEquals("new", c[1])
    }

    @Test
    fun clearReleasesEverythingAndEmptiesTheCache() {
        val released = mutableListOf<Int>()
        val c = cache(3, released)
        c.put(1, "a")
        c.put(2, "b")
        c.clear()
        assertEquals(0, c.size)
        assertEquals(listOf(1, 2), released.sorted())
    }

    @Test
    fun capacityMustBePositive() {
        try {
            BoundedPageCache<String>(0) { _, _ -> }
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("capacity"))
        }
    }
}

class PdfNavigatorTest {
    @Test
    fun navigationClampsAtBothEnds() {
        val nav = PdfNavigator(pageCount = 3)
        assertFalse(nav.previous())
        assertTrue(nav.next() && nav.next())
        assertFalse(nav.next())
        assertEquals(2, nav.currentPage)
        assertTrue(nav.isLastPage)
        assertTrue(nav.goTo(-10))
        assertTrue(nav.isFirstPage)
    }

    @Test
    fun startPageIsClamped() {
        assertEquals(2, PdfNavigator(3, startPage = 99).currentPage)
        assertEquals(0, PdfNavigator(3, startPage = -1).currentPage)
    }

    @Test
    fun adjacentPagesStayInsideTheDocument() {
        assertEquals(listOf(0, 1), PdfNavigator(5, 0).adjacentPages())
        assertEquals(listOf(1, 2, 3), PdfNavigator(5, 2).adjacentPages())
        assertEquals(listOf(3, 4), PdfNavigator(5, 4).adjacentPages())
        assertEquals(listOf(0), PdfNavigator(1, 0).adjacentPages())
    }

    @Test
    fun progressIsATypedPageLocatorAndFinishesOnTheLastPage() {
        val nav = PdfNavigator(10, 4)
        assertEquals(PageLocator(4, 10), nav.locator())
        assertFalse(nav.toProgress(1L).isFinished)
        nav.goTo(9)
        assertTrue(nav.toProgress(2L).isFinished)
    }

    @Test
    fun resumesFromSavedPageProgressAndIgnoresOtherLocatorTypes() {
        val saved = ReadingProgress.page(7, 20, 1L)
        assertEquals(7, PdfNavigator.fromProgress(saved, 20).currentPage)
        assertEquals(0, PdfNavigator.fromProgress(ReadingProgress.time(5, 10, 0, 1L), 20).currentPage)
        assertEquals(0, PdfNavigator.fromProgress(null, 20).currentPage)
    }

    @Test
    fun displayNumbersAreOneBased() {
        assertEquals(1, PdfNavigator.displayNumber(0))
    }

    @Test
    fun emptyDocumentIsRejected() {
        try {
            PdfNavigator(0)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("one page"))
        }
    }
}

class PdfDocumentSessionTest {
    @Test
    fun repeatedAccessToACachedPageRendersOnce() {
        val renderer = FakeRenderer(10)
        val session = PdfDocumentSession(renderer)
        val first = session.page(3, 400, 400)
        val second = session.page(3, 400, 400)
        assertSame(first, second)
        assertEquals(listOf(3), renderer.rendered)
    }

    @Test
    fun renderedBitmapSizeFollowsTheViewport() {
        val session = PdfDocumentSession(FakeRenderer(2))
        val small = session.page(0, 200, 200)
        assertTrue(small.size.width <= 200 && small.size.height <= 200)
    }

    @Test
    fun readingThroughALargeDocumentNeverKeepsMoreThanTheBoundedCache() {
        val renderer = FakeRenderer(500)
        val session = PdfDocumentSession(renderer)
        (0 until 500).forEach {
            session.page(it, 300, 300)
            session.retainAround(it)
            assertTrue(session.cachedPages.size <= PdfDocumentSession.DEFAULT_CACHE_PAGES)
        }
        assertEquals(500, renderer.rendered.size)
        assertTrue("only a bounded number of pages is ever alive", session.cachedPages.size <= 3)
        assertEquals(500 - session.cachedPages.size, renderer.released.size)
    }

    @Test
    fun retainAroundKeepsOnlyTheCurrentPageAndItsNeighbours() {
        val session = PdfDocumentSession(FakeRenderer(20), cacheCapacity = 10)
        (0..9).forEach { session.page(it, 100, 100) }
        session.retainAround(5)
        assertEquals(setOf(4, 5, 6), session.cachedPages)
    }

    @Test
    fun invalidateReleasesEverythingSoANewViewportReRenders() {
        val renderer = FakeRenderer(5)
        val session = PdfDocumentSession(renderer)
        val before = session.page(1, 100, 100)
        session.invalidate()
        val after = session.page(1, 800, 800)
        assertNotSame(before, after)
        assertTrue(renderer.released.contains(before))
    }

    @Test
    fun closeReleasesEveryCachedImageAndClosesTheRendererExactlyOnce() {
        val renderer = FakeRenderer(5)
        val session = PdfDocumentSession(renderer)
        session.page(0, 100, 100)
        session.page(1, 100, 100)
        session.close()
        session.close()
        assertEquals(1, renderer.closeCalls)
        assertEquals(2, renderer.released.size)
        assertTrue(session.isClosed)
        assertTrue(session.cachedPages.isEmpty())
    }

    @Test
    fun usingAClosedDocumentFailsFast() {
        val session = PdfDocumentSession(FakeRenderer(2))
        session.close()
        try {
            session.page(0, 10, 10)
            fail("expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("closed"))
        }
    }

    @Test
    fun outOfRangePagesAreRejected() {
        val session = PdfDocumentSession(FakeRenderer(2))
        try {
            session.page(2, 10, 10)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("out of range"))
        }
    }
}
