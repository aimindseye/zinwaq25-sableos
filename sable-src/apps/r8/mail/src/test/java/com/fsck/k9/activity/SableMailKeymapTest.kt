package com.fsck.k9.activity

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SableMailKeymapTest {
    private fun press(
        keyCode: Int,
        char: Char? = null,
        shift: Boolean = false,
        alt: Boolean = false,
        ctrl: Boolean = false,
        repeat: Int = 0,
    ) = SableMailKeyPress(keyCode, char, shift, alt, ctrl, repeat)

    private fun cmd(
        press: SableMailKeyPress,
        typing: Boolean = false,
    ) = SableMailKeymap.command(press, textInputActive = typing)

    @Test
    fun sharedContractKeys() {
        assertEquals(SableMailCommand.COMPOSE, cmd(press(KeyEvent.KEYCODE_C, 'c')))
        assertEquals(SableMailCommand.TOP, cmd(press(KeyEvent.KEYCODE_T, 't')))
        assertEquals(SableMailCommand.NEXT, cmd(press(KeyEvent.KEYCODE_J, 'j')))
        assertEquals(SableMailCommand.NEXT, cmd(press(KeyEvent.KEYCODE_N, 'n')))
        assertEquals(SableMailCommand.PREVIOUS, cmd(press(KeyEvent.KEYCODE_K, 'k')))
        assertEquals(SableMailCommand.PREVIOUS, cmd(press(KeyEvent.KEYCODE_P, 'p')))
        assertEquals(SableMailCommand.REPLY, cmd(press(KeyEvent.KEYCODE_R, 'r')))
        assertEquals(SableMailCommand.DELETE, cmd(press(KeyEvent.KEYCODE_D, 'd')))
        assertEquals(SableMailCommand.DELETE, cmd(press(KeyEvent.KEYCODE_DEL)))
        assertEquals(SableMailCommand.DELETE, cmd(press(KeyEvent.KEYCODE_FORWARD_DEL)))
        assertEquals(SableMailCommand.SEARCH, cmd(press(KeyEvent.KEYCODE_S, 's')))
        assertEquals(SableMailCommand.SEARCH, cmd(press(KeyEvent.KEYCODE_SLASH, '/')))
        assertEquals(SableMailCommand.BACK, cmd(press(KeyEvent.KEYCODE_ESCAPE)))
        assertNull("Enter opens the focused row natively", cmd(press(KeyEvent.KEYCODE_ENTER)))
    }

    @Test
    fun artifactReaderKeys() {
        // sable-mail-calendar-titan2.svg: R Reply, L All, F Fwd, V Archive, D Del, E quote, A attachments.
        assertEquals(SableMailCommand.REPLY_ALL, cmd(press(KeyEvent.KEYCODE_L, 'l')))
        assertEquals(SableMailCommand.FORWARD, cmd(press(KeyEvent.KEYCODE_F, 'f')))
        assertEquals(SableMailCommand.ARCHIVE, cmd(press(KeyEvent.KEYCODE_V, 'v')))
        assertEquals(SableMailCommand.EXPAND_QUOTE, cmd(press(KeyEvent.KEYCODE_E, 'e')))
        assertEquals(SableMailCommand.ATTACHMENTS, cmd(press(KeyEvent.KEYCODE_A, 'a')))
    }

    @Test
    fun splitViewForcedOnlyOnSquareKeyboardScreens() {
        assertTrue("Q25 720x720 @193dpi", SableMailLayout.forceSplitView(597, 597))
        assertTrue("square minus status bar", SableMailLayout.forceSplitView(597, 560))
        assertFalse("Pixel portrait", SableMailLayout.forceSplitView(412, 915))
        assertFalse("Pixel landscape", SableMailLayout.forceSplitView(915, 412))
        assertFalse("small square", SableMailLayout.forceSplitView(400, 400))
        assertFalse(SableMailLayout.forceSplitView(600, 0))
    }

    @Test
    fun quotesCollapsedUntilExpanded() {
        val body = "<p>Hi</p><blockquote>old</blockquote>"
        assertTrue(SableMailQuotes.html(body, expanded = false).startsWith("<style>"))
        assertTrue(SableMailQuotes.html(body, expanded = false).endsWith(body))
        assertTrue(SableMailQuotes.html(body, expanded = false).contains(SableMailQuotes.MARKER))
        assertEquals(body, SableMailQuotes.html(body, expanded = true))
    }

    @Test
    fun dockHintNamesRealKeys() {
        assertEquals(SableMailCommand.COMPOSE, cmd(press(KeyEvent.KEYCODE_C, 'c')))
        listOf("C Compose", "R Reply", "/ Search", "J/K Move", "T Latest").forEach {
            assertTrue(it, SableMailLayout.DOCK_LIST_HINT.contains(it))
        }
        listOf("L All", "F Fwd", "V Archive", "D Del", "E Quote", "A Attach").forEach {
            assertTrue(it, SableMailLayout.DOCK_READER_HINT.contains(it))
        }
    }

    @Test
    fun upstreamActionsKeptOnFreeKeys() {
        assertEquals(SableMailCommand.SELECT, cmd(press(KeyEvent.KEYCODE_X, 'x')))
        assertEquals(SableMailCommand.TOGGLE_FLAG, cmd(press(KeyEvent.KEYCODE_G, 'g')))
        assertEquals(SableMailCommand.MOVE, cmd(press(KeyEvent.KEYCODE_M, 'm')))
        assertEquals(SableMailCommand.COPY, cmd(press(KeyEvent.KEYCODE_Y, 'y')))
        assertEquals(SableMailCommand.TOGGLE_READ, cmd(press(KeyEvent.KEYCODE_Z, 'z')))
        assertEquals(SableMailCommand.VIEW_MODE, cmd(press(KeyEvent.KEYCODE_O, 'o')))
        assertEquals(SableMailCommand.REVERSE_SORT, cmd(press(KeyEvent.KEYCODE_I, 'i')))
    }

    @Test
    fun spaceIsExplicitPaging() {
        assertEquals(SableMailCommand.PAGE_DOWN, cmd(press(KeyEvent.KEYCODE_SPACE, ' ')))
        assertEquals(SableMailCommand.PAGE_UP, cmd(press(KeyEvent.KEYCODE_SPACE, ' ', shift = true)))
    }

    @Test
    fun slashTypedWithAltOrSymStillSearches() {
        assertEquals(SableMailCommand.SEARCH, cmd(press(KeyEvent.KEYCODE_O, '/', alt = true)))
    }

    @Test
    fun otherAltShiftAndCtrlChordsAreNotCommands() {
        assertNull(cmd(press(KeyEvent.KEYCODE_R, '3', alt = true)))
        assertNull(cmd(press(KeyEvent.KEYCODE_D, 'D', shift = true)))
        assertNull(cmd(press(KeyEvent.KEYCODE_C, 'c', ctrl = true)))
        assertNull(cmd(press(KeyEvent.KEYCODE_DEL, shift = true)))
    }

    @Test
    fun textInputAlwaysWins() {
        listOf(
            press(KeyEvent.KEYCODE_D, 'd'),
            press(KeyEvent.KEYCODE_DEL),
            press(KeyEvent.KEYCODE_SPACE, ' '),
            press(KeyEvent.KEYCODE_J, 'j'),
            press(KeyEvent.KEYCODE_SLASH, '/'),
            press(KeyEvent.KEYCODE_C, 'c'),
        ).forEach { assertNull("$it while typing", cmd(it, typing = true)) }
        assertEquals(SableMailCommand.BACK, cmd(press(KeyEvent.KEYCODE_ESCAPE), typing = true))
        assertFalse(SableMailKeymap.upstreamFallbackAllowed(press(KeyEvent.KEYCODE_DPAD_LEFT), textInputActive = true))
    }

    @Test
    fun heldKeysRepeatOnlyMovementAndPaging() {
        assertNull(cmd(press(KeyEvent.KEYCODE_D, 'd', repeat = 1)))
        assertNull(cmd(press(KeyEvent.KEYCODE_DEL, repeat = 3)))
        assertNull(cmd(press(KeyEvent.KEYCODE_C, 'c', repeat = 1)))
        assertEquals(SableMailCommand.NEXT, cmd(press(KeyEvent.KEYCODE_J, 'j', repeat = 4)))
        assertEquals(SableMailCommand.PAGE_DOWN, cmd(press(KeyEvent.KEYCODE_SPACE, ' ', repeat = 2)))
    }

    @Test
    fun onlyVolumeAndLeftRightReachUpstream() {
        assertTrue(SableMailKeymap.upstreamFallbackAllowed(press(KeyEvent.KEYCODE_VOLUME_UP), false))
        assertTrue(SableMailKeymap.upstreamFallbackAllowed(press(KeyEvent.KEYCODE_DPAD_RIGHT), false))
        // Upstream J = previous and S = select disagree with the Sable contract, so they never run.
        assertFalse(SableMailKeymap.upstreamFallbackAllowed(press(KeyEvent.KEYCODE_J, 'j'), false))
        assertFalse(SableMailKeymap.upstreamFallbackAllowed(press(KeyEvent.KEYCODE_S, 's'), false))
        assertFalse(SableMailKeymap.upstreamFallbackAllowed(press(KeyEvent.KEYCODE_Q, 'q'), false))
    }

    @Test
    fun composeSendAndCancel() {
        assertEquals(
            SableComposeCommand.SEND,
            SableMailKeymap.composeCommand(press(KeyEvent.KEYCODE_ENTER, ctrl = true)),
        )
        assertEquals(
            SableComposeCommand.SEND,
            SableMailKeymap.composeCommand(press(KeyEvent.KEYCODE_ENTER, alt = true)),
        )
        assertNull(SableMailKeymap.composeCommand(press(KeyEvent.KEYCODE_ENTER)))
        assertNull(SableMailKeymap.composeCommand(press(KeyEvent.KEYCODE_ENTER, alt = true, repeat = 1)))
        assertEquals(SableComposeCommand.CANCEL, SableMailKeymap.composeCommand(press(KeyEvent.KEYCODE_ESCAPE)))
        assertNull(SableMailKeymap.composeCommand(press(KeyEvent.KEYCODE_D, 'd')))
    }

    @Test
    fun listTargets() {
        // 20 rows, rows 5..12 visible, row 7 focused.
        fun target(
            move: SableListMove,
            focused: Int = 7,
        ) = SableMailKeymap.listTarget(move, focused, firstVisible = 5, lastVisible = 12, itemCount = 20)
        assertEquals(8, target(SableListMove.NEXT))
        assertEquals(6, target(SableListMove.PREVIOUS))
        assertEquals(14, target(SableListMove.PAGE_DOWN))
        assertEquals(0, target(SableListMove.PAGE_UP))
        assertEquals(0, target(SableListMove.TOP))
        // Nothing focused: the first move lands on the first visible row.
        assertEquals(5, target(SableListMove.NEXT, focused = -1))
        assertEquals(5, target(SableListMove.PREVIOUS, focused = -1))
        assertEquals(12, target(SableListMove.PAGE_DOWN, focused = -1))
        // Clamped at both ends; empty list has no target.
        assertEquals(19, SableMailKeymap.listTarget(SableListMove.NEXT, 19, 15, 19, 20))
        assertEquals(19, SableMailKeymap.listTarget(SableListMove.PAGE_DOWN, 18, 15, 19, 20))
        assertEquals(0, SableMailKeymap.listTarget(SableListMove.PREVIOUS, 0, 0, 4, 20))
        assertNull(SableMailKeymap.listTarget(SableListMove.TOP, -1, -1, -1, 0))
    }
}
