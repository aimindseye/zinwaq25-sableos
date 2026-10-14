// Host tests for the Files keyboard model (DocumentsUI patch 1001). Run by tests/run.sh.
package com.android.documentsui.sable;

import static org.junit.Assert.assertEquals;

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
    public void lettersKeepTypeToFocus() {
        assertEquals(Action.UPSTREAM, plain(SableFilesKeys.KEYCODE_C, 'c', true));
        assertEquals(Action.UPSTREAM, plain(SableFilesKeys.KEYCODE_R, 'r', true));
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
