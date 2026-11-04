package org.sableos.media

/**
 * Media's file and station-list probes, in Kotlin (they were the media-domain Rust crate).
 *
 * Pure parsing with no I/O, so they test on the host: radio station lists in TXT, CSV or M3U
 * form, and the leading bytes of a WAV or MP3 file. Playback, MediaSession, audio focus and
 * storage access stay in the Android layer.
 */
internal object MediaFormats {
    data class Station(
        val name: String,
        val url: String,
    )

    data class StationList(
        val stations: List<Station>,
        val skippedLines: Int,
    )

    data class WavInfo(
        val channels: Int,
        val sampleRate: Long,
        val bitsPerSample: Int,
        val dataBytes: Long,
    )

    enum class MpegVersion { Mpeg1, Mpeg2, Mpeg25, Reserved }

    enum class MpegLayer { Layer1, Layer2, Layer3, Reserved }

    data class Mp3Info(
        val hasId3: Boolean,
        val frameSync: Boolean,
        val sampleRate: Int,
        val version: MpegVersion,
        val layer: MpegLayer,
    )

    enum class ProbeError { TooSmall, InvalidContainer, UnsupportedWavFormat, MissingFormatChunk, MissingDataChunk }

    class ProbeException(
        val error: ProbeError,
    ) : Exception(error.name)

    private const val NAME_MAX = 48

    /**
     * Rows may be `name=url`, `name|url`, comma, semicolon or tab pairs (either order), a bare
     * HTTP(S) URL, or an `#EXTINF` line naming the URL on the next line.
     */
    fun parseStationList(text: String): StationList {
        val stations = mutableListOf<Station>()
        var skipped = 0
        var pendingName: String? = null
        for (raw in text.lines()) {
            val line = clean(raw)
            if (line.isEmpty()) continue
            val extinf = extinfName(line)
            when {
                extinf != null -> pendingName = extinf
                line.startsWith('#') -> Unit
                isSupportedRadioUrl(line) -> {
                    stations += Station(pendingName ?: nameFromUrl(line, stations.size + 1), line)
                    pendingName = null
                }
                else -> {
                    pendingName = null
                    val station = stationPair(line, stations.size + 1)
                    if (station != null) stations += station else skipped++
                }
            }
        }
        return StationList(stations, skipped)
    }

