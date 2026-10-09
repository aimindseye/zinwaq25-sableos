package org.sableos.reference.setupflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reference.typetofind.KeyInput
import org.sableos.reference.typetofind.KeyKind

class FlowKeyModelTest {
    private val networks = listOf(WifiNetwork("Home", true), WifiNetwork("Cafe", false))
    private val welcome = FlowKeyModel(Steps.welcome())
    private val wifi = FlowKeyModel(Steps.wifiList(networks))
    private val password = FlowKeyModel(Steps.wifiPassword())
    private val account = FlowKeyModel(Steps.account())

    private fun key(
        kind: KeyKind,
        c: Char = KeyInput.NO_CHAR,
        shift: Boolean = false,
        repeat: Boolean = false,
        editable: Boolean = false
    ) = KeyInput(kind, c, shift = shift, repeat = repeat, editableFocused = editable)

    private fun FlowKeyModel.press(s: FlowState, kind: KeyKind) = reduce(s, key(kind))

    // --- deterministic initial focus ---

    @Test fun focusStartsOnThePrimaryActionWhenThereIsOne() {
        assertEquals("start", welcome.focused(FlowState())?.id)
        assertEquals("username", account.focused(FlowState())?.id)
        assertEquals("password", password.focused(FlowState())?.id)
    }

    @Test fun aStepWithoutAPrimaryStartsOnItsFirstControl() {
        assertEquals("wifi:Home", wifi.focused(FlowState())?.id)
    }

    @Test fun focusIsAlwaysAValidIndex() {
        var s = FlowState()
        repeat(12) { s = wifi.press(s, KeyKind.Down).state }
        assertTrue(wifi.focusIndex(s) in 0..3)
        repeat(12) { s = wifi.press(s, KeyKind.Up).state }
        assertEquals(0, wifi.focusIndex(s))
    }

    // --- arrows, Tab, Shift+Tab, no trap ---

    @Test fun arrowsWalkTheWifiListAndReachAddNetworkAndSkip() {
        var s = FlowState()
        s = wifi.press(s, KeyKind.Down).state
        assertEquals("wifi:Cafe", wifi.focused(s)?.id)
        s = wifi.press(s, KeyKind.Down).state
        assertEquals(Steps.ADD_NETWORK, wifi.focused(s)?.id)
        s = wifi.press(s, KeyKind.Down).state
        assertEquals("wifi_skip", wifi.focused(s)?.id)
    }

    @Test fun tabAndShiftTabWalkAndNeverTrap() {
        var s = FlowState()
        s = welcome.press(s, KeyKind.Tab).state
        assertEquals("language", welcome.focused(s)?.id)
        assertEquals(FlowEffect.FocusLeaves(true), welcome.press(s, KeyKind.Tab).effect)
        s = welcome.reduce(s, key(KeyKind.Tab, shift = true)).state
        assertEquals("start", welcome.focused(s)?.id)
        assertEquals(
            FlowEffect.FocusLeaves(false),
            welcome.reduce(s, key(KeyKind.Tab, shift = true)).effect
        )
    }

    @Test fun disabledControlsAreSkippedByEveryMove() {
        val m = FlowKeyModel(Steps.account(FlowPolicy(accountSkippable = false)))
        var s = FlowState()
        repeat(6) { s = m.press(s, KeyKind.Down).state }
        assertEquals("sign_in", m.focused(s)?.id)
    }

    // --- activation ---

    @Test fun enterAndSpaceActivateAButtonOrListItem() {
        val s = wifi.press(FlowState(), KeyKind.Down).state
        assertEquals(FlowEffect.Activate("wifi:Cafe"), wifi.press(s, KeyKind.Activate).effect)
        assertEquals(FlowEffect.Activate("wifi:Cafe"), wifi.press(s, KeyKind.Space).effect)
    }

    @Test fun enterOnTheWelcomeStartActivatesIt() {
        assertEquals(
            FlowEffect.Activate("start"),
            welcome.press(FlowState(), KeyKind.Activate).effect
        )
    }

    // --- text entry stays with the IME ---

