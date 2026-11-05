package org.sableos.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MediaFormatsTest {
    @Test
    fun stationListAcceptsTextPairsAndM3u() {
        val text =
            "#EXTM3U\n#EXTINF:-1,News Radio\nhttps://radio.example/live.mp3\n" +
                "Jazz|http://jazz.example/stream\ninvalid row\n"
        val parsed = MediaFormats.parseStationList(text)
        assertEquals(listOf("News Radio", "Jazz"), parsed.stations.map { it.name })
        assertEquals(1, parsed.skippedLines)
        val quoted = MediaFormats.parseStationList("\"http://q.example/a\"")
        assertEquals("q.example", quoted.stations.single().name)
    }

    @Test
    fun wavProbeReadsPcmMetadata() {
        val buffer = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(40)
        buffer.put("WAVEfmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(2)
        buffer.putInt(44_100)
        buffer.putInt(176_400)
        buffer.putShort(4)
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(4)
        buffer.putInt(0)
        assertEquals(MediaFormats.WavInfo(2, 44_100, 16, 4), MediaFormats.probeWav(buffer.array()))
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
