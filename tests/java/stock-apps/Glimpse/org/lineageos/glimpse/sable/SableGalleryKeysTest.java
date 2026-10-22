// Host tests for the Photos keyboard model (Glimpse patches 1021 and 1023). Run by tests/run.sh.
package org.lineageos.glimpse.sable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.lineageos.glimpse.sable.SableGalleryKeys.Action;

public class SableGalleryKeysTest {

    private static final int O = 43;
    private static final int G = 35;

    private static Action viewer(int keyCode, int ch) {
        return SableGalleryKeys.viewer(keyCode, ch, false, false, false, false, false, false);
    }

    private static Action viewerAlt(int keyCode, int ch) {
        return SableGalleryKeys.viewer(keyCode, ch, true, false, false, false, false, false);
    }

    @Test
    public void leftRightAndJkMove() {
        assertEquals(Action.PREVIOUS, viewer(SableGalleryKeys.KEYCODE_DPAD_LEFT, 0));
        assertEquals(Action.NEXT, viewer(SableGalleryKeys.KEYCODE_DPAD_RIGHT, 0));
        assertEquals(Action.PREVIOUS, viewer(SableGalleryKeys.KEYCODE_J, 'j'));
        assertEquals(Action.NEXT, viewer(SableGalleryKeys.KEYCODE_K, 'k'));
    }

    @Test
    public void arrowsAndSpaceStayWithAFocusedButton() {
        assertEquals(Action.NONE, SableGalleryKeys.viewer(
                SableGalleryKeys.KEYCODE_DPAD_LEFT, 0, false, false, false, false, true, false));
        assertEquals(Action.NONE, SableGalleryKeys.viewer(
                SableGalleryKeys.KEYCODE_SPACE, ' ', false, false, false, false, true, false));
        // Letters still work: the buttons do not use them.
        assertEquals(Action.INFO, SableGalleryKeys.viewer(
                SableGalleryKeys.KEYCODE_I, 'i', false, false, false, false, true, false));
    }

    @Test
    public void spacePlaysVideoAndPagesPhotos() {
        assertEquals(Action.PLAY_PAUSE, SableGalleryKeys.viewer(
                SableGalleryKeys.KEYCODE_SPACE, ' ', false, false, false, false, false, true));
        assertEquals(Action.NEXT, viewer(SableGalleryKeys.KEYCODE_SPACE, ' '));
        assertEquals(Action.PREVIOUS, SableGalleryKeys.viewer(
                SableGalleryKeys.KEYCODE_SPACE, ' ', false, false, true, false, false, false));
    }

    @Test
    public void plusAndMinusZoomAsTheQ25AltLayerTypesThem() {
        // Q25: Alt+O is "+", Alt+I is "-"; plain I stays Info.
        assertEquals(Action.ZOOM_IN, viewerAlt(O, '+'));
        assertEquals(Action.ZOOM_OUT, viewerAlt(SableGalleryKeys.KEYCODE_I, '-'));
        assertEquals(Action.INFO, viewer(SableGalleryKeys.KEYCODE_I, 'i'));
        // External keyboards.
        assertEquals(Action.ZOOM_IN, viewer(SableGalleryKeys.KEYCODE_EQUALS, '='));
        assertEquals(Action.ZOOM_IN, viewer(SableGalleryKeys.KEYCODE_NUMPAD_ADD, '+'));
        assertEquals(Action.ZOOM_OUT, viewer(SableGalleryKeys.KEYCODE_MINUS, '-'));
        assertEquals(Action.ZOOM_FIT, viewer(SableGalleryKeys.KEYCODE_0, '0'));
        // Other Alt symbols are not ours ("/" is Alt+G).
        assertEquals(Action.NONE, viewerAlt(G, '/'));
    }

    @Test
    public void actionKeys() {
        // S is search in MEDIA_GALLERY_UX, not share; share is E (export).
        assertEquals(Action.NONE, viewer(SableGalleryKeys.KEYCODE_S, 's'));
        assertEquals(Action.SHARE, viewer(SableGalleryKeys.KEYCODE_E, 'e'));
        assertEquals(Action.ZOOM_TOGGLE, viewer(SableGalleryKeys.KEYCODE_Z, 'z'));
        assertEquals(Action.FAVORITE, viewer(SableGalleryKeys.KEYCODE_F, 'f'));
        assertEquals(Action.DELETE, viewer(SableGalleryKeys.KEYCODE_D, 'd'));
        assertEquals(Action.DELETE, viewer(SableGalleryKeys.KEYCODE_DEL, 0));
        assertEquals(Action.DELETE, viewer(SableGalleryKeys.KEYCODE_FORWARD_DEL, 0));
    }

