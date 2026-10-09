package org.sableos.reference.typetofind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartModelTest {
    private fun app(label: String) =
        AppEntry(label, "p.${label.lowercase().replace(' ', '_')}", "C")
    private val pinned =
        listOf(app("Phone"), app("Messages"), app("Camera"), app("Settings"), app("Sable Setup"))
    private val all = pinned + listOf(app("Radio diagnostics"), app("Weather"), app("Clock"))
    private val model = StartModel(pinned, all, listOf(CommandEntry("lock", "Lock screen")), 2)

    private fun key(
        kind: KeyKind,
        c: Char = KeyInput.NO_CHAR,
        repeat: Boolean = false,
        editable: Boolean = false,
        shift: Boolean = false
    ) = KeyInput(kind, c, shift = shift, repeat = repeat, editableFocused = editable)

    private fun type(s: StartState, text: String) = text.fold(s) { st, c ->
        model.reduce(st, key(KeyKind.Printable, c)).state
    }
    private fun press(s: StartState, kind: KeyKind) = model.reduce(s, key(kind))
    private fun label(s: StartState) = model.focused(s)?.label

    // --- type-to-find from the root and from All Apps ---

    @Test fun printableKeyFromRootStartsSearchAndKeepsTheFirstCharacter() {
        val r = model.reduce(StartState(), key(KeyKind.Printable, 's'))
        assertEquals(Level.Search, r.state.level)
        assertEquals("s", r.state.query)
        assertTrue(r.consumed)
    }

    @Test fun everyCharacterOfAFastBurstIsKept() {
        assertEquals("settin", type(StartState(), "settin").query)
    }

    @Test fun firstCharacterStartsSearchFromAllAppsToo() {
        val allApps = StartState(level = Level.AllApps)
        val r = model.reduce(allApps, key(KeyKind.Printable, 'w'))
        assertEquals(Level.Search, r.state.level)
        assertEquals(Level.AllApps, r.state.origin)
        assertEquals("w", r.state.query)
    }

    @Test fun searchRanksLikeTheReferenceFilter() {
        val s = type(StartState(), "se")
        assertEquals(listOf("Settings", "Sable Setup"), model.items(s).map { it.label })
    }

    @Test fun searchOffersCommandsAfterApps() {
        val s = type(StartState(), "lock")
        assertEquals(listOf("Clock", "Lock screen"), model.items(s).map { it.label })
        assertNotNull(model.items(s).last().command)
    }

    @Test fun firstMatchIsFocusedAndEnterLaunchesIt() {
        val s = type(StartState(), "mes")
        assertEquals("Messages", label(s))
        val r = press(s, KeyKind.Activate)
        assertEquals("Messages", (r.effect as Effect.Launch).item.label)
    }

    @Test fun enterOnNoMatchDoesNothing() {
        val r = press(type(StartState(), "zzz"), KeyKind.Activate)
        assertEquals(Effect.None, r.effect)
        assertEquals(-1, model.focusIndex(type(StartState(), "zzz")))
    }

    @Test fun spaceIsTextInSearchButNeverLeadsOrDoubles() {
        var s = type(StartState(), "sable")
        s = press(s, KeyKind.Space).state
        assertEquals("sable ", s.query)
        assertEquals("sable ", press(s, KeyKind.Space).state.query)
        s = type(s, "set")
        assertEquals("Sable Setup", label(s))
    }

    @Test fun backspaceEditsThenLeavesSearchWhenEmpty() {
        var s = type(StartState(), "ab")
        s = press(s, KeyKind.Backspace).state
        assertEquals("a", s.query)
        s = press(s, KeyKind.Backspace).state
        assertEquals(Level.Root, s.level)
        assertEquals("", s.query)
    }

    @Test fun queryIsBounded() {
        assertEquals(StartModel.MAX_QUERY, type(StartState(), "x".repeat(500)).query.length)
    }

    // --- editable text stays with the IME / InputConnection ---

    @Test fun textKeysInAFocusedEditableAreNotConsumed() {
        listOf(
            KeyKind.Printable,
            KeyKind.Space,
            KeyKind.Backspace,
            KeyKind.Left,
            KeyKind.Right,
            KeyKind.MoveHome,
            KeyKind.MoveEnd,
            KeyKind.PageUp,
            KeyKind.PageDown
        ).forEach {
            val r = model.reduce(StartState(), key(it, 'a', editable = true))
            assertFalse(it.name, r.consumed)
            assertEquals(StartState(), r.state)
        }
    }

    @Test fun resultNavigationStillWorksWhileAnEditableHasFocus() {
        val s = type(StartState(), "s")
        val r = model.reduce(s, key(KeyKind.Down, editable = true))
        assertTrue(r.consumed)
        assertEquals(1, model.focusIndex(r.state))
    }

    @Test fun commandChordsAreLeftToShortcutRouting() {
        val r = model.reduce(StartState(), KeyInput(KeyKind.Printable, 'f', ctrl = true))
        assertFalse(r.consumed)
        assertEquals(StartState(), r.state)
    }

    // --- no soft keyboard from a physical key ---

    @Test fun physicalTypingNeverRequestsTheSoftKeyboard() {
        assertFalse(type(StartState(), "abc").softImeRequested)
    }

    @Test fun onlyATouchOnTheSearchFieldRequestsIt() {
        val s = model.onSearchFieldTapped(StartState())
        assertTrue(s.softImeRequested)
        assertEquals(Level.Search, s.level)
        assertFalse(type(StartState(), "abc").softImeRequested)
    }

    @Test fun leavingSearchClearsTheSoftKeyboardRequest() {
        val s = model.onSearchFieldTapped(StartState())
        assertFalse(press(s, KeyKind.Back).state.softImeRequested)
    }

    // --- deterministic focus, arrows, D-pad ---

    @Test fun focusStartsOnTheFirstItemAndIsAlwaysInRange() {
        assertEquals(0, model.focusIndex(StartState()))
        var s = StartState()
        listOf(
            KeyKind.Down,
            KeyKind.Down,
            KeyKind.Down,
            KeyKind.Right,
            KeyKind.Up,
            KeyKind.Left,
            KeyKind.Left
        ).forEach {
            s = press(s, it).state
            assertTrue(model.focusIndex(s) in 0..pinned.lastIndex)
        }
    }

    @Test fun gridMovesByColumnsAndByOne() {
        var s = StartState()
        s = press(s, KeyKind.Right).state
        assertEquals("Messages", label(s))
        s = press(s, KeyKind.Down).state
        assertEquals("Settings", label(s))
        s = press(s, KeyKind.Left).state
        assertEquals("Camera", label(s))
        s = press(s, KeyKind.Up).state
        assertEquals("Phone", label(s))
    }

    @Test fun downIntoAPartialLastRowClampsToTheLastItem() {
        var s = StartState()
        repeat(3) { s = press(s, KeyKind.Right).state }
        s = press(s, KeyKind.Down).state
        assertEquals("Sable Setup", label(s))
    }

    @Test fun rootEdgesHandFocusOutInsteadOfTrapping() {
        assertEquals(Effect.FocusLeaves(false), press(StartState(), KeyKind.Up).effect)
        var s = StartState()
        repeat(4) { s = press(s, KeyKind.Right).state }
        assertEquals(Effect.FocusLeaves(true), press(s, KeyKind.Down).effect)
    }

    @Test fun allAppsListClampsAtItsEnds() {
        val s = StartState(level = Level.AllApps)
        assertEquals(0, model.focusIndex(press(s, KeyKind.Up).state))
        var t = s
        repeat(20) { t = press(t, KeyKind.Down).state }
        assertEquals(all.lastIndex, model.focusIndex(t))
    }

    @Test fun searchResultsMoveUpAndDownOnly() {
        val s = type(StartState(), "s")
        assertEquals(0, model.focusIndex(press(s, KeyKind.Right).state))
        assertEquals(1, model.focusIndex(press(s, KeyKind.Down).state))
        assertEquals(0, model.focusIndex(press(press(s, KeyKind.Down).state, KeyKind.Up).state))
    }

    @Test fun tabWalksForwardAndShiftTabBackAndNeitherTraps() {
        var s = StartState()
        s = press(s, KeyKind.Tab).state
        assertEquals("Messages", label(s))
        s = model.reduce(s, key(KeyKind.Tab, shift = true)).state
        assertEquals("Phone", label(s))
        assertEquals(
            Effect.FocusLeaves(false),
            model.reduce(s, key(KeyKind.Tab, shift = true)).effect
        )
        repeat(4) { s = press(s, KeyKind.Tab).state }
        assertEquals(Effect.FocusLeaves(true), press(s, KeyKind.Tab).effect)
    }

    // --- activation ---

    @Test fun enterAndSpaceActivateTheFocusedItemOutsideSearch() {
        val s = press(StartState(), KeyKind.Right).state
        assertEquals("Messages", (press(s, KeyKind.Activate).effect as Effect.Launch).item.label)
        assertEquals("Messages", (press(s, KeyKind.Space).effect as Effect.Launch).item.label)
    }

    @Test fun activationRecordsWhatWasLaunched() {
        val r = press(StartState(), KeyKind.Activate)
        assertEquals("p.phone/C", r.state.lastLaunchedId)
    }

    // --- Back / Escape / MoveHome semantics ---

    @Test fun backAndEscapeGoUpExactlyOneLevel() {
        listOf(KeyKind.Back, KeyKind.Escape).forEach {
            val s = type(StartState(level = Level.AllApps), "x")
            val a = press(s, it).state
            assertEquals(Level.AllApps, a.level)
            assertEquals("", a.query)
            assertEquals(Level.Root, press(a, it).state.level)
        }
    }

    @Test fun backAtTheRootIsNotConsumedSoTheSystemOwnsIt() {
        assertFalse(press(StartState(), KeyKind.Back).consumed)
        assertFalse(press(StartState(), KeyKind.Escape).consumed)
    }

    @Test fun systemHomeIsNotAnAppLocalRootCommand() {
        val deep = type(StartState(level = Level.AllApps), "wea")
        val r = model.reduce(deep, KeyMapper.map(RawKey(KeyMapper.KEYCODE_HOME, 0, 0, false)))
        assertFalse(r.consumed)
        assertEquals(deep, r.state)
        assertEquals(Level.Search, r.state.level)
        assertEquals("wea", r.state.query)
    }

    @Test fun moveHomeAndMoveEndFocusTheFirstAndLastItemOfTheCurrentCollection() {
        val levels =
            listOf(StartState(), StartState(level = Level.AllApps), type(StartState(), "e"))
        levels.forEach { start ->
            val items = model.items(start)
            val end = press(start, KeyKind.MoveEnd).state
            assertEquals(items.last().label, label(end))
            assertEquals(items.first().label, label(press(end, KeyKind.MoveHome).state))
            assertEquals(start.level, end.level)
            assertEquals(start.query, end.query)
        }
    }

    @Test fun moveHomeNeverLeavesTheCurrentLevel() {
        val deep = type(StartState(level = Level.AllApps), "wea")
        val r = press(deep, KeyKind.MoveHome)
        assertEquals(Level.Search, r.state.level)
        assertEquals("wea", r.state.query)
        assertEquals(Effect.None, r.effect)
    }

    @Test fun moveKeysWithAnEditableFocusedStayWithTheEditor() {
        listOf(KeyKind.MoveHome, KeyKind.MoveEnd).forEach {
            val s = type(StartState(), "se")
            val r = model.reduce(s, key(it, editable = true))
            assertFalse(it.name, r.consumed)
            assertEquals(s, r.state)
        }
    }

    @Test fun pagingKeysAreNeverHandledByStart() {
        listOf(KeyKind.PageUp, KeyKind.PageDown).forEach {
            val s = type(StartState(), "se")
            listOf(false, true).forEach { editable ->
                val r = model.reduce(s, key(it, editable = editable))
                assertFalse(it.name, r.consumed)
                assertEquals(s, r.state)
                assertEquals(Effect.None, r.effect)
            }
            val root = model.reduce(StartState(), key(it))
            assertFalse(it.name, root.consumed)
            assertEquals(StartState(), root.state)
        }
    }

    @Test fun activateKeyLaunchesAndIgnoresRepeat() {
        assertTrue(press(StartState(), KeyKind.Activate).effect is Effect.Launch)
        assertEquals(
            Effect.None,
            model.reduce(StartState(), key(KeyKind.Activate, repeat = true)).effect
        )
    }

    // --- key repeat ---

    @Test fun arrowRepeatMovesFocus() {
        val r = model.reduce(StartState(), key(KeyKind.Right, repeat = true))
        assertEquals("Messages", label(r.state))
    }

    @Test fun oneShotActionsIgnoreRepeat() {
        listOf(
            KeyKind.Activate,
            KeyKind.Space,
            KeyKind.Back,
            KeyKind.Escape,
            KeyKind.Tab
        ).forEach {
            val r = model.reduce(StartState(), key(it, repeat = true))
            assertEquals(it.name, Effect.None, r.effect)
            assertEquals(it.name, StartState(), r.state)
        }
    }

    @Test fun aRepeatCanNeverBeTheFirstCharacterOfASearch() {
        assertEquals(
            Level.Root,
            model.reduce(StartState(), key(KeyKind.Printable, 's', repeat = true)).state.level
        )
    }

    @Test fun repeatedCharactersInsideSearchAreKept() {
        val s = type(StartState(), "a")
        assertEquals("aa", model.reduce(s, key(KeyKind.Printable, 'a', repeat = true)).state.query)
    }

    // --- focus restoration ---

    @Test fun focusSurvivesADialogUnchanged() {
        val s = press(StartState(), KeyKind.Right).state
        assertEquals(s, model.onDialogDismissed(s))
        assertEquals("Messages", label(model.onDialogDismissed(s)))
    }

    @Test fun returningFromALaunchRestoresFocusToTheLaunchedItem() {
        var s = press(press(StartState(), KeyKind.Right).state, KeyKind.Down).state
        s = press(s, KeyKind.Activate).state
        val back = model.onReturnFromLaunch(s)
        assertEquals("Settings", label(back))
        assertEquals(Level.Root, back.level)
    }

    @Test fun returningFromASearchLaunchLeavesSearchAndKeepsOriginFocus() {
        val s = press(type(StartState(level = Level.AllApps), "cam"), KeyKind.Activate).state
        val back = model.onReturnFromLaunch(s)
        assertEquals(Level.AllApps, back.level)
        assertEquals("", back.query)
        assertEquals("Camera", label(back))
    }

    @Test fun focusFollowsTheItemWhenTheListChanges() {
        val s = press(press(StartState(), KeyKind.Right).state, KeyKind.Right).state
        assertEquals("Camera", label(s))
        val shrunk = model.withApps(pinned.filter { it.label != "Phone" }, all)
        assertEquals("Camera", shrunk.focused(s)?.label)
    }

    @Test fun focusFallsBackToTheNearestIndexWhenTheItemVanishes() {
        val s = press(press(StartState(), KeyKind.Right).state, KeyKind.Right).state
        val shrunk = model.withApps(pinned.filter { it.label != "Camera" }, all)
        assertEquals("Settings", shrunk.focused(s)?.label)
        val tiny = model.withApps(listOf(app("Phone")), all)
        assertEquals("Phone", tiny.focused(s)?.label)
    }

    @Test fun anEmptyLevelHasNoFocusAndNoCrash() {
        val empty = StartModel(emptyList(), emptyList(), emptyList(), 3)
        val s = StartState()
        assertEquals(-1, empty.focusIndex(s))
        assertNull(empty.focused(s))
        listOf(
            KeyKind.Up,
            KeyKind.Down,
            KeyKind.Left,
            KeyKind.Right,
            KeyKind.Tab,
            KeyKind.Activate,
            KeyKind.Space,
            KeyKind.MoveHome,
            KeyKind.MoveEnd
        ).forEach {
            empty.reduce(s, key(it))
        }
    }

    // --- touch stays complete ---

    @Test fun touchHidesTheFocusRingAndAKeyBringsItBack() {
        val s = press(StartState(), KeyKind.Right).state
        assertTrue(s.focusVisible)
        val t = model.onTouch(s)
        assertFalse(t.focusVisible)
        assertEquals(label(s), label(t))
        assertTrue(press(t, KeyKind.Right).state.focusVisible)
    }

    @Test fun otherKeysAreNotConsumed() {
        assertFalse(model.reduce(StartState(), key(KeyKind.Other)).consumed)
    }
}
