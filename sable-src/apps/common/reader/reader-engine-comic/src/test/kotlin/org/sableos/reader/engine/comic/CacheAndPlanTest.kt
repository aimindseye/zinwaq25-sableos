package org.sableos.reader.engine.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reader.model.PageLocator

class CacheAndPlanTest {
    private class Released { val keys = mutableListOf<String>() }

    private fun cache(max: Long, log: Released) =
        ByteBudgetCache<String, ByteArray>(max, { it.size.toLong() }, { k, _ -> log.keys += k })

    @Test
    fun theByteBudgetIsNeverExceededAndOldestGoesFirst() {
        val log = Released()
        val c = cache(100, log)
        c.put("a", ByteArray(40)); c.put("b", ByteArray(40)); c["a"]
        c.put("c", ByteArray(40))
        assertTrue(c.totalBytes <= 100)
        assertEquals(setOf("a", "c"), c.keys())
        assertEquals(listOf("b"), log.keys)
    }

    @Test
    fun aValueLargerThanTheWholeBudgetIsReleasedNotKept() {
        val log = Released()
        val c = cache(10, log)
        assertFalse(c.put("big", ByteArray(11)))
        assertEquals(0, c.size)
        assertEquals(listOf("big"), log.keys)
    }

    @Test
    fun replacingClearingAndRetainingReleaseExactlyOnce() {
        val log = Released()
        val c = cache(1000, log)
        c.put("a", ByteArray(10)); c.put("a", ByteArray(20))
        assertEquals(20, c.totalBytes)
        c.put("b", ByteArray(5)); c.put("c", ByteArray(5))
        c.retain { it == "c" }
        assertEquals(setOf("c"), c.keys())
        c.clear()
        assertEquals(0, c.totalBytes)
        assertEquals(listOf("a", "a", "b", "c"), log.keys)
    }

    @Test
    fun theNewestEntryIsProtectedWhileTrimming() {
        val log = Released()
        val c = cache(50, log)
        c.put("a", ByteArray(30)); c.put("b", ByteArray(30))
        assertEquals(setOf("b"), c.keys())
    }

    @Test
    fun decodePlansStayInsideThePixelBudget() {
        val plan = ImageDecodePlanner.plan(4000, 6000, 1080, 1920)
        assertNotNull(plan)
        assertTrue(plan!!.outWidth.toLong() * plan.outHeight <= ComicLimits.DEFAULT_MAX_DECODED_PIXELS)
        assertTrue("never sample below the target", plan.outWidth >= 1080 || plan.sampleSize == 1)
        assertEquals(1, ImageDecodePlanner.plan(800, 1200, 1080, 1920)?.sampleSize)
    }

    @Test
    fun hugeImagesAreSubsampledAndAbsurdOnesRefused() {
        val big = ImageDecodePlanner.plan(10_000, 10_000, 100, 100)
        val budgetBytes = ComicLimits.DEFAULT_MAX_DECODED_PIXELS * DecodePlan.BYTES_PER_PIXEL
        assertTrue((big?.decodedBytes ?: Long.MAX_VALUE) <= budgetBytes)
        assertNull("100+ Mpx declared is a bomb", ImageDecodePlanner.plan(20_000, 20_000, 1080, 1920))
        assertNull(ImageDecodePlanner.plan(0, 100, 10, 10))
        assertNull(ImageDecodePlanner.plan(100, -1, 10, 10))
    }

    @Test
    fun aWebtoonStripIsPlannedByWidthWithinBudget() {
        val strip = ImageDecodePlanner.plan(800, 30_000, 1080, 2000)
        assertNotNull(strip)
        assertTrue(strip!!.outWidth.toLong() * strip.outHeight <= ComicLimits.DEFAULT_MAX_DECODED_PIXELS)
    }

    @Test
    fun navigationClampsAndReportsProgress() {
        val nav = ComicNavigator(5, startPage = 99)
        assertEquals(4, nav.currentPage)
        assertFalse(nav.next())
        assertTrue(nav.previous())
        assertTrue(nav.goTo(0)); assertFalse(nav.previous())
        assertEquals(PageLocator(0, 5), nav.locator())
        assertEquals(0..1, nav.window())
        assertTrue(ComicNavigator(5, 4).toProgress(1L).isFinished)
        assertEquals(2, ComicNavigator.fromProgress(null, 5).also { it.goTo(2) }.currentPage)
    }

    @Test
    fun chapterNavigationFollowsTheFolderStructure() {
        val chapters = listOf(ComicChapter("a", 0), ComicChapter("b", 10), ComicChapter("c", 20))
        val nav = ComicNavigator(30, 12)
        assertEquals(1, nav.chapterIndex(chapters))
        assertEquals(20, nav.nextChapterStart(chapters))
        assertEquals(10, nav.previousChapterStart(chapters))
        nav.goTo(10)
        assertEquals(0, nav.previousChapterStart(chapters))
        nav.goTo(25)
        assertNull(nav.nextChapterStart(chapters))
        assertNull(ComicNavigator(3).chapterIndex(emptyList()))
    }
}
