package org.sableos.titan2.camera.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickReviewTest {
    private val a = Capture("uri/a", "SBL_a.jpg", "image/jpeg")
    private val b = Capture("uri/b", "SBL_b.dng", "image/x-adobe-dng")
    private val c = Capture("uri/c", "SBL_c.mp4", "video/mp4")

    private fun session(vararg cs: Capture<String>): ReviewState<String> =
        cs.fold(ReviewState()) { s, x -> QuickReview.added(s, x) }

    private fun ReviewState<String>.press(vararg actions: ReviewAction): ReviewStep<String> =
        actions.fold(ReviewStep(this)) { step, x -> QuickReview.apply(step.state, x) }

    // ---- last capture -------------------------------------------------------------------------

    @Test fun lastCaptureIsRememberedAndKindComesFromMime() {
        val s = session(a, b, c)
        assertEquals(c, s.latest)
        assertFalse(s.open)
        assertNull(s.current)
        assertEquals(
            listOf(CaptureKind.Photo, CaptureKind.Raw, CaptureKind.Video),
            s.captures.map { it.kind }
        )
    }

    @Test fun openWithNothingCapturedSaysSoAndStaysClosed() {
        val step = QuickReview.open(ReviewState<String>())
        assertFalse(step.state.open)
        assertTrue(step.status!!.isNotBlank())
        assertNull(step.effect)
    }

    @Test fun openShowsTheLatestCapture() {
        val s = QuickReview.open(session(a, b, c)).state
        assertTrue(s.open)
        assertEquals(c, s.current)
        assertEquals("3 / 3", s.position)
    }

    @Test fun leftAndRightWalkThisSessionAndStopAtTheEnds() {
        val s = QuickReview.open(session(a, b, c)).state
        assertEquals(b, s.press(ReviewAction.Older).state.current)
        assertEquals(a, s.press(ReviewAction.Older, ReviewAction.Older).state.current)
        val oldest = s.press(ReviewAction.Older, ReviewAction.Older, ReviewAction.Older)
        assertEquals(a, oldest.state.current)
        assertTrue(oldest.status!!.isNotBlank())
        val newest = s.press(ReviewAction.Newer)
        assertEquals(c, newest.state.current)
        assertTrue(newest.status!!.isNotBlank())
    }

    @Test fun aNewCaptureDoesNotMoveAnOpenReview() {
        val s = QuickReview.open(session(a, b)).state.press(ReviewAction.Older).state
        val after = QuickReview.added(s, c)
        assertEquals(a, after.current)
        assertEquals("1 / 3", after.position)
    }

    @Test fun sessionListIsBounded() {
        var s = ReviewState<String>()
        repeat(QuickReview.MAX_SESSION_CAPTURES + 5) {
            s = QuickReview.added(s, Capture("u$it", "n$it", "image/jpeg"))
        }
        assertEquals(QuickReview.MAX_SESSION_CAPTURES, s.captures.size)
        assertEquals("u${QuickReview.MAX_SESSION_CAPTURES + 4}", s.latest!!.ref)
        assertEquals(s.captures.lastIndex, s.index)
    }

    // ---- delete needs an explicit confirm ----------------------------------------------------

    @Test fun deleteAsksFirstAndDoesNothingUntilConfirmed() {
        val s = QuickReview.open(session(a, b, c)).state
        val asked = s.press(ReviewAction.Delete)
        assertTrue(asked.state.confirmingDelete)
        assertNull(asked.effect)
        assertTrue(asked.status!!.contains("SBL_c.mp4"))
        val confirmed = asked.state.press(ReviewAction.Confirm)
        assertEquals(ReviewEffect.Delete(c), confirmed.effect)
        assertFalse(confirmed.state.confirmingDelete)
    }

    @Test fun deletePressedTwiceNeverDeletes() {
        val s = QuickReview.open(session(a)).state
        val twice = s.press(ReviewAction.Delete, ReviewAction.Delete)
        assertNull(twice.effect)
        assertTrue(twice.state.confirmingDelete)
    }

    @Test fun cancelOrBackKeepsTheFileAndTheReview() {
        val s = QuickReview.open(session(a, b)).state.press(ReviewAction.Delete).state
        for (x in listOf(ReviewAction.Cancel, ReviewAction.Close)) {
            val kept = s.press(x)
            assertNull(kept.effect)
            assertFalse(kept.state.confirmingDelete)
            assertTrue(kept.state.open)
            assertEquals(b, kept.state.current)
        }
    }

    @Test fun otherKeysAreIgnoredWhileConfirming() {
        val s = QuickReview.open(session(a, b)).state.press(ReviewAction.Delete).state
        for (x in listOf(ReviewAction.Older, ReviewAction.Share, ReviewAction.ShowInFiles)) {
            val step = s.press(x)
            assertNull(step.effect)
            assertEquals(s, step.state)
        }
    }

    @Test fun removedShowsTheNeighbourAndClosesWhenEmpty() {
        val s = QuickReview.open(session(a, b, c)).state.press(ReviewAction.Older).state
        val afterB = QuickReview.removed(s, b.ref)
        assertEquals(listOf(a, c), afterB.captures)
        assertEquals(c, afterB.current)
        val afterOlderRemoved = QuickReview.removed(QuickReview.open(session(a, b)).state, a.ref)
        assertEquals(b, afterOlderRemoved.current)
        val last = QuickReview.removed(QuickReview.open(session(a)).state, a.ref)
        assertFalse(last.open)
        assertNull(last.latest)
        assertEquals(s, QuickReview.removed(s, "unknown"))
    }

    // ---- share and Files only on request ----------------------------------------------------

    @Test fun shareFilesAndOpenAreExplicitEffectsOnTheShownCapture() {
        val s = QuickReview.open(session(a, b)).state
        assertEquals(ReviewEffect.Share(b), s.press(ReviewAction.Share).effect)
        assertEquals(ReviewEffect.ShowInFiles(b), s.press(ReviewAction.ShowInFiles).effect)
        assertEquals(ReviewEffect.Open(b), s.press(ReviewAction.Open).effect)
        // Opening, browsing and saving never share anything by themselves.
        assertNull(QuickReview.open(session(a, b)).effect)
        assertNull(s.press(ReviewAction.Older).effect)
    }

    @Test fun closeReturnsToTheViewfinderAndKeepsTheSession() {
        val s = QuickReview.open(session(a, b)).state.press(ReviewAction.Close).state
        assertFalse(s.open)
        assertEquals(b, s.latest)
        assertNull(QuickReview.apply(s, ReviewAction.Delete).effect)
    }

    // ---- review keys ------------------------------------------------------------------------

    @Test fun reviewKeysMatchTheSpec() {
        fun k(key: String, confirming: Boolean = false) =
            ReviewKeyMap.resolve(key, false, confirming)
        assertEquals(ReviewAction.Older, k("left"))
        assertEquals(ReviewAction.Newer, k("right"))
        assertEquals(ReviewAction.Delete, k("backspace"))
        assertEquals(ReviewAction.Delete, k("delete"))
        assertEquals(ReviewAction.Share, k("s"))
        assertEquals(ReviewAction.ShowInFiles, k("f"))
        assertEquals(ReviewAction.Close, k("escape"))
        assertEquals(ReviewAction.Close, k("back"))
        assertEquals(ReviewAction.Confirm, k("enter", true))
        assertEquals(ReviewAction.Confirm, k("y", true))
        assertEquals(ReviewAction.Cancel, k("n", true))
        assertEquals(ReviewAction.Cancel, k("escape", true))
        assertNull(k("backspace", true)) // Del never confirms its own question
        assertNull(k("space")) // the shutter does nothing inside the review
    }

    @Test fun reviewKeyNamesAreReachableFromRawKeyCodes() {
        assertEquals("backspace", RawKeys.nameOf(67))
        assertEquals("delete", RawKeys.nameOf(112))
        val bound = (ReviewKeyMap.browse.keys + ReviewKeyMap.confirm.keys).map { it.key }.toSet()
        assertTrue(RawKeys.allNames().containsAll(bound))
    }

    @Test fun reviewLegendIsGeneratedFromTheKeysAndCoversEveryAction() {
        assertEquals(ReviewAction.entries.toSet(), ControlLegend.reviewLabelled())
        val browse = ControlLegend.reviewEntries()
        assertEquals(ReviewKeyMap.browse.values.toSet().size, browse.size)
        assertTrue(browse.any { it.first == "Del / Delete" && it.second.contains("Delete") })
        assertTrue(browse.any { it.first == "S" && it.second == "Share" })
        assertTrue(browse.any { it.first == "F" && it.second == "Show in Files" })
        assertEquals(
            listOf("Enter / Y", "Back / Esc / N").sorted(),
            ControlLegend.reviewEntries(confirming = true).map { it.first }.sorted()
        )
    }

    @Test fun everyReviewKeyActionHasATouchButton() {
        assertEquals(
            ReviewKeyMap.browse.values.toSet(),
            ReviewTouch.browse.map { it.first }.toSet()
        )
        assertEquals(
            ReviewKeyMap.confirm.values.toSet(),
            ReviewTouch.confirm.map { it.first }.toSet()
        )
    }
}
