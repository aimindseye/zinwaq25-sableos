// Host tests for the Browser keyboard model (Jelly patch 1041). Run by tests/run.sh.
package org.lineageos.jelly.sable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.lineageos.jelly.sable.SableBrowserKeys.Action;

public class SableBrowserKeysTest {

    private static final int G = 35;
    private static final int A = 29;

    private static Action before(int keyCode, boolean alt, boolean ctrl, boolean editing) {
        return SableBrowserKeys.beforePage(keyCode, alt, ctrl, false, false, editing);
    }

    private static Action after(int keyCode, int ch, boolean alt, boolean shift, boolean editing) {
        return SableBrowserKeys.afterPage(keyCode, ch, alt, false, shift, false, editing);
    }

    @Test
    public void altChordsBeforeThePage() {
        assertEquals(Action.FOCUS_ADDRESS, before(SableBrowserKeys.KEYCODE_L, true, false, false));
        assertEquals(Action.NEW_TAB, before(SableBrowserKeys.KEYCODE_T, true, false, false));
        assertEquals(Action.FIND, before(SableBrowserKeys.KEYCODE_F, true, false, false));
        assertEquals(Action.RELOAD, before(SableBrowserKeys.KEYCODE_R, true, false, false));
        assertEquals(Action.BACK, before(SableBrowserKeys.KEYCODE_DPAD_LEFT, true, false, false));
        assertEquals(Action.FORWARD,
                before(SableBrowserKeys.KEYCODE_DPAD_RIGHT, true, false, false));
    }

    @Test
    public void ctrlWorksLikeAltOnAnExternalKeyboard() {
        assertEquals(Action.FOCUS_ADDRESS, before(SableBrowserKeys.KEYCODE_L, false, true, false));
        assertEquals(Action.NONE, before(SableBrowserKeys.KEYCODE_L, true, true, false));
    }

    @Test
    public void plainKeysNeverOverrideThePage() {
        // Before the page only chords count: the page gets every plain key first.
        assertEquals(Action.NONE, before(SableBrowserKeys.KEYCODE_L, false, false, false));
        assertEquals(Action.NONE, before(SableBrowserKeys.KEYCODE_SPACE, false, false, false));
        assertEquals(Action.NONE, SableBrowserKeys.beforePage(
                SableBrowserKeys.KEYCODE_L, true, false, true, false, false));
        assertEquals(Action.NONE, SableBrowserKeys.beforePage(
                SableBrowserKeys.KEYCODE_L, true, false, false, true, false));
    }

    @Test
    public void textInputAlwaysWins() {
        // In a field Alt+letter types the letter's symbol, and plain keys are text.
        assertEquals(Action.NONE, before(SableBrowserKeys.KEYCODE_L, true, false, true));
        assertEquals(Action.NONE, before(SableBrowserKeys.KEYCODE_DPAD_LEFT, true, false, true));
        assertEquals(Action.NONE, before(SableBrowserKeys.KEYCODE_L, false, true, true));
        assertEquals(Action.NONE, after(SableBrowserKeys.KEYCODE_L, 'l', false, false, true));
        assertEquals(Action.NONE, after(SableBrowserKeys.KEYCODE_SPACE, ' ', false, false, true));
        assertEquals(Action.NONE, after(SableBrowserKeys.KEYCODE_B, 'b', false, false, true));
        assertEquals(Action.NONE, after(G, '/', true, false, true));
    }

    @Test
    public void singleKeysOnceThePageDeclined() {
        assertEquals(Action.FOCUS_ADDRESS,
                after(SableBrowserKeys.KEYCODE_L, 'l', false, false, false));
        assertEquals(Action.NEW_TAB, after(SableBrowserKeys.KEYCODE_T, 't', false, false, false));
        assertEquals(Action.FIND, after(SableBrowserKeys.KEYCODE_F, 'f', false, false, false));
        assertEquals(Action.BACK, after(SableBrowserKeys.KEYCODE_B, 'b', false, false, false));
        assertEquals(Action.TOP, after(SableBrowserKeys.KEYCODE_MOVE_HOME, 0, false, false, false));
        assertEquals(Action.BOTTOM,
                after(SableBrowserKeys.KEYCODE_MOVE_END, 0, false, false, false));
    }

    @Test
    public void spacePagesDownAndShiftSpaceUp() {
        assertEquals(Action.PAGE_DOWN,
                after(SableBrowserKeys.KEYCODE_SPACE, ' ', false, false, false));
        assertEquals(Action.PAGE_UP, after(SableBrowserKeys.KEYCODE_SPACE, ' ', false, true, false));
    }

    @Test
    public void slashFindsAlsoAsAltG() {
        // On the Q25 "/" is Alt+G: the character decides, not the key.
        assertEquals(Action.FIND, after(G, '/', true, false, false));
        assertEquals(Action.FIND, after(SableBrowserKeys.KEYCODE_SLASH, '/', false, false, false));
        // Other Alt keys type symbols the page did not want: still not ours.
        assertEquals(Action.NONE, after(SableBrowserKeys.KEYCODE_L, '"', true, false, false));
    }

    @Test
    public void shiftedLettersAndOtherKeysAreNotOurs() {
        assertEquals(Action.NONE, after(SableBrowserKeys.KEYCODE_L, 'L', false, true, false));
        assertEquals(Action.NONE, after(A, 'a', false, false, false));
        assertEquals(Action.NONE, SableBrowserKeys.afterPage(
                SableBrowserKeys.KEYCODE_L, 'l', false, true, false, false, false));
        assertEquals(Action.NONE, SableBrowserKeys.afterPage(
                SableBrowserKeys.KEYCODE_L, 'l', false, false, false, true, false));
    }

    @Test
    public void onlyPagingRepeats() {
        assertTrue(SableBrowserKeys.repeats(Action.PAGE_DOWN));
        assertTrue(SableBrowserKeys.repeats(Action.PAGE_UP));
        for (Action a : new Action[] {Action.NEW_TAB, Action.BACK, Action.FIND, Action.RELOAD,
                Action.FOCUS_ADDRESS, Action.TOP, Action.BOTTOM, Action.FORWARD}) {
            assertFalse(a.name(), SableBrowserKeys.repeats(a));
        }
    }
}
