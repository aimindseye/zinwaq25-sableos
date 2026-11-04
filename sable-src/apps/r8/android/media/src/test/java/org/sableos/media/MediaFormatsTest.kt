package org.sableos.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MediaFormatsTest {
    @Test
    fun stationListAcceptsTextPairsAndM3u() {
        val parsed =
            MediaFormats.parseStationList(
                "#EXTM3U\n#EXTINF:-1,News Radio\nhttps://radio.example/live.mp3\nJazz|http://jazz.example/stream\ninvalid row\n",
            )
        assertEquals(listOf("News Radio", "Jazz"), parsed.stations.map { it.name })
        assertEquals(1, parsed.skippedLines)
        assertEquals("q.example", MediaFormats.parseStationList("\"http://q.example/a\"").stations.single().name)
    }

    @Test
    fun wavProbeReadsPcmMetadata() {
        val wav =
            ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()).putInt(40).put("WAVEfmt ".toByteArray()).putInt(16)
                putShort(1).putShort(2).putInt(44_100).putInt(176_400).putShort(4).putShort(16)
                put("data".toByteArray()).putInt(4).putInt(0)
            }.array()
        assertEquals(MediaFormats.WavInfo(2, 44_100, 16, 4), MediaFormats.probeWav(wav))
        val error = runCatching { MediaFormats.probeWav(ByteArray(4)) }.exceptionOrNull()
        assertEquals(MediaFormats.ProbeError.TooSmall, (error as MediaFormats.ProbeException).error)
    }

    @Test
    fun mp3ProbeRecognizesMpeg1Layer3() {
        val info = MediaFormats.probeMp3(byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0x64))
        assertTrue(info.frameSync)
        assertEquals(MediaFormats.MpegVersion.Mpeg1, info.version)
        assertEquals(MediaFormats.MpegLayer.Layer3, info.layer)
        assertEquals(44_100, info.sampleRate)
    }
}
