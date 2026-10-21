// Host tests for the Files keyboard model (DocumentsUI patches 1001 and 1003). Run by tests/run.sh.
package com.android.documentsui.sable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.documentsui.sable.SableFilesKeys.Action;
import org.junit.Test;

public class SableFilesKeysTest {

    private static final int G = 35;
    private static final int W = 51;

    /** A key with Alt held, nothing typing, a focused file. */
    private static Action alt(int keyCode, int ch) {
        return SableFilesKeys.decide(keyCode, ch, true, false, false, false, false, false, true);
    }

    private static Action plain(int keyCode, int ch, boolean hasTarget) {
        return SableFilesKeys.decide(keyCode, ch, false, false, false, false, false, false, hasTarget);
    }

    @Test
    public void altCommands() {
        // The Q25 Alt layer types 9 . 3 , - + * for these keys; the command wins outside a field.
        assertEquals(Action.COPY_TO, alt(SableFilesKeys.KEYCODE_C, '9'));
        assertEquals(Action.MOVE_TO, alt(SableFilesKeys.KEYCODE_M, '.'));
        assertEquals(Action.RENAME, alt(SableFilesKeys.KEYCODE_R, '3'));
        assertEquals(Action.NEW_FOLDER, alt(SableFilesKeys.KEYCODE_N, ','));
        assertEquals(Action.INFO, alt(SableFilesKeys.KEYCODE_I, '-'));
        assertEquals(Action.OPEN_WITH, alt(SableFilesKeys.KEYCODE_O, '+'));
        assertEquals(Action.SELECT_ALL, alt(SableFilesKeys.KEYCODE_A, '*'));
    }

    @Test
    public void slashSearchesAlsoAsAltG() {
        assertEquals(Action.SEARCH, alt(G, '/'));
        assertEquals(Action.SEARCH, plain(SableFilesKeys.KEYCODE_SLASH, '/', false));
    }

    @Test
    public void otherAltKeysKeepTypeToFocus() {
        // Alt+W types "1": it starts a type-to-focus search for names beginning with 1.
        assertEquals(Action.UPSTREAM, alt(W, '1'));
    }

    @Test
    public void designSingleLettersOnAFocusedFile() {
        assertEquals(Action.COPY_TO, plain(SableFilesKeys.KEYCODE_C, 'c', true));
        assertEquals(Action.MOVE_TO, plain(SableFilesKeys.KEYCODE_M, 'm', true));
        assertEquals(Action.RENAME, plain(SableFilesKeys.KEYCODE_R, 'r', true));
        assertEquals(Action.INFO, plain(SableFilesKeys.KEYCODE_I, 'i', true));
        assertEquals(Action.OPEN_WITH, plain(SableFilesKeys.KEYCODE_O, 'o', true));
        assertEquals(Action.EXPORT, plain(SableFilesKeys.KEYCODE_E, 'e', true));
        assertEquals(Action.DELETE, plain(SableFilesKeys.KEYCODE_D, 'd', true));
    }

    @Test
    public void designSingleLettersAnywhere() {
        assertEquals(Action.NEXT, plain(SableFilesKeys.KEYCODE_J, 'j', false));
        assertEquals(Action.PREVIOUS, plain(SableFilesKeys.KEYCODE_K, 'k', false));
        assertEquals(Action.LATEST, plain(SableFilesKeys.KEYCODE_T, 't', false));
        assertEquals(Action.VIEW_MODE, plain(SableFilesKeys.KEYCODE_V, 'v', false));
        assertEquals(Action.SELECT_ALL, plain(SableFilesKeys.KEYCODE_A, 'a', false));
        assertEquals(Action.SEARCH, plain(SableFilesKeys.KEYCODE_S, 's', false));
    }

    @Test
    public void fileLettersWithoutATargetKeepTypeToFocus() {
        assertEquals(Action.UPSTREAM, plain(SableFilesKeys.KEYCODE_C, 'c', false));
        assertEquals(Action.UPSTREAM, plain(SableFilesKeys.KEYCODE_D, 'd', false));
        // F (favorite) is not bound: the Files app has no favorites. Other letters type-to-focus.
        assertEquals(Action.UPSTREAM, plain(34, 'f', true));
        assertEquals(Action.UPSTREAM, plain(W, 'w', true));
        // N alone is a name letter; Alt+N is new folder.
        assertEquals(Action.UPSTREAM, plain(SableFilesKeys.KEYCODE_N, 'n', true));
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                SableFilesKeys.KEYCODE_J, 'J', false, false, true, false, false, false, true));
    }

    @Test
    public void onlyMovingRepeats() {
        assertTrue(SableFilesKeys.repeats(Action.NEXT));
        assertTrue(SableFilesKeys.repeats(Action.PREVIOUS));
        assertFalse(SableFilesKeys.repeats(Action.DELETE));
        assertFalse(SableFilesKeys.repeats(Action.COPY_TO));
    }

    @Test
    public void dockText() {
        for (String key : new String[] {"Enter Open", "J/K Move", "T Latest", "E Export",
                "O Open with"}) {
            assertTrue(key, SableFilesKeys.DOCK_HINT.contains(key));
        }
        assertEquals("\u25b8 plan.pdf \u00b7 PDF document \u00b7 1.2 MB",
                SableFilesKeys.focusLine("plan.pdf", "PDF document", "1.2 MB", " "));
        assertEquals("\u25b8 \u2014", SableFilesKeys.focusLine(null, null, null, null));
    }

    @Test
    public void runningTypeToFocusOwnsEveryKey() {
        // "2024": Alt+E then Alt+0... once it runs, Alt+C is "9", not Copy to; Del edits it.
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                SableFilesKeys.KEYCODE_C, '9', true, false, false, false, false, true, true));
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                SableFilesKeys.KEYCODE_DEL, 0, false, false, false, false, false, true, true));
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                G, '/', true, false, false, false, false, true, true));
    }

    @Test
    public void textInputAlwaysWins() {
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                SableFilesKeys.KEYCODE_R, '3', true, false, false, false, true, false, true));
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                SableFilesKeys.KEYCODE_DEL, 0, false, false, false, false, true, false, true));
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                G, '/', true, false, false, false, true, false, true));
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                SableFilesKeys.KEYCODE_D, 'd', false, false, false, false, true, false, true));
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                SableFilesKeys.KEYCODE_J, 'j', false, false, false, false, false, true, true));
    }

    @Test
    public void delDeletesATargetElseGoesUp() {
        assertEquals(Action.DELETE, plain(SableFilesKeys.KEYCODE_DEL, 0, true));
        assertEquals(Action.UPSTREAM, plain(SableFilesKeys.KEYCODE_DEL, 0, false));
        // Alt+Del is upstream's own delete; Shift+Del is not ours.
        assertEquals(Action.UPSTREAM, alt(SableFilesKeys.KEYCODE_DEL, 0));
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                SableFilesKeys.KEYCODE_DEL, 0, false, false, true, false, false, false, true));
    }

    @Test
    public void ctrlMetaAndShiftChordsKeepUpstream() {
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                SableFilesKeys.KEYCODE_C, 'c', false, true, false, false, false, false, true));
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                SableFilesKeys.KEYCODE_C, 'c', false, false, false, true, false, false, true));
        assertEquals(Action.UPSTREAM, SableFilesKeys.decide(
                SableFilesKeys.KEYCODE_C, '9', true, false, true, false, false, false, true));
    }
}