    fun isSupportedRadioUrl(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")

    private const val RIFF_HEADER = 12
    private const val CHUNK_HEADER = 8
    private const val FMT_MIN = 16
    private const val PCM = 1

    /** Reads PCM WAV metadata from a whole file or its leading bytes. */
    fun probeWav(bytes: ByteArray): WavInfo {
        if (bytes.size < RIFF_HEADER) throw ProbeException(ProbeError.TooSmall)
        if (!bytes.startsWith("RIFF", 0) || !bytes.startsWith("WAVE", CHUNK_HEADER)) {
            throw ProbeException(ProbeError.InvalidContainer)
        }
        var cursor = RIFF_HEADER.toLong()
        var format: Triple<Int, Long, Int>? = null
        var dataBytes: Long? = null
        while (cursor + CHUNK_HEADER <= bytes.size) {
            val at = cursor.toInt()
            val length = leU32(bytes, at + 4)
            cursor += CHUNK_HEADER
            val end = cursor + length
            if (bytes.startsWith("fmt ", at)) {
                if (length < FMT_MIN || end > bytes.size) throw ProbeException(ProbeError.TooSmall)
                val body = cursor.toInt()
                if (leU16(bytes, body) != PCM) throw ProbeException(ProbeError.UnsupportedWavFormat)
                format = Triple(leU16(bytes, body + 2), leU32(bytes, body + 4), leU16(bytes, body + 14))
            } else if (bytes.startsWith("data", at)) {
                dataBytes = length
                break
            }
            if (end > bytes.size) throw ProbeException(ProbeError.TooSmall)
            cursor = end + (length and 1L)
        }
        val (channels, sampleRate, bits) = format ?: throw ProbeException(ProbeError.MissingFormatChunk)
        return WavInfo(channels, sampleRate, bits, dataBytes ?: throw ProbeException(ProbeError.MissingDataChunk))
    }

    private const val ID3_HEADER = 10
    private const val SYNC_TAIL = 3
    private const val SEVEN_BITS = 0x7f
    private const val FRAME_SYNC_HIGH = 0xe0

    /** Looks past any ID3v2 tag for the first MPEG frame header. */
    fun probeMp3(bytes: ByteArray): Mp3Info {
        val hasId3 = bytes.size >= ID3_HEADER && bytes.startsWith("ID3", 0)
        val tagEnd =
            if (hasId3) {
                ID3_HEADER.toLong() +
                    ((u8(bytes, 6) and SEVEN_BITS).toLong() shl 21) +
                    ((u8(bytes, 7) and SEVEN_BITS).toLong() shl 14) +
                    ((u8(bytes, 8) and SEVEN_BITS).toLong() shl 7) +
                    (u8(bytes, 9) and SEVEN_BITS).toLong()
            } else {
                0L
            }
        val last = maxOf(bytes.size - SYNC_TAIL, 0)
        val start = if (tagEnd < last) tagEnd.toInt() else 0
        for (index in start until last) {
            if (u8(bytes, index) != 0xff || (u8(bytes, index + 1) and FRAME_SYNC_HIGH) != FRAME_SYNC_HIGH) continue
            val header = u8(bytes, index + 1) shl 16 or (u8(bytes, index + 2) shl 8) or u8(bytes, index + 3)
            val versionBits = (header shr 19) and 3
            val layerBits = (header shr 17) and 3
            val rateIndex = (header shr 10) and 3
            return Mp3Info(
                hasId3 = hasId3,
                frameSync = true,
                sampleRate = sampleRate(versionBits, rateIndex),
                version =
                    when (versionBits) {
                        0 -> MpegVersion.Mpeg25
                        2 -> MpegVersion.Mpeg2
                        3 -> MpegVersion.Mpeg1
                        else -> MpegVersion.Reserved
                    },
                layer =
                    when (layerBits) {
                        1 -> MpegLayer.Layer3
                        2 -> MpegLayer.Layer2
                        3 -> MpegLayer.Layer1
                        else -> MpegLayer.Reserved
                    },
            )
        }
        return Mp3Info(hasId3, false, 0, MpegVersion.Reserved, MpegLayer.Reserved)
    }

    private val SEPARATORS = charArrayOf('=', '|', ',', ';', '\t')

    private fun stationPair(
        line: String,
        fallbackIndex: Int,
    ): Station? {
        for (separator in SEPARATORS) {
            val at = line.indexOf(separator)
            if (at < 0) continue
            val left = clean(line.substring(0, at))
            val right = clean(line.substring(at + 1))
            if (isSupportedRadioUrl(left)) return Station(right.ifEmpty { nameFromUrl(left, fallbackIndex) }, left)
            if (isSupportedRadioUrl(right)) return Station(left.ifEmpty { nameFromUrl(right, fallbackIndex) }, right)
        }
        return null
    }

    private const val EXTINF = "#EXTINF"

    private fun extinfName(line: String): String? {
        // ASCII-only case folding, as before: "#extinf" matches, a dotless ı does not.
        val head = line.take(EXTINF.length).map { if (it in 'a'..'z') it - ('a' - 'A') else it }.joinToString("")
        if (head != EXTINF) return null
        val comma = line.lastIndexOf(',')
        if (comma < 0) return null
        return clean(line.substring(comma + 1)).ifEmpty { null }
    }

    private fun clean(input: String): String =
        input
            .trimStart('﻿')
            .trim()
            .trim('"')
            .trim('\'')
            .trim()

    private fun nameFromUrl(
        url: String,
        fallbackIndex: Int,
    ): String {
        val host = url.removePrefix("http://").removePrefix("https://").substringBefore('/').trim()
        return if (host.isEmpty()) "Station $fallbackIndex" else host.take(NAME_MAX)
    }

    private val MPEG1 = intArrayOf(44_100, 48_000, 32_000)
    private val MPEG2 = intArrayOf(22_050, 24_000, 16_000)
    private val MPEG25 = intArrayOf(11_025, 12_000, 8_000)

    private fun sampleRate(
        versionBits: Int,
        index: Int,
    ): Int {
        if (index >= MPEG1.size) return 0
        return when (versionBits) {
            3 -> MPEG1[index]
            2 -> MPEG2[index]
            0 -> MPEG25[index]
            else -> 0
        }
    }

    private fun ByteArray.startsWith(
        tag: String,
        at: Int,
    ): Boolean = at + tag.length <= size && tag.indices.all { this[at + it] == tag[it].code.toByte() }

    private fun u8(
        bytes: ByteArray,
        at: Int,
    ): Int = bytes[at].toInt() and 0xff

    private fun leU16(
        bytes: ByteArray,
        at: Int,
    ): Int = u8(bytes, at) or (u8(bytes, at + 1) shl 8)

    private fun leU32(
        bytes: ByteArray,
        at: Int,
    ): Long = leU16(bytes, at).toLong() or (leU16(bytes, at + 2).toLong() shl 16)
}
