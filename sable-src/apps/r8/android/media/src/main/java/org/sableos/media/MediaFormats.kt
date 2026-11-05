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
    private const val BOM = '\uFEFF'
    private const val HTTP = "http://"
    private const val HTTPS = "https://"

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
            val extinf = extinfName(line)
            when {
                line.isEmpty() -> {
                    Unit
                }

                extinf != null -> {
                    pendingName = extinf
                }

                line.startsWith('#') -> {
                    Unit
                }

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

    fun isSupportedRadioUrl(url: String): Boolean = url.startsWith(HTTP) || url.startsWith(HTTPS)

    private const val RIFF_HEADER = 12
    private const val WAVE_OFFSET = 8
    private const val CHUNK_HEADER = 8
    private const val LENGTH_OFFSET = 4
    private const val FMT_MIN = 16
    private const val PCM = 1
    private const val CHANNELS_OFFSET = 2
    private const val RATE_OFFSET = 4
    private const val BITS_OFFSET = 14

    /** Reads PCM WAV metadata from a whole file or its leading bytes. */
    fun probeWav(bytes: ByteArray): WavInfo {
        val headerError =
            when {
                bytes.size < RIFF_HEADER -> ProbeError.TooSmall
                !bytes.startsWith("RIFF", 0) || !bytes.startsWith("WAVE", WAVE_OFFSET) -> ProbeError.InvalidContainer
                else -> null
            }
        if (headerError != null) throw ProbeException(headerError)
        return WavChunks(bytes).read()
    }

    /** Walks the RIFF chunks after the header up to the data chunk. */
    private class WavChunks(
        private val bytes: ByteArray,
    ) {
        private var format: Triple<Int, Long, Int>? = null
        private var dataBytes: Long? = null

        fun read(): WavInfo {
            var cursor = RIFF_HEADER.toLong()
            while (dataBytes == null && cursor + CHUNK_HEADER <= bytes.size) {
                cursor = chunk(cursor.toInt())
            }
            val (channels, sampleRate, bits) = format ?: throw ProbeException(ProbeError.MissingFormatChunk)
            return WavInfo(channels, sampleRate, bits, dataBytes ?: throw ProbeException(ProbeError.MissingDataChunk))
        }

        /** Reads the chunk at [at]; the next chunk's offset (word aligned). */
        private fun chunk(at: Int): Long {
            val length = leU32(bytes, at + LENGTH_OFFSET)
            val body = at.toLong() + CHUNK_HEADER
            val end = body + length
            when {
                bytes.startsWith("fmt ", at) -> format(body.toInt(), length, end)
                bytes.startsWith("data", at) -> dataBytes = length
            }
            if (dataBytes == null && end > bytes.size) throw ProbeException(ProbeError.TooSmall)
            return end + (length and 1L)
        }

        private fun format(
            body: Int,
            length: Long,
            end: Long,
        ) {
            if (length < FMT_MIN || end > bytes.size) throw ProbeException(ProbeError.TooSmall)
            if (leU16(bytes, body) != PCM) throw ProbeException(ProbeError.UnsupportedWavFormat)
            format =
                Triple(
                    leU16(bytes, body + CHANNELS_OFFSET),
                    leU32(bytes, body + RATE_OFFSET),
                    leU16(bytes, body + BITS_OFFSET),
                )
        }
    }

    private const val ID3_HEADER = 10
    private const val ID3_SIZE_START = 6
    private const val SYNCHSAFE_BITS = 7
    private const val SEVEN_BITS = 0x7f
    private const val SYNC_TAIL = 3
    private const val SYNC_BYTE = 0xff
    private const val FRAME_SYNC_HIGH = 0xe0
    private const val BYTE_BITS = 8
    private const val BYTE_MASK = 0xff
    private const val TWO_BITS = 3
    private const val VERSION_SHIFT = 19
    private const val LAYER_SHIFT = 17
    private const val RATE_SHIFT = 10

    /** Indexed by the header's two version bits: 00 MPEG 2.5, 01 reserved, 10 MPEG 2, 11 MPEG 1. */
    private val VERSIONS = listOf(MpegVersion.Mpeg25, MpegVersion.Reserved, MpegVersion.Mpeg2, MpegVersion.Mpeg1)

    /** Indexed by the header's two layer bits: 00 reserved, 01 Layer III, 10 Layer II, 11 Layer I. */
    private val LAYERS = listOf(MpegLayer.Reserved, MpegLayer.Layer3, MpegLayer.Layer2, MpegLayer.Layer1)

    /** Sample rates per version bits, each indexed by the rate bits (11 is reserved). */
    private val RATES =
        listOf(
            listOf(11_025, 12_000, 8_000),
            emptyList(),
            listOf(22_050, 24_000, 16_000),
            listOf(44_100, 48_000, 32_000),
        )

    /** Looks past any ID3v2 tag for the first MPEG frame header. */
    fun probeMp3(bytes: ByteArray): Mp3Info {
        val hasId3 = bytes.size >= ID3_HEADER && bytes.startsWith("ID3", 0)
        val tagEnd = if (hasId3) id3End(bytes) else 0L
        val last = maxOf(bytes.size - SYNC_TAIL, 0)
        val start = if (tagEnd < last) tagEnd.toInt() else 0
        val frame = (start until last).firstOrNull { isFrameSync(bytes, it) }
        return if (frame == null) {
            Mp3Info(hasId3, false, 0, MpegVersion.Reserved, MpegLayer.Reserved)
        } else {
            frameInfo(bytes, frame, hasId3)
        }
    }

    /** The end of the ID3v2 tag: ten header bytes plus its synchsafe size. */
    private fun id3End(bytes: ByteArray): Long =
        ID3_HEADER +
            (ID3_SIZE_START until ID3_HEADER).fold(0L) { size, index ->
                (size shl SYNCHSAFE_BITS) or (u8(bytes, index) and SEVEN_BITS).toLong()
            }

    private fun isFrameSync(
        bytes: ByteArray,
        index: Int,
    ): Boolean = u8(bytes, index) == SYNC_BYTE && (u8(bytes, index + 1) and FRAME_SYNC_HIGH) == FRAME_SYNC_HIGH

    private fun frameInfo(
        bytes: ByteArray,
        index: Int,
        hasId3: Boolean,
    ): Mp3Info {
        val header =
            (u8(bytes, index + 1) shl (BYTE_BITS * 2)) or
                (u8(bytes, index + 2) shl BYTE_BITS) or
                u8(bytes, index + SYNC_TAIL)
        val versionBits = (header shr VERSION_SHIFT) and TWO_BITS
        val rateIndex = (header shr RATE_SHIFT) and TWO_BITS
        return Mp3Info(
            hasId3 = hasId3,
            frameSync = true,
            sampleRate = RATES[versionBits].getOrElse(rateIndex) { 0 },
            version = VERSIONS[versionBits],
            layer = LAYERS[(header shr LAYER_SHIFT) and TWO_BITS],
        )
    }

    private val SEPARATORS = listOf('=', '|', ',', ';', '\t')

    /** The first separator whose one side is an HTTP(S) URL; the other side names it. */
    private fun stationPair(
        line: String,
        fallbackIndex: Int,
    ): Station? =
        SEPARATORS
            .asSequence()
            .mapNotNull { separator -> pairAt(line, separator, fallbackIndex) }
            .firstOrNull()

    private fun pairAt(
        line: String,
        separator: Char,
        fallbackIndex: Int,
    ): Station? {
        val at = line.indexOf(separator)
        if (at < 0) return null
        val left = clean(line.substring(0, at))
        val right = clean(line.substring(at + 1))
        return when {
            isSupportedRadioUrl(left) -> Station(right.ifEmpty { nameFromUrl(left, fallbackIndex) }, left)
            isSupportedRadioUrl(right) -> Station(left.ifEmpty { nameFromUrl(right, fallbackIndex) }, right)
            else -> null
        }
    }

    private const val EXTINF = "#EXTINF"

    private fun extinfName(line: String): String? {
        val comma = line.lastIndexOf(',')
        if (!isExtinf(line) || comma < 0) return null
        return clean(line.substring(comma + 1)).ifEmpty { null }
    }

    /** ASCII-only case folding, as before: "#extinf" matches, a dotless ı does not. */
    private fun isExtinf(line: String): Boolean =
        line.length >= EXTINF.length &&
            EXTINF.indices.all { index ->
                val c = line[index]
                (if (c in 'a'..'z') c - ('a' - 'A') else c) == EXTINF[index]
            }

    private fun clean(input: String): String =
        input
            .trimStart(BOM)
            .trim()
            .trim('"')
            .trim('\'')
            .trim()

    private fun nameFromUrl(
        url: String,
        fallbackIndex: Int,
    ): String {
        val withoutScheme = url.removePrefix(if (url.startsWith(HTTP)) HTTP else HTTPS)
        val host = withoutScheme.substringBefore('/').trim()
        return if (host.isEmpty()) "Station $fallbackIndex" else host.take(NAME_MAX)
    }

    private fun ByteArray.startsWith(
        tag: String,
        at: Int,
    ): Boolean = at + tag.length <= size && tag.indices.all { this[at + it] == tag[it].code.toByte() }

    private fun u8(
        bytes: ByteArray,
        at: Int,
    ): Int = bytes[at].toInt() and BYTE_MASK

    private fun leU16(
        bytes: ByteArray,
        at: Int,
    ): Int = u8(bytes, at) or (u8(bytes, at + 1) shl BYTE_BITS)

    private fun leU32(
        bytes: ByteArray,
        at: Int,
    ): Long = leU16(bytes, at).toLong() or (leU16(bytes, at + 2).toLong() shl (BYTE_BITS * 2))
}
