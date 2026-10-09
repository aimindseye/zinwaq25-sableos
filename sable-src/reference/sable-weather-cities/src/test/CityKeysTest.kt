package org.sableos.reference.weathercities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reference.typetofind.KeyInput
import org.sableos.reference.typetofind.KeyKind

class CityKeysTest {
    private val cities = DefaultCities.ALL
    private val model = CityScreenModel(cities, resultCount = 3)
    private val start = model.initial(cities.first())

    private fun key(
        kind: KeyKind,
        c: Char = KeyInput.NO_CHAR,
        shift: Boolean = false,
        ctrl: Boolean = false,
        repeat: Boolean = false,
        editable: Boolean = false
    ) = KeyInput(kind, c, shift = shift, ctrl = ctrl, repeat = repeat, editableFocused = editable)

    private fun CityScreenModel.press(s: CityScreenState, kind: KeyKind) = reduce(s, key(kind))

    private fun CityScreenModel.pressAll(
        s0: CityScreenState,
        vararg kinds: KeyKind
    ): CityScreenState = kinds.fold(s0) { s, k -> reduce(s, key(k)).state }

    // --- focus ---

    @Test fun focusStartsOnTheSelectedCity() {
        assertEquals("city:Jersey City", model.focusedId(start))
        assertEquals("city:Mumbai", model.focusedId(model.initial(cities.last())))
    }

    @Test fun focusIsHiddenUntilAKeyIsPressedAndTouchHidesItAgain() {
        assertFalse(start.focusVisible)
        val s = model.press(start, KeyKind.Down).state
        assertTrue(s.focusVisible)
        assertFalse(model.onTouch(s).focusVisible)
    }

    @Test fun arrowsWalkTheRowsThenAddAndResetAndStopAtTheEnds() {
        var s = start
        val seen = mutableListOf(model.focusedId(s))
        repeat(8) {
            s = model.press(s, KeyKind.Down).state
            seen += model.focusedId(s)
        }
        assertEquals(
            listOf(
                "city:Jersey City",
                "city:New York",
                "city:Edison",
                "city:Mumbai",
                "add_city",
                "reset_defaults"
            ),
            seen.distinct()
        )
        assertEquals("reset_defaults", seen.last())
        repeat(9) { s = model.press(s, KeyKind.Up).state }
        assertEquals("city:Jersey City", model.focusedId(s))
    }

    @Test fun moveHomeGoesToTheFirstItemAndMoveEndToTheLast() {
        val mid = model.pressAll(start, KeyKind.Down, KeyKind.Down)
        assertEquals("city:Jersey City", model.focusedId(model.press(mid, KeyKind.MoveHome).state))
        assertEquals("reset_defaults", model.focusedId(model.press(mid, KeyKind.MoveEnd).state))
    }

    @Test fun moveKeysLeaveARemoveActionForTheEdgeRows() {
        val onRemove = model.pressAll(start, KeyKind.Right)
        assertEquals("city:Jersey City:remove", onRemove.focusId)
        assertEquals(
            "reset_defaults",
            model.focusedId(model.press(onRemove, KeyKind.MoveEnd).state)
        )
    }

    @Test fun moveKeysNeverActivateOrLeaveTheScreen() {
        listOf(KeyKind.MoveHome, KeyKind.MoveEnd).forEach {
            val r = model.press(start, it)
            assertEquals(it.name, CityEffect.None, r.effect)
            assertEquals(it.name, CityScreen.List, r.state.screen)
            assertTrue(it.name, r.consumed)
        }
    }

    @Test fun systemHomeAsOtherIsNeverConsumedOnAnyScreen() {
        val add = model.reduce(start, key(KeyKind.Printable, 'p')).state
        val dialog = model.press(start, KeyKind.Backspace).state
        listOf(start, add, dialog).forEach {
            val r = model.reduce(it, key(KeyKind.Other))
            assertFalse(r.consumed)
            assertEquals(it, r.state)
        }
    }

