package org.sableos.reader.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reader.capability.ReaderCapabilities

/** One-shot repeat guard: an auto-repeated key-down must never run a one-press-one-transition action twice. */
class ReaderKeyRepeatTest {
    private val epubPaged = KeyContext(ReaderSurface.EPUB_PAGED, ReaderCapabilities.EPUB)
    private val epubScroll = KeyContext(ReaderSurface.EPUB_SCROLL, ReaderCapabilities.EPUB)
    private val pdf = KeyContext(ReaderSurface.PDF_PAGED, ReaderCapabilities.PDF)
    private val audio = KeyContext(ReaderSurface.AUDIO, ReaderCapabilities.AUDIOBOOK)
    private val comicScroll = KeyContext(ReaderSurface.COMIC_CONTINUOUS, ReaderCapabilities.COMIC)

    private fun press(k: ReaderKey, character: Char? = null, repeated: Boolean = false) =
        KeyInput(k, character = character, repeated = repeated)

    private fun resolve(input: KeyInput, context: KeyContext) = ReaderKeyMap.resolve(input, context)

    private fun handled(input: KeyInput, context: KeyContext) =
        (resolve(input, context) as? KeyResolution.Handled)?.action

    @Test
    fun theInitialPressStillRunsEveryOneShotAction() {
        assertEquals(ReaderAction.TOGGLE_CHROME, handled(press(ReaderKey.ACTIVATE), epubPaged))
        assertEquals(ReaderAction.TOGGLE_CHROME, handled(press(ReaderKey.MENU), pdf))
        assertEquals(ReaderAction.TOGGLE_BOOKMARK, handled(press(ReaderKey.CHARACTER, 'b'), epubPaged))
        assertEquals(ReaderAction.OPEN_CONTENTS, handled(press(ReaderKey.CHARACTER, 't'), epubPaged))
        assertEquals(ReaderAction.OPEN_FIND, handled(press(ReaderKey.CHARACTER, 'f'), epubPaged))
        assertEquals(ReaderAction.OPEN_APPEARANCE, handled(press(ReaderKey.CHARACTER, 'a'), epubPaged))
        assertEquals(ReaderAction.DISMISS_OR_BACK, handled(press(ReaderKey.ESCAPE), epubPaged))
        assertEquals(ReaderAction.PLAY_PAUSE, handled(press(ReaderKey.SPACE), audio))
    }

    @Test
    fun repeatedChromeToggleCannotDoubleToggle() {
        listOf(ReaderKey.ACTIVATE, ReaderKey.MENU).forEach {
            assertEquals(KeyResolution.RepeatIgnored, resolve(press(it, repeated = true), epubPaged))
            assertEquals(KeyResolution.RepeatIgnored, resolve(press(it, repeated = true), pdf))
        }
    }

    @Test
    fun repeatedBookmarkContentsFindAndAppearanceAreIgnored() {
        listOf('b', 't', 'f', 'a').forEach { c ->
            val resolution = resolve(press(ReaderKey.CHARACTER, c, true), epubPaged)
            assertEquals("letter $c", KeyResolution.RepeatIgnored, resolution)
        }
        assertEquals(KeyResolution.RepeatIgnored, resolve(press(ReaderKey.CHARACTER, 'b', true), audio))
    }

    @Test
    fun repeatedEscapeAndBackCannotDismissTwice() {
        listOf(ReaderKey.ESCAPE, ReaderKey.BACK).forEach { key ->
            listOf(epubPaged, pdf, audio, comicScroll).forEach {
                assertEquals(KeyResolution.RepeatIgnored, resolve(press(key, repeated = true), it))
            }
        }
    }

    @Test
    fun repeatedPlayPauseCannotFlipPlaybackAgain() {
        assertEquals(KeyResolution.RepeatIgnored, resolve(press(ReaderKey.SPACE, repeated = true), audio))
    }

    @Test
    fun pageTurnsChapterChangesAndSeeksAreOnePressOneTransition() {
        listOf(ReaderKey.LEFT, ReaderKey.RIGHT, ReaderKey.PAGE_UP, ReaderKey.PAGE_DOWN, ReaderKey.SPACE).forEach {
            listOf(epubPaged, pdf, audio).forEach { context ->
                assertEquals(KeyResolution.RepeatIgnored, resolve(press(it, repeated = true), context))
            }
        }
    }

    @Test
    fun onlyLineScrollingMayRepeatWhileHeld() {
        assertEquals(ReaderAction.SCROLL_DOWN, handled(press(ReaderKey.DOWN, repeated = true), comicScroll))
        assertEquals(ReaderAction.SCROLL_UP, handled(press(ReaderKey.UP, repeated = true), epubScroll))
        assertEquals(
            ReaderAction.entries.filter { it.repeatsWhileHeld }.toSet(),
            setOf(ReaderAction.SCROLL_UP, ReaderAction.SCROLL_DOWN),
        )
        assertEquals(KeyResolution.RepeatIgnored, resolve(press(ReaderKey.PAGE_DOWN, repeated = true), comicScroll))
        assertEquals(KeyResolution.RepeatIgnored, resolve(press(ReaderKey.SPACE, repeated = true), comicScroll))
    }

    @Test
    fun aRepeatOfAKeyTheMapDoesNotHandleStillPassesThrough() {
        assertEquals(KeyResolution.PassThrough, resolve(press(ReaderKey.CHARACTER, 'f', true), pdf))
        assertEquals(KeyResolution.PassThrough, resolve(press(ReaderKey.ACTIVATE, repeated = true), audio))
        assertEquals(
            KeyResolution.PassThrough,
            ReaderKeyMap.resolve(press(ReaderKey.CHARACTER, 'b', true), epubPaged.copy(editableFocused = true)),
        )
        assertEquals(
            KeyResolution.PassThrough,
            ReaderKeyMap.resolve(KeyInput(ReaderKey.CHARACTER, 'b', ctrl = true, repeated = true), epubPaged),
        )
    }

    @Test
    fun epubPolicyNeverEmitsACommandForARepeatedOneShotKey() {
        fun decide(input: KeyInput) =
            EpubKeyPolicy.decide(input, scrollMode = false, editableFocused = false, overlayOpen = false)
        assertEquals(EpubKeyCommand.TOGGLE_CHROME, decide(press(ReaderKey.ACTIVATE)))
        assertNull(decide(press(ReaderKey.ACTIVATE, repeated = true)))
        assertNull(decide(press(ReaderKey.CHARACTER, 'b', true)))
        assertNull(decide(press(ReaderKey.CHARACTER, 't', true)))
        assertNull(decide(press(ReaderKey.CHARACTER, 'f', true)))
        assertNull(decide(press(ReaderKey.CHARACTER, 'a', true)))
        assertNull(decide(press(ReaderKey.ESCAPE, repeated = true)))
        assertEquals(EpubKeyCommand.ESCAPE, decide(press(ReaderKey.ESCAPE)))
    }

    @Test
    fun theInputBoundaryDefaultsToNotRepeated() {
        assertFalse(KeyInput(ReaderKey.SPACE).repeated)
        assertTrue(KeyInput(ReaderKey.SPACE, repeated = true).repeated)
    }
}