    @Test
    public void ctrlMetaAndShiftedLettersAreNotOurs() {
        assertEquals(Action.NONE, SableGalleryKeys.viewer(
                SableGalleryKeys.KEYCODE_S, 's', false, true, false, false, false, false));
        assertEquals(Action.NONE, SableGalleryKeys.viewer(
                SableGalleryKeys.KEYCODE_S, 's', false, false, false, true, false, false));
        assertEquals(Action.NONE, SableGalleryKeys.viewer(
                SableGalleryKeys.KEYCODE_D, 'D', false, false, true, false, false, false));
        assertEquals(Action.NONE, viewerAlt(SableGalleryKeys.KEYCODE_S, '4'));
    }

    @Test
    public void gridKeys() {
        assertEquals(Action.OPEN,
                SableGalleryKeys.grid(SableGalleryKeys.KEYCODE_SPACE, false, false, false, false, false));
        assertEquals(Action.DELETE,
                SableGalleryKeys.grid(SableGalleryKeys.KEYCODE_DEL, false, false, false, false, false));
        assertEquals(Action.DELETE,
                SableGalleryKeys.grid(SableGalleryKeys.KEYCODE_D, false, false, false, false, false));
        assertEquals(Action.NONE,
                SableGalleryKeys.grid(SableGalleryKeys.KEYCODE_S, false, false, false, false, false));
        assertEquals(Action.SHARE,
                SableGalleryKeys.grid(SableGalleryKeys.KEYCODE_E, false, false, false, false, false));
        assertEquals(Action.NONE,
                SableGalleryKeys.grid(SableGalleryKeys.KEYCODE_E, true, false, false, false, false));
        assertEquals(Action.PREVIOUS,
                SableGalleryKeys.grid(SableGalleryKeys.KEYCODE_J, false, false, false, false, false));
        assertEquals(Action.NEXT,
                SableGalleryKeys.grid(SableGalleryKeys.KEYCODE_K, false, false, false, false, true));
    }

    @Test
    public void captionStrips() {
        assertTrue(SableGalleryKeys.VIEWER_CAPTION.startsWith("[J Prev] [K Next] [Z Zoom] [D Del]"));
        assertEquals("IMG_2042 \u00b7 12:42\n" + SableGalleryKeys.GRID_CAPTION_KEYS,
                SableGalleryKeys.gridCaption("IMG_2042", "12:42"));
        assertTrue(SableGalleryKeys.gridCaption(null, null).startsWith("\u2014\n"));
        assertTrue(SableGalleryKeys.GRID_CAPTION_KEYS.contains("[J/K Prev/Next]"));
    }

    @Test
    public void pickingNeverDeletesOrShares() {
        assertEquals(Action.NONE,
                SableGalleryKeys.grid(SableGalleryKeys.KEYCODE_DEL, false, false, false, false, true));
        assertEquals(Action.NONE,
                SableGalleryKeys.grid(SableGalleryKeys.KEYCODE_E, false, false, false, false, true));
        assertEquals(Action.OPEN,
                SableGalleryKeys.grid(SableGalleryKeys.KEYCODE_SPACE, false, false, false, false, true));
    }

    @Test
    public void onlyMovingAndZoomingRepeat() {
        assertTrue(SableGalleryKeys.repeats(Action.NEXT));
        assertTrue(SableGalleryKeys.repeats(Action.ZOOM_IN));
        assertFalse(SableGalleryKeys.repeats(Action.DELETE));
        assertFalse(SableGalleryKeys.repeats(Action.SHARE));
        assertFalse(SableGalleryKeys.repeats(Action.PLAY_PAUSE));
    }

    @Test
    public void zoomStepsStayWithinBounds() {
        assertEquals(1.5f, SableGalleryKeys.zoomStep(1f, 1f, 8f, true));
        assertEquals(1f, SableGalleryKeys.zoomStep(1.2f, 1f, 8f, false));
        assertEquals(8f, SableGalleryKeys.zoomStep(7f, 1f, 8f, true));
        assertEquals(1f, SableGalleryKeys.zoomStep(1f, 1f, 8f, false));
        // Unknown current scale starts from the minimum; unusable bounds change nothing.
        assertEquals(1.5f, SableGalleryKeys.zoomStep(0f, 1f, 8f, true));
        assertEquals(2f, SableGalleryKeys.zoomStep(2f, 0f, 8f, true));
        assertEquals(2f, SableGalleryKeys.zoomStep(2f, 3f, 1f, true));
    }
}