    @Test fun pageUpAndPageDownAreNeverHandledOnAnyScreenAndStayWithTheEditor() {
        val add = model.reduce(start, key(KeyKind.Printable, 'p')).state
        val dialog = model.press(start, KeyKind.Backspace).state
        listOf(KeyKind.PageUp, KeyKind.PageDown).forEach { kind ->
            listOf(start, add, dialog).forEach {
                listOf(false, true).forEach { editable ->
                    val r = model.reduce(it, key(kind, editable = editable))
                    assertFalse(kind.name, r.consumed)
                    assertEquals(it, r.state)
                    assertEquals(CityEffect.None, r.effect)
                }
            }
        }
    }

    @Test fun theSearchFieldKeepsMoveHomeAndMoveEnd() {
        val open = model.reduce(start, key(KeyKind.Printable, 'p')).state
        listOf(KeyKind.MoveHome, KeyKind.MoveEnd).forEach {
            val r = model.press(open, it)
            assertFalse(it.name, r.consumed)
            assertEquals(open.query, r.state.query)
            assertEquals(-1, r.state.resultFocus)
        }
    }

    @Test fun anEditableFlagKeepsMoveKeysOnAnyScreen() {
        listOf(KeyKind.MoveHome, KeyKind.MoveEnd).forEach {
            val r = model.reduce(start, key(it, editable = true))
            assertFalse(it.name, r.consumed)
            assertEquals(start, r.state)
        }
    }

    @Test fun moveKeysJumpBetweenTheFirstAndLastResultOnceFocusIsOnAResult() {
        val open = model.reduce(start, key(KeyKind.Printable, 'p')).state
        val onResult = model.press(open, KeyKind.Down).state
        assertEquals(2, model.press(onResult, KeyKind.MoveEnd).state.resultFocus)
        assertEquals(
            0,
            model.press(
                model.press(onResult, KeyKind.MoveEnd).state,
                KeyKind.MoveHome
            ).state.resultFocus
        )
    }

    @Test fun moveKeysOnResultsWithNoResultsStayOnTheField() {
        val none = CityScreenModel(cities, resultCount = 0)
        val open = none.reduce(none.initial(cities.first()), key(KeyKind.Printable, 'z')).state
        assertEquals(-1, none.press(open.copy(resultFocus = 0), KeyKind.MoveEnd).state.resultFocus)
    }

    @Test fun moveKeysAreIgnoredInsideADialogButNotConsumedAsHome() {
        val dlg = model.press(start, KeyKind.Backspace).state
        val r = model.press(dlg, KeyKind.MoveEnd)
        assertEquals(CityScreen.ConfirmRemove, r.state.screen)
        assertEquals(DialogButton.Cancel, r.state.dialog)
    }

    @Test fun tabWalksAndHandsFocusOutAtTheEndsInsteadOfTrapping() {
        assertEquals(
            CityEffect.FocusLeaves(false),
            model.reduce(start, key(KeyKind.Tab, shift = true)).effect
        )
        var s = start
        repeat(5) { s = model.press(s, KeyKind.Tab).state }
        assertEquals("reset_defaults", model.focusedId(s))
        assertEquals(CityEffect.FocusLeaves(true), model.press(s, KeyKind.Tab).effect)
    }

    @Test fun rightReachesTheRemoveActionAndLeftComesBack() {
        val s = model.press(start, KeyKind.Right).state
        assertEquals("city:Jersey City:remove", s.focusId)
        assertEquals(
            "city:Jersey City",
            model.focusedId(model.press(s, KeyKind.Left).state).removeSuffix(":remove")
        )
    }

    // --- activation ---

    @Test fun enterAndSpaceSelectTheFocusedCity() {
        val s = model.press(start, KeyKind.Down).state
        listOf(KeyKind.Activate, KeyKind.Space).forEach {
            assertEquals(CityEffect.Select("New York"), model.press(s, it).effect)
        }
    }

    @Test fun heldEnterDoesNotActivateRepeatedly() {
        assertEquals(
            CityEffect.None,
            model.reduce(start, key(KeyKind.Activate, repeat = true)).effect
        )
    }

    @Test fun backAndEscapeLeaveTheScreenFromTheList() {
        listOf(KeyKind.Back, KeyKind.Escape).forEach {
            assertEquals(CityEffect.Exit, model.press(start, it).effect)
        }
    }

    // --- type to search/add ---

    @Test fun aPrintableKeyOpensAddWithTheFirstCharacterKept() {
        val r = model.reduce(start, key(KeyKind.Printable, 'p'))
        assertEquals(CityScreen.Add, r.state.screen)
        assertEquals("p", r.state.query)
        assertEquals(CityEffect.Search("p"), r.effect)
    }

