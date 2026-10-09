package org.lineageos.setupwizard.sable

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lineageos.setupwizard.sable.SableKeyPolicy.Candidate
import org.lineageos.setupwizard.sable.SableKeyPolicy.Decision
import org.lineageos.setupwizard.sable.SableKeyPolicy.Focus
import org.lineageos.setupwizard.sable.SableKeyPolicy.Kind

/** Pure tests for the keyboard-first setup policy carried in patches/framework/packages/apps/SetupWizard/0401. */
class SableKeyPolicyTest {
    private fun decide(
        kind: Kind,
        focus: Focus,
        cmd: Boolean = false,
        repeat: Boolean = false,
        lastText: Boolean = false,
        primary: Boolean = true,
    ): Decision = SableKeyPolicy.decide(kind, focus, cmd, repeat, lastText, primary)

    @Test fun keyCodesMapToTheNormalizedVocabulary() {
        assertEquals(Kind.ACTIVATE, SableKeyPolicy.kindOf(66, 0))
        assertEquals(Kind.ACTIVATE, SableKeyPolicy.kindOf(160, 0))
        assertEquals(Kind.ACTIVATE, SableKeyPolicy.kindOf(23, 0))
        assertEquals(Kind.MOVE_HOME, SableKeyPolicy.kindOf(122, 0))
        assertEquals(Kind.MOVE_END, SableKeyPolicy.kindOf(123, 0))
        assertEquals(Kind.OTHER, SableKeyPolicy.kindOf(3, 0))
        assertEquals(Kind.ESCAPE, SableKeyPolicy.kindOf(111, 0))
        assertEquals(Kind.PRINTABLE, SableKeyPolicy.kindOf(29, 'a'.code))
        assertEquals(Kind.OTHER, SableKeyPolicy.kindOf(29, 0))
        assertEquals(Kind.OTHER, SableKeyPolicy.kindOf(1000, 0x80000041.toInt()))
    }

    @Test fun theFirstNavigationKeyWithNothingFocusedShowsFocusInsteadOfActing() {
        listOf(Kind.UP, Kind.DOWN, Kind.LEFT, Kind.RIGHT, Kind.TAB, Kind.ACTIVATE, Kind.SPACE).forEach {
            assertEquals(it.name, Decision.FOCUS_INITIAL, decide(it, Focus.NONE))
        }
    }

    @Test fun moveHomeAndMoveEndJumpToTheEdgesOutsideTextFields() {
        assertEquals(Decision.FOCUS_FIRST, decide(Kind.MOVE_HOME, Focus.NONE))
        assertEquals(Decision.FOCUS_LAST, decide(Kind.MOVE_END, Focus.CONTROL))
        assertEquals(Decision.PASS, decide(Kind.MOVE_HOME, Focus.TEXT))
        assertEquals(Decision.PASS, decide(Kind.MOVE_END, Focus.TEXT))
    }

    @Test fun platformKeysAreNeverClaimed() {
        listOf(Kind.OTHER, Kind.PAGE_UP, Kind.PAGE_DOWN, Kind.BACK, Kind.ESCAPE).forEach { k ->
            Focus.values().forEach { f ->
                assertEquals("$k/$f", Decision.PASS, decide(k, f))
                assertEquals("$k/$f repeat", Decision.PASS, decide(k, f, repeat = true))
            }
        }
    }

    @Test fun commandShortcutsAreNeverClaimed() {
        Kind.values().forEach { k ->
            Focus.values().forEach { f -> assertEquals("$k/$f", Decision.PASS, decide(k, f, cmd = true)) }
        }
        assertEquals(true, SableKeyPolicy.hasCommandModifier(0x1000))
        assertEquals(true, SableKeyPolicy.hasCommandModifier(0x10000))
        assertEquals(false, SableKeyPolicy.hasCommandModifier(0x1))
    }

    @Test fun focusedControlsKeepPlatformNavigationAndActivation() {
        listOf(Kind.UP, Kind.DOWN, Kind.LEFT, Kind.RIGHT, Kind.TAB, Kind.ACTIVATE, Kind.SPACE, Kind.PRINTABLE).forEach {
            assertEquals(it.name, Decision.PASS, decide(it, Focus.CONTROL))
        }
    }

    @Test fun aHeldConfirmKeyDoesNotRepeatTheAction() {
        listOf(Kind.ACTIVATE, Kind.SPACE, Kind.TAB).forEach {
            assertEquals(it.name, Decision.CONSUME, decide(it, Focus.CONTROL, repeat = true))
        }
        assertEquals(Decision.CONSUME, decide(Kind.ACTIVATE, Focus.TEXT, repeat = true))
        assertEquals(Decision.PASS, decide(Kind.DOWN, Focus.CONTROL, repeat = true))
    }

    @Test fun textKeysStayWithTheFieldAndTheIme() {
        listOf(Kind.PRINTABLE, Kind.SPACE, Kind.BACKSPACE, Kind.LEFT, Kind.RIGHT, Kind.UP, Kind.DOWN, Kind.TAB).forEach {
            assertEquals(it.name, Decision.PASS, decide(it, Focus.TEXT))
            assertEquals(it.name, Decision.PASS, decide(it, Focus.TEXT, lastText = true))
        }
    }

    @Test fun enterInTheLastTextFieldSubmitsThePrimaryAction() {
        assertEquals(Decision.SUBMIT_PRIMARY, decide(Kind.ACTIVATE, Focus.TEXT, lastText = true))
        assertEquals(Decision.PASS, decide(Kind.ACTIVATE, Focus.TEXT, lastText = false))
        assertEquals(Decision.PASS, decide(Kind.ACTIVATE, Focus.TEXT, lastText = true, primary = false))
    }

    @Test fun initialFocusIsTheFirstTextFieldThenThePrimaryThenTheFirstControl() {
        assertEquals(1, SableKeyPolicy.initialIndex(listOf(Candidate.CONTROL, Candidate.TEXT, Candidate.PRIMARY)))
        assertEquals(2, SableKeyPolicy.initialIndex(listOf(Candidate.CONTROL, Candidate.CONTROL, Candidate.PRIMARY)))
        assertEquals(0, SableKeyPolicy.initialIndex(listOf(Candidate.CONTROL, Candidate.CONTROL)))
        assertEquals(-1, SableKeyPolicy.initialIndex(emptyList()))
    }

    @Test fun noKeyEverDeadEndsAStepWhatever() {
        // Every decision is either pass-through or a focus/primary action the host can perform:
        // Back/Escape always pass, so leaving a step backwards is always possible.
        Kind.values().forEach { k ->
            Focus.values().forEach { f ->
                listOf(false, true).forEach { r ->
                    val d = decide(k, f, repeat = r)
                    if (k == Kind.BACK || k == Kind.ESCAPE) assertEquals(Decision.PASS, d)
                }
            }
        }
    }
}