    @Test fun textKeysInATextFieldAreNotConsumed() {
        listOf(
            KeyKind.Printable,
            KeyKind.Space,
            KeyKind.Backspace,
            KeyKind.Left,
            KeyKind.Right
        ).forEach {
            val r = password.reduce(FlowState(), key(it, 'a'))
            assertFalse(it.name, r.consumed)
            assertEquals(FlowState(), r.state)
        }
    }

    @Test fun anEditableFlagAloneAlsoLeavesTextKeysToTheIme() {
        assertFalse(
            welcome.reduce(FlowState(), key(KeyKind.Printable, 'x', editable = true)).consumed
        )
    }

    @Test fun enterInTheLastTextFieldSubmitsThePrimaryAction() {
        assertEquals(
            FlowEffect.Submit("connect"),
            password.press(FlowState(), KeyKind.Activate).effect
        )
    }

    @Test fun enterInAnEarlierTextFieldMovesToTheNextTextField() {
        val r = account.press(FlowState(), KeyKind.Activate)
        assertEquals(FlowEffect.None, r.effect)
        assertEquals("account_password", account.focused(r.state)?.id)
        assertEquals(FlowEffect.Submit("sign_in"), account.press(r.state, KeyKind.Activate).effect)
    }

    @Test fun arrowsStillMoveBetweenFieldsAndButtons() {
        val s = password.press(FlowState(), KeyKind.Down).state
        assertEquals(ControlKind.Switch, password.focused(s)?.kind)
    }

    // --- Back / Escape ---

    @Test fun backAndEscapeGoBackWhereTheStepAllowsIt() {
        listOf(KeyKind.Back, KeyKind.Escape).forEach {
            assertEquals(FlowEffect.GoBack, password.press(FlowState(), it).effect)
        }
    }

    @Test fun backOnTheFirstStepIsLeftToTheSystem() {
        val r = welcome.press(FlowState(), KeyKind.Back)
        assertFalse(r.consumed)
        assertEquals(FlowEffect.None, r.effect)
    }

    // --- repeat ---

    @Test fun oneShotKeysIgnoreRepeatButArrowsRepeat() {
        listOf(
            KeyKind.Activate,
            KeyKind.Space,
            KeyKind.Back,
            KeyKind.Escape,
            KeyKind.Tab
        ).forEach {
            val r = welcome.reduce(FlowState(), key(it, repeat = true))
            assertEquals(it.name, FlowEffect.None, r.effect)
            assertEquals(it.name, FlowState(), r.state)
        }
        assertEquals(
            "language",
            welcome.focused(welcome.reduce(FlowState(), key(KeyKind.Down, repeat = true)).state)?.id
        )
    }

    // --- focus visibility and restoration ---

    @Test fun keysShowFocusAndTouchHidesIt() {
        val s = welcome.press(FlowState(), KeyKind.Down).state
        assertTrue(s.focusVisible)
        assertFalse(welcome.onTouch(s).focusVisible)
        assertEquals(welcome.focused(s)?.id, welcome.focused(welcome.onTouch(s))?.id)
    }

    @Test fun focusSurvivesRotationOrADialogByIdentity() {
        val s = wifi.press(FlowState(), KeyKind.Down).state
        assertEquals(s, wifi.restore(s))
        assertEquals("wifi:Cafe", wifi.focused(wifi.restore(s))?.id)
    }

    @Test fun focusFollowsTheNetworkWhenTheListChanges() {
        val s = wifi.press(FlowState(), KeyKind.Down).state
        val updated = FlowKeyModel(Steps.wifiList(listOf(WifiNetwork("New", true)) + networks))
        assertEquals("wifi:Cafe", updated.focused(s)?.id)
    }

    @Test fun focusFallsBackToTheNearestIndexWhenTheNetworkVanishes() {
        val s = wifi.press(FlowState(), KeyKind.Down).state
        val updated = FlowKeyModel(Steps.wifiList(listOf(WifiNetwork("Home", true))))
        assertEquals(Steps.ADD_NETWORK, updated.focused(s)?.id)
    }

