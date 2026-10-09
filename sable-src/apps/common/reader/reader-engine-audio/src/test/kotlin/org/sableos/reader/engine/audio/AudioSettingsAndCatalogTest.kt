package org.sableos.reader.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AudioSettingsAndCatalogTest {
    @Test
    fun settingsRoundTripAndSnapToOfferedChoices() {
        val settings = AudioPlaybackSettings(1.5f, 10_000, 60_000)
        assertEquals(settings, AudioSettingsCodec.decode(AudioSettingsCodec.encode(settings)))
        val odd = AudioSettingsCodec.decode("{\"speed\":1.64,\"back\":11000,\"fwd\":40000}")
        assertEquals(1.75f, odd?.speed)
        assertEquals(10_000L, odd?.skipBackMs)
        assertEquals(30_000L, odd?.skipForwardMs)
    }

    @Test
    fun badSettingsDecodeToNull() {
        listOf(null, "", "x", "{}", "{\"v\":1}", "[1]").forEach { assertNull(it, AudioSettingsCodec.decode(it)) }
        assertEquals(1.0f, AudioSettingsCodec.decode("{\"speed\":NaN}")?.speed ?: 1.0f, 0f)
        assertEquals(AudioPlaybackSettings.SPEEDS.last(), AudioPlaybackSettings.snapSpeed(99f))
    }

    @Test
    fun foldersOrderNaturallyAndIgnoreNonAudio() {
        val entries = listOf(
            AudioEntry("10 Ten.mp3", "content://a/10", 1),
            AudioEntry("cover.jpg", "content://a/c", 1),
            AudioEntry("2 Two.MP3", "content://a/2", 1),
            AudioEntry("._junk.mp3", "content://a/j", 1),
            AudioEntry("1 One.m4a", "content://a/1", 1),
            AudioEntry("notes.txt", "content://a/n", 1),
        )
        assertEquals(listOf("1 One.m4a", "2 Two.MP3", "10 Ten.mp3"), AudioCatalog.order(entries).map { it.name })
    }

    @Test
    fun emptyOversizedAndStreamingFoldersAreRefused() {
        fun failure(block: () -> Unit): AudioCatalogFailure? =
            (runCatching(block).exceptionOrNull() as? AudioCatalogException)?.failure
        val notAudio = listOf(AudioEntry("a.txt", "content://x", 1))
        assertEquals(AudioCatalogFailure.NO_AUDIO, failure { AudioCatalog.order(notAudio) })
        assertEquals(AudioCatalogFailure.NO_AUDIO, failure { AudioCatalog.order(emptyList()) })
        val many = (0..3).map { AudioEntry("$it.mp3", "content://x/$it", 1) }
        val tooMany = failure { AudioCatalog.order(many, AudioLimits(maxTracks = 3)) }
        assertEquals(AudioCatalogFailure.TOO_MANY_FILES, tooMany)
        assertEquals(
            AudioCatalogFailure.NOT_LOCAL,
            failure { AudioCatalog.order(listOf(AudioEntry("a.mp3", "https://example.invalid/a.mp3", 1))) },
        )
        if (failure { AudioCatalog.order(many) } != null) fail("four files within default limits must pass")
    }

    @Test
    fun namesAndTitlesAreRecognised() {
        assertTrue(AudioCatalog.isAudioName("dir/Book.M4B"))
        assertTrue(AudioCatalog.isAudioName("a\\b\\c.opus"))
        assertFalse(AudioCatalog.isAudioName("book.pdf"))
        assertFalse(AudioCatalog.isAudioName("noextension"))
        assertTrue(AudioCatalog.isChapteredContainer("x.m4b"))
        assertFalse(AudioCatalog.isChapteredContainer("x.mp3"))
        assertEquals("Chapter 01", AudioCatalog.titleFromName("sub/Chapter 01.mp3"))
        assertEquals("weird", AudioCatalog.titleFromName("weird"))
    }

    @Test
    fun progressIsWrittenAtMostOncePerIntervalUnlessForced() {
        var now = 0L
        val throttle = ProgressThrottle({ now }, intervalMs = 10_000)
        assertTrue(throttle.shouldWrite())
        now += 9_999
        assertFalse(throttle.shouldWrite())
        assertTrue(throttle.shouldWrite(force = true))
        now += 9_999
        assertFalse(throttle.shouldWrite())
        now += 1
        assertTrue(throttle.shouldWrite())
        throttle.reset()
        assertTrue(throttle.shouldWrite())
    }
}

class AudioTimeFormatTest {
    @Test
    fun clocksShowHoursOnlyWhenNeeded() {
        assertEquals("0:00", AudioTimeFormat.clock(0))
        assertEquals("0:09", AudioTimeFormat.clock(9_999))
        assertEquals("1:05", AudioTimeFormat.clock(65_000))
        assertEquals("59:59", AudioTimeFormat.clock(3_599_000))
        assertEquals("1:00:00", AudioTimeFormat.clock(3_600_000))
        assertEquals("12:34:56", AudioTimeFormat.clock(45_296_000))
        assertEquals("0:00", AudioTimeFormat.clock(-5_000))
    }

    @Test
    fun intervalsReadNaturally() {
        assertEquals("15 s", AudioTimeFormat.interval(15_000))
        assertEquals("1 min", AudioTimeFormat.interval(60_000))
        assertEquals("90 s", AudioTimeFormat.interval(90_000))
    }
}