    @Test fun aKeyboardOpenedSearchNeverAsksForTheSoftKeyboard() {
        assertFalse(model.reduce(start, key(KeyKind.Printable, 'p')).state.softKeyboard)
        assertFalse(
            model.pressAll(
                start,
                KeyKind.Down,
                KeyKind.Down,
                KeyKind.Down,
                KeyKind.Down,
                KeyKind.Activate
            ).softKeyboard
        )
    }

    @Test fun touchingTheSearchFieldIsWhatRequestsTheSoftKeyboard() {
        val open = model.reduce(start, key(KeyKind.Printable, 'p')).state
        assertTrue(model.onSearchFieldTapped(open).softKeyboard)
    }

    @Test fun aHeldKeyCannotStartASearch() {
        val r = model.reduce(start, key(KeyKind.Printable, 'p', repeat = true))
        assertEquals(CityScreen.List, r.state.screen)
    }

    @Test fun textKeysInTheSearchFieldAreLeftToTheEditable() {
        val open = model.reduce(start, key(KeyKind.Printable, 'p')).state
        listOf(
            KeyKind.Printable,
            KeyKind.Space,
            KeyKind.Backspace,
            KeyKind.Left,
            KeyKind.Right
        ).forEach {
            val r = model.reduce(open, key(it, 'a'))
            assertFalse(it.name, r.consumed)
        }
    }

    @Test fun commandChordsAreNotConsumed() {
        assertFalse(model.reduce(start, key(KeyKind.Printable, 'c', ctrl = true)).consumed)
    }

    @Test fun editingTheQueryAsksTheHostToSearchAndResetsResultFocus() {
        val open = model.reduce(start, key(KeyKind.Printable, 'p')).state
        val r = model.onQueryChanged(open.copy(resultFocus = 1), "par")
        assertEquals(CityEffect.Search("par"), r.effect)
        assertEquals(-1, r.state.resultFocus)
    }

    @Test fun downMovesFromTheFieldIntoTheResultsAndUpComesBack() {
        val open = model.reduce(start, key(KeyKind.Printable, 'p')).state
        var s = open
        repeat(5) { s = model.press(s, KeyKind.Down).state }
        assertEquals(2, s.resultFocus)
        repeat(5) { s = model.press(s, KeyKind.Up).state }
        assertEquals(-1, s.resultFocus)
    }

    @Test fun enterOnAResultAddsThatResultAndOnTheFieldAddsTheFirst() {
        val open = model.reduce(start, key(KeyKind.Printable, 'p')).state
        assertEquals(CityEffect.AddResult(0), model.press(open, KeyKind.Activate).effect)
        val onSecond = model.pressAll(open, KeyKind.Down, KeyKind.Down)
        assertEquals(CityEffect.AddResult(1), model.press(onSecond, KeyKind.Activate).effect)
        assertEquals(CityEffect.AddResult(1), model.press(onSecond, KeyKind.Space).effect)
    }

    @Test fun enterWithNoResultsDoesNothing() {
        val none = CityScreenModel(cities, resultCount = 0)
        val open = none.reduce(none.initial(cities.first()), key(KeyKind.Printable, 'z')).state
        assertEquals(CityEffect.None, none.press(open, KeyKind.Activate).effect)
    }

    @Test fun backFromAddReturnsToTheListWithFocusWhereItWas() {
        val s = model.pressAll(start, KeyKind.Down)
        val open = model.reduce(s, key(KeyKind.Printable, 'p')).state
        val back = model.press(open, KeyKind.Escape).state
        assertEquals(CityScreen.List, back.screen)
        assertEquals("", back.query)
        assertEquals("city:New York", model.focusedId(back))
    }

    @Test fun theAddRowOpensAnEmptySearchWithNoSearchYet() {
        val s = model.pressAll(start, KeyKind.Down, KeyKind.Down, KeyKind.Down, KeyKind.Down)
        val r = model.press(s, KeyKind.Activate)
        assertEquals(CityScreen.Add, r.state.screen)
        assertEquals(CityEffect.None, r.effect)
        val closed = model.press(r.state, KeyKind.Back).state
        assertEquals("add_city", model.focusedId(closed))
    }

