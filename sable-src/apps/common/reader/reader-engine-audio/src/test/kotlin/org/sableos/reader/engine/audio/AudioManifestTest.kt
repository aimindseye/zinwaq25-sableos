package org.sableos.reader.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AudioManifestTest {
    private fun good() = AudioManifest.fromTracks(
        "Book",
        "Ann",
        listOf(AudioTrack("content://a/1", "One", 1000), AudioTrack("file:///b/2.mp3", "Two", 2000, 5)),
    )

    @Test
    fun aManifestRoundTrips() {
        val manifest = good()
        assertEquals(manifest, AudioManifestCodec.decode(AudioManifestCodec.encode(manifest)))
        assertEquals(3000L, manifest.durationMs)
    }

    @Test
    fun badOrHostileManifestsAreRejectedNotTrusted() {
        listOf(null, "", "  ", "nope", "{}", "[]").forEach { assertNull(it, AudioManifestCodec.decode(it)) }
        val streaming = good().copy(tracks = listOf(AudioTrack("https://example.invalid/a.mp3", "Net", 1000)))
        assertNull(AudioManifestCodec.decode(AudioManifestCodec.encode(streaming)))
        val outsideBook = good().copy(chapters = listOf(AudioChapter("x", 0), AudioChapter("y", 9_999_999)))
        assertNull(AudioManifestCodec.decode(AudioManifestCodec.encode(outsideBook)))
        val unordered = good().copy(
            chapters = listOf(AudioChapter("x", 0), AudioChapter("y", 2000), AudioChapter("z", 1000)),
        )
        assertNull(AudioManifestCodec.decode(AudioManifestCodec.encode(unordered)))
        val notStartingAtZero = good().copy(chapters = listOf(AudioChapter("x", 5)))
        assertNull(AudioManifestCodec.decode(AudioManifestCodec.encode(notStartingAtZero)))
        assertNull(AudioManifestCodec.decode(AudioManifestCodec.encode(good().copy(tracks = emptyList()))))
        assertNull(AudioManifestCodec.decode(AudioManifestCodec.encode(good().copy(version = 99))))
    }

    @Test
    fun limitsApplyToDecodedManifests() {
        val tiny = AudioLimits(maxTracks = 1)
        assertNull(AudioManifestCodec.decode(AudioManifestCodec.encode(good()), tiny))
        assertNotNull(AudioManifestCodec.decode(AudioManifestCodec.encode(good())))
    }

    @Test
    fun onlyLocalSourcesArePlayable() {
        listOf("content://x/1", "file:///sdcard/a.mp3", "FILE:///a", "Content://a/b").forEach {
            assertEquals(it, true, PlaybackSourcePolicy.isLocal(it))
        }
        listOf(
            "http://x/a.mp3", "https://x/a.mp3", "ftp://x/a", "rtsp://x/a", "data:audio/mp3;base64,AAAA",
            "javascript:1",
            "/sdcard/a.mp3", "", "content:", "file:", "a.mp3",
        ).forEach { assertEquals(it, false, PlaybackSourcePolicy.isLocal(it)) }
    }
}