    @Test fun otherKeysAndChordsAreNotConsumed() {
        assertFalse(welcome.reduce(FlowState(), key(KeyKind.Other)).consumed)
        assertFalse(
            welcome.reduce(FlowState(), KeyInput(KeyKind.Printable, 'a', ctrl = true)).consumed
        )
    }

    @Test fun anEmptyStepHasNoFocusAndNoCrash() {
        val m = FlowKeyModel(Step("empty", StepKind.Welcome, emptyList(), null))
        assertEquals(-1, m.focusIndex(FlowState()))
        assertNull(m.focused(FlowState()))
        KeyKind.values().forEach { m.reduce(FlowState(), key(it)) }
    }

    // --- MoveHome / MoveEnd (platform_sable normalized key contract) ---

    @Test fun moveHomeAndMoveEndFocusTheFirstAndLastEnabledControlOutsideAnEditor() {
        val s = wifi.press(FlowState(), KeyKind.MoveEnd).state
        assertEquals("wifi_skip", wifi.focused(s)?.id)
        assertEquals("wifi:Home", wifi.focused(wifi.press(s, KeyKind.MoveHome).state)?.id)
    }

    @Test fun moveEndSkipsDisabledControls() {
        val noSkip = FlowKeyModel(Steps.wifiList(networks, FlowPolicy(wifiSkippable = false)))
        assertEquals(
            "wifi:add",
            noSkip.focused(noSkip.press(FlowState(), KeyKind.MoveEnd).state)?.id
        )
    }

    @Test fun moveKeysDoNotActivateAnythingOrLeaveTheStep() {
        listOf(KeyKind.MoveHome, KeyKind.MoveEnd).forEach {
            val r = wifi.press(FlowState(), it)
            assertEquals(it.name, FlowEffect.None, r.effect)
            assertTrue(it.name, r.consumed)
        }
    }

    @Test fun moveKeysStayWithAFocusedTextFieldAndAnEditableFlag() {
        listOf(KeyKind.MoveHome, KeyKind.MoveEnd).forEach {
            val inField = password.reduce(FlowState(), key(it))
            assertFalse(it.name, inField.consumed)
            assertEquals(FlowState(), inField.state)
            val flagged = wifi.reduce(FlowState(), key(it, editable = true))
            assertFalse(it.name, flagged.consumed)
            assertEquals(FlowState(), flagged.state)
        }
    }

    @Test fun moveKeysWorkOnAStepWithTextFieldsOnceFocusIsOnAButton() {
        val onSwitch = password.press(FlowState(), KeyKind.Down).state
        assertEquals(
            "connect",
            password.focused(password.press(onSwitch, KeyKind.MoveEnd).state)?.id
        )
        assertEquals(
            "password",
            password.focused(password.press(onSwitch, KeyKind.MoveHome).state)?.id
        )
    }

    @Test fun movementKeysMayRepeat() {
        val s = wifi.reduce(FlowState(), key(KeyKind.MoveEnd, repeat = true)).state
        assertEquals("wifi_skip", wifi.focused(s)?.id)
    }

    @Test fun pageUpAndPageDownStayWithAnEditorAndAreOtherwiseUnhandled() {
        listOf(KeyKind.PageUp, KeyKind.PageDown).forEach {
            val inField = password.reduce(FlowState(), key(it))
            assertFalse(it.name, inField.consumed)
            assertEquals(FlowState(), inField.state)
            val flagged = wifi.reduce(FlowState(), key(it, editable = true))
            assertFalse(it.name, flagged.consumed)
            assertEquals(FlowState(), flagged.state)
            val outside = wifi.reduce(FlowState(), key(it))
            assertFalse(it.name, outside.consumed)
            assertEquals(FlowState(), outside.state)
            assertEquals(it.name, FlowEffect.None, outside.effect)
        }
    }

    @Test fun systemHomeAsOtherIsNotConsumedAndNeverMovesFocus() {
        val r = wifi.reduce(FlowState(), key(KeyKind.Other))
        assertFalse(r.consumed)
        assertEquals(FlowState(), r.state)
    }
}
