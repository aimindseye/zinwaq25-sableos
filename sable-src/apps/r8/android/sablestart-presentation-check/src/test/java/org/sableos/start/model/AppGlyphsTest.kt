package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One tile-free glyph per shipped app, chosen by package only (Q4 device review, Q7). */
class AppGlyphsTest {
    @Test
    fun everyShippedSableAppHasAGlyph() {
        // product/q25/apps.tsv packages (enabled and disabled rows).
        val sable =
            listOf(
                "org.sableos.titan2.keyboard",
                "org.sableos.titan2.camera",
                "org.sableos.titan2.displaycompat",
                "org.sableos.titan2.setup",
                "org.sableos.titan2.vault",
                "org.sableos.titan2.radiodiag",
                "org.sableos.tools",
                "org.sableos.calculator",
                "org.sableos.sudoku",
                "org.sableos.minesweeper",
                "org.sableos.game2048",
                "org.sableos.bricks",
                "org.sableos.snake",
                "org.sableos.blocks",
                "org.sableos.reversi",
                "org.sableos.solitaire",
                "org.sableos.mahjong",
                "org.sableos.words",
                "org.sableos.media",
                "org.sableos.hub",
                "org.sableos.weather",
                "org.sableos.calendar",
                "org.sableos.messages",
                "org.sableos.reader",
                "org.sableos.mail",
                "org.sableos.textreader",
            )
        sable.forEach { assertTrue(it, AppGlyphs.forPackage(it) != null) }
    }

    @Test
    fun keptLineageAppsMapByPackage() {
        assertEquals(AppGlyph.PHONE, AppGlyphs.forPackage("com.android.dialer"))
        assertEquals(AppGlyph.CONTACTS, AppGlyphs.forPackage("com.android.contacts"))
        assertEquals(AppGlyph.SETTINGS, AppGlyphs.forPackage("com.android.settings"))
        assertEquals(AppGlyph.PHOTOS, AppGlyphs.forPackage("org.lineageos.glimpse"))
        assertEquals(AppGlyph.BROWSER, AppGlyphs.forPackage("org.lineageos.jelly"))
        assertEquals(AppGlyph.FILES, AppGlyphs.forPackage("com.android.documentsui"))
        assertEquals(AppGlyph.CLOCK, AppGlyphs.forPackage("com.android.deskclock"))
        assertEquals(AppGlyph.RECORDER, AppGlyphs.forPackage("org.lineageos.recorder"))
        assertEquals(AppGlyph.SERVICES, AppGlyphs.forPackage("com.google.android.gms"))
        assertEquals(AppGlyph.STORE, AppGlyphs.forPackage("com.aurora.store"))
    }

    @Test
    fun sableCameraAndTheBaseCameraLookDifferent() {
        assertEquals(AppGlyph.CAMERA, AppGlyphs.forPackage(CoreAppIdentity.SABLE_CAMERA))
        assertEquals(AppGlyph.VIDEO_CAMERA, AppGlyphs.forPackage("org.lineageos.aperture"))
    }

    @Test
    fun otherAppsDrawTheirOwnIcon() {
        // LineageOS Calendar (Etar) is not Sable Calendar, whatever its label says.
        assertNull(AppGlyphs.forPackage("org.lineageos.etar"))
        assertNull(AppGlyphs.forPackage("org.lineageos.twelve"))
        assertNull(AppGlyphs.forPackage("com.example.chat"))
    }

    @Test
    fun glyphsAreDistinctPhosphorGlyphsWithOpaqueAccents() {
        val names = AppGlyph.entries.map { it.phosphor }
        assertEquals(AppGlyph.entries.size, names.toSet().size)
        AppGlyph.entries.forEach { assertEquals(it.name, 0xFF000000, it.accent and 0xFF000000) }
    }
}
