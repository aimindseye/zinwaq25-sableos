package org.sableos.start.model

/**
 * One Start glyph per app the Q25 image ships (Q4 device review, icon style
 * Q7: one tile-free glyph set, Ripe-like). The glyph geometry is Phosphor
 * regular at the commit pinned in third_party/phosphor-icons (drawn in
 * ui/SableGlyphIcon.kt); [accent] tints the glyph's lower half and matches the
 * app's own launcher icon accent (ICON_MANIFEST.tsv) for Sable apps.
 */
enum class AppGlyph(
    /** The Phosphor regular glyph name, as in third_party/phosphor-icons. */
    val phosphor: String,
    /** ARGB accent. */
    val accent: Long,
) {
    PHONE("phone-call", 0xFF4D9CFF),
    CONTACTS("address-book", 0xFFF28C45),
    MESSAGES("chat-circle-dots", 0xFF35C66B),
    MAIL("envelope-simple", 0xFF64748B),
    CALENDAR("calendar-dots", 0xFFF28C45),
    WEATHER("cloud-sun", 0xFF4D9CFF),
    PHOTOS("image-square", 0xFF7A42E8),
    MEDIA("music-notes", 0xFF7A42E8),
    CAMERA("camera", 0xFFE8458F),

    /** The base camera beside Sable Camera: Aperture keeps video and the secure camera on the Q25. */
    VIDEO_CAMERA("video-camera", 0xFFE8458F),
    CALCULATOR("calculator", 0xFF4D9CFF),
    FILES("folder-simple", 0xFFF5C542),
    CLOCK("clock", 0xFF64748B),
    BROWSER("globe", 0xFF4D9CFF),
    SETTINGS("gear-six", 0xFF64748B),
    RECORDER("microphone", 0xFFE5534B),
    READER("book-open-text", 0xFF35C66B),
    TEXT_READER("text-align-left", 0xFF4D9CFF),
    NOTES("notepad", 0xFFF5C542),
    SUDOKU("grid-nine", 0xFF4D9CFF),
    MINESWEEPER("bomb", 0xFFF28C45),
    GAME_2048("grid-four", 0xFF64748B),
    BRICKS("wall", 0xFFE34D59),
    SNAKE("path", 0xFF35C66B),
    BLOCKS("squares-four", 0xFF9A63E8),
    REVERSI("circle-half", 0xFF94A3B8),
    SOLITAIRE("cards", 0xFFD32F45),
    MAHJONG("stack", 0xFF2E8B57),
    WORDS("text-aa", 0xFFD9A441),
    VAULT("vault", 0xFF8B5CF6),
    TOOLS("wrench", 0xFFE5534B),
    RADIO("cell-signal-high", 0xFFE5534B),
    KEYBOARD("keyboard", 0xFFE8B931),
    DISPLAY("frame-corners", 0xFF34C8E8),
    SETUP("list-checks", 0xFF2BB5A0),
    STORE("shopping-bag", 0xFF35C66B),
    SERVICES("puzzle-piece", 0xFF4D9CFF),
}

/**
 * Package -> glyph, by package only: a label never picks a glyph, so the
 * LineageOS "Calendar" (Etar) can no longer borrow Sable Calendar's mark.
 * Apps not listed here draw their own icon ([AppGlyph] style: their themed
 * monochrome layer when they have one, else the icon itself, no tile).
 *
 * Pure Kotlin; unit tested in apps/r8/android/sablestart-presentation-check.
 */
object AppGlyphs {
    /** Canonical packages ([CoreAppIdentity.canonicalPackage]); LineageOS equivalents resolve through it. */
    val BY_PACKAGE: Map<String, AppGlyph> =
        mapOf(
            // Sable apps (product/q25/apps.tsv).
            "org.sableos.hub" to AppGlyph.MESSAGES,
            "org.sableos.messages" to AppGlyph.MESSAGES,
            "org.sableos.mail" to AppGlyph.MAIL,
            "org.sableos.calendar" to AppGlyph.CALENDAR,
            "org.sableos.weather" to AppGlyph.WEATHER,
            "org.sableos.media" to AppGlyph.MEDIA,
            "org.sableos.calculator" to AppGlyph.CALCULATOR,
            "org.sableos.reader" to AppGlyph.READER,
            "org.sableos.textreader" to AppGlyph.TEXT_READER,
            "org.sableos.notes" to AppGlyph.NOTES,
            "org.sableos.sudoku" to AppGlyph.SUDOKU,
            "org.sableos.minesweeper" to AppGlyph.MINESWEEPER,
            "org.sableos.game2048" to AppGlyph.GAME_2048,
            "org.sableos.bricks" to AppGlyph.BRICKS,
            "org.sableos.snake" to AppGlyph.SNAKE,
            "org.sableos.blocks" to AppGlyph.BLOCKS,
            "org.sableos.reversi" to AppGlyph.REVERSI,
            "org.sableos.solitaire" to AppGlyph.SOLITAIRE,
            "org.sableos.mahjong" to AppGlyph.MAHJONG,
            "org.sableos.words" to AppGlyph.WORDS,
            "org.sableos.tools" to AppGlyph.TOOLS,
            CoreAppIdentity.SABLE_CAMERA to AppGlyph.CAMERA,
            "org.sableos.titan2.vault" to AppGlyph.VAULT,
            "org.sableos.titan2.radiodiag" to AppGlyph.RADIO,
            "org.sableos.titan2.keyboard" to AppGlyph.KEYBOARD,
            "org.sableos.titan2.displaycompat" to AppGlyph.DISPLAY,
            "org.sableos.titan2.setup" to AppGlyph.SETUP,
            // LineageOS apps the image keeps (Aperture, Glimpse and Jelly via CoreAppIdentity).
            "com.android.dialer" to AppGlyph.PHONE,
            "com.android.contacts" to AppGlyph.CONTACTS,
            "com.android.messaging" to AppGlyph.MESSAGES,
            "com.android.settings" to AppGlyph.SETTINGS,
            CoreAppIdentity.PHOTOS to AppGlyph.PHOTOS,
            CoreAppIdentity.CAMERA to AppGlyph.VIDEO_CAMERA,
            CoreAppIdentity.BROWSER to AppGlyph.BROWSER,
            CoreAppIdentity.FILES to AppGlyph.FILES,
            CoreAppIdentity.CLOCK to AppGlyph.CLOCK,
            "org.lineageos.recorder" to AppGlyph.RECORDER,
            // microG Settings and Aurora Store (product/q25/microg).
            "com.google.android.gms" to AppGlyph.SERVICES,
            "com.aurora.store" to AppGlyph.STORE,
        )

    /** The glyph for [packageName], or null when the app draws its own icon. */
    fun forPackage(packageName: String): AppGlyph? = BY_PACKAGE[CoreAppIdentity.canonicalPackage(packageName)]
}
