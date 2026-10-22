// Host tests for the Browser keyboard model (Jelly patches 1041 and 1043). Run by tests/run.sh.
package org.lineageos.jelly.sable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.lineageos.jelly.sable.SableBrowserKeys.Action;
import org.lineageos.jelly.sable.SableBrowserKeys.Mode;

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
    public void tabChords() {
        // Design: Ctrl+W close, Ctrl+Tab / Ctrl+Shift+Tab next / previous; Alt on the Q25.
        assertEquals(Action.CLOSE_TAB, before(SableBrowserKeys.KEYCODE_W, true, false, false));
        assertEquals(Action.CLOSE_TAB, before(SableBrowserKeys.KEYCODE_W, false, true, false));
        assertEquals(Action.NEXT_TAB, before(SableBrowserKeys.KEYCODE_K, true, false, false));
        assertEquals(Action.PREVIOUS_TAB, before(SableBrowserKeys.KEYCODE_J, true, false, false));
        assertEquals(Action.TAB_GRID, before(A, true, false, false));
        assertEquals(Action.NEXT_TAB, SableBrowserKeys.beforePage(
                SableBrowserKeys.KEYCODE_TAB, false, true, false, false, false));
        assertEquals(Action.PREVIOUS_TAB, SableBrowserKeys.beforePage(
                SableBrowserKeys.KEYCODE_TAB, false, true, true, false, false));
        // Alt+Tab is the system's; plain Tab moves focus in the page.
        assertEquals(Action.NONE, before(SableBrowserKeys.KEYCODE_TAB, true, false, false));
        assertEquals(Action.NONE, before(SableBrowserKeys.KEYCODE_TAB, false, false, false));
        // Never while typing.
        assertEquals(Action.NONE, before(SableBrowserKeys.KEYCODE_W, true, false, true));
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
        assertEquals(Action.NONE, after(SableBrowserKeys.KEYCODE_R, 'r', false, false, true));
        assertEquals(Action.NONE, after(SableBrowserKeys.KEYCODE_T, 't', false, false, true));
        assertEquals(Action.NONE, after(G, '/', true, false, true));
    }

    @Test
    public void designSingleKeysOnceThePageDeclined() {
        // BROWSER_WEBVIEW_UX: T top, B bottom, L link mode, R reader, / find.
        assertEquals(Action.TOP, after(SableBrowserKeys.KEYCODE_T, 't', false, false, false));
        assertEquals(Action.BOTTOM, after(SableBrowserKeys.KEYCODE_B, 'b', false, false, false));
        assertEquals(Action.LINK_NEXT, after(SableBrowserKeys.KEYCODE_L, 'l', false, false, false));
        assertEquals(Action.LINK_PREVIOUS,
                after(SableBrowserKeys.KEYCODE_L, 'L', false, true, false));
        assertEquals(Action.READER, after(SableBrowserKeys.KEYCODE_R, 'r', false, false, false));
        assertEquals(Action.FIND, after(SableBrowserKeys.KEYCODE_F, 'f', false, false, false));
        assertEquals(Action.TOP, after(SableBrowserKeys.KEYCODE_MOVE_HOME, 0, false, false, false));
        assertEquals(Action.BOTTOM,
                after(SableBrowserKeys.KEYCODE_MOVE_END, 0, false, false, false));
    }

    @Test
    public void addressAndNewTabAreChordsOnly() {
        // The old single-key meanings (L address, T new tab, B back) are gone.
        for (int key : new int[] {SableBrowserKeys.KEYCODE_L, SableBrowserKeys.KEYCODE_T,
                SableBrowserKeys.KEYCODE_B}) {
            Action a = after(key, 0, false, false, false);
            assertFalse(a == Action.FOCUS_ADDRESS || a == Action.NEW_TAB || a == Action.BACK);
        }
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
        assertEquals(Action.NONE, after(SableBrowserKeys.KEYCODE_T, 'T', false, true, false));
        assertEquals(Action.NONE, after(A, 'a', false, false, false));
        assertEquals(Action.NONE, SableBrowserKeys.afterPage(
                SableBrowserKeys.KEYCODE_L, 'l', false, true, false, false, false));
        assertEquals(Action.NONE, SableBrowserKeys.afterPage(
                SableBrowserKeys.KEYCODE_L, 'l', false, false, false, true, false));
    }

    @Test
    public void tabGridKeys() {
        assertEquals(Action.CLOSE_TAB, SableBrowserKeys.tabGrid(32, false, false, false, false));
        assertEquals(Action.CLOSE_TAB,
                SableBrowserKeys.tabGrid(SableBrowserKeys.KEYCODE_DEL, false, false, false, false));
        assertEquals(Action.CLOSE_TAB,
                SableBrowserKeys.tabGrid(SableBrowserKeys.KEYCODE_W, true, false, false, false));
        assertEquals(Action.NONE,
                SableBrowserKeys.tabGrid(SableBrowserKeys.KEYCODE_W, false, false, false, false));
        assertEquals(Action.NONE, SableBrowserKeys.tabGrid(32, false, false, true, false));
        assertEquals(Action.NONE, SableBrowserKeys.tabGrid(66, false, false, false, false));
    }

    @Test
    public void adjacentTabWrapsInOpeningOrder() {
        int[] tabs = {12, 5, 9};
        assertEquals(9, SableBrowserKeys.adjacentTab(tabs, 5, true));
        assertEquals(12, SableBrowserKeys.adjacentTab(tabs, 9, true));
        assertEquals(5, SableBrowserKeys.adjacentTab(tabs, 12, true));
        assertEquals(12, SableBrowserKeys.adjacentTab(tabs, 5, false));
        assertEquals(-1, SableBrowserKeys.adjacentTab(new int[] {5}, 5, true));
        assertEquals(-1, SableBrowserKeys.adjacentTab(null, 5, true));
        // Current tab missing from the list: step from where it would sit.
        assertEquals(9, SableBrowserKeys.adjacentTab(tabs, 7, true));
        assertEquals(5, SableBrowserKeys.adjacentTab(tabs, 7, false));
    }

    @Test
    public void dockHints() {
        assertTrue(SableBrowserKeys.hint(Mode.START, 0, 0).contains("[Alt+T New]"));
        assertTrue(SableBrowserKeys.hint(Mode.START, 0, 0).contains("[Alt+W Close]"));
        assertTrue(SableBrowserKeys.hint(Mode.PAGE, 0, 0).contains("[R] reader"));
        assertTrue(SableBrowserKeys.hint(Mode.READER, 2, 8).startsWith("Reader 2/8"));
        assertTrue(SableBrowserKeys.hint(Mode.READER, 9, 8).startsWith("Reader 8/8"));
        assertTrue(SableBrowserKeys.hint(Mode.READER, 0, 0).contains("[T] top"));
        assertTrue(SableBrowserKeys.hint(Mode.READER, 0, 0).contains("[L] link mode"));
    }

    @Test
    public void onlyPagingAndLinkMovesRepeat() {
        assertTrue(SableBrowserKeys.repeats(Action.PAGE_DOWN));
        assertTrue(SableBrowserKeys.repeats(Action.PAGE_UP));
        assertTrue(SableBrowserKeys.repeats(Action.LINK_NEXT));
        for (Action a : new Action[] {Action.NEW_TAB, Action.CLOSE_TAB, Action.NEXT_TAB,
                Action.TAB_GRID, Action.READER, Action.BACK, Action.FIND, Action.RELOAD,
                Action.FOCUS_ADDRESS, Action.TOP, Action.BOTTOM, Action.FORWARD}) {
            assertFalse(a.name(), SableBrowserKeys.repeats(a));
        }
    }
}