    @Test fun tabInAddHandsFocusOutAndNeverTraps() {
        val open = model.reduce(start, key(KeyKind.Printable, 'p')).state
        assertEquals(CityEffect.FocusLeaves(true), model.press(open, KeyKind.Tab).effect)
    }

    // --- remove / reset dialogs ---

    @Test fun backspaceOnACityAsksToRemoveItWithCancelFocused() {
        val r = model.press(start, KeyKind.Backspace)
        assertEquals(CityScreen.ConfirmRemove, r.state.screen)
        assertEquals("Jersey City", r.state.pendingRemove)
        assertEquals(DialogButton.Cancel, r.state.dialog)
        assertEquals(CityEffect.None, r.effect)
    }

    @Test fun theRemoveActionAlsoOpensTheConfirmation() {
        val s = model.pressAll(start, KeyKind.Right)
        assertEquals(CityScreen.ConfirmRemove, model.press(s, KeyKind.Activate).state.screen)
    }

    @Test fun enterOnCancelKeepsTheCityAndRestoresFocus() {
        val dlg = model.pressAll(model.press(start, KeyKind.Down).state, KeyKind.Backspace)
        val r = model.press(dlg, KeyKind.Activate)
        assertEquals(CityEffect.None, r.effect)
        assertEquals(CityScreen.List, r.state.screen)
        assertEquals("city:New York", model.focusedId(r.state))
    }

    @Test fun confirmingRemovesTheCityAndFocusLandsOnTheNeighbour() {
        val dlg = model.press(model.press(start, KeyKind.Down).state, KeyKind.Backspace).state
        val toggled = model.press(dlg, KeyKind.Right).state
        assertEquals(DialogButton.Confirm, toggled.dialog)
        val r = model.press(toggled, KeyKind.Activate)
        assertEquals(CityEffect.Remove("New York"), r.effect)
        val after = CityScreenModel(cities.filter { it.name != "New York" })
        assertEquals("city:Edison", after.focusedId(after.afterChange(r.state, null)))
    }

    @Test fun removingTheLastRowFocusesTheNewLastRowNotOutOfRange() {
        var s = model.pressAll(start, KeyKind.Down, KeyKind.Down, KeyKind.Down)
        s = model.pressAll(s, KeyKind.Backspace, KeyKind.Right)
        val r = model.press(s, KeyKind.Activate)
        assertEquals(CityEffect.Remove("Mumbai"), r.effect)
        val after = CityScreenModel(cities.dropLast(1))
        assertEquals("add_city", after.focusedId(after.afterChange(r.state, null)))
    }

    @Test fun escapeAndBackCancelADialogAndRestoreFocus() {
        val dlg = model.press(start, KeyKind.Backspace).state
        listOf(KeyKind.Back, KeyKind.Escape).forEach {
            val s = model.press(dlg, it).state
            assertEquals(CityScreen.List, s.screen)
            assertEquals("city:Jersey City", model.focusedId(s))
        }
    }

    @Test fun resetAsksForConfirmationAndThenResets() {
        var s = start
        repeat(5) { s = model.press(s, KeyKind.Down).state }
        assertEquals("reset_defaults", model.focusedId(s))
        val dlg = model.press(s, KeyKind.Activate).state
        assertEquals(CityScreen.ConfirmReset, dlg.screen)
        assertEquals(CityEffect.None, model.press(dlg, KeyKind.Activate).effect)
        val confirmed = model.pressAll(dlg, KeyKind.Right)
        assertEquals(CityEffect.Reset, model.press(confirmed, KeyKind.Activate).effect)
    }

    @Test fun theDialogNeverTrapsFocusAndIgnoresStrayKeys() {
        val dlg = model.press(start, KeyKind.Backspace).state
        assertEquals(
            CityScreen.ConfirmRemove,
            model.reduce(dlg, key(KeyKind.Printable, 'q')).state.screen
        )
    }

    @Test fun focusFallsBackToTheNearestIndexWhenTheRowVanishes() {
        val s = model.pressAll(start, KeyKind.Down, KeyKind.Down)
        val smaller = CityScreenModel(cities.filter { it.name != "Edison" })
        assertEquals("city:Mumbai", smaller.focusedId(s))
    }
}
