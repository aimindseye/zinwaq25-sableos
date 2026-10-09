package org.sableos.reader.engine.comic

/** How to decode one page image so it stays within a pixel budget; produced from the *declared* size, pre-decode. */
data class DecodePlan(val sampleSize: Int, val outWidth: Int, val outHeight: Int) {
    val decodedBytes: Long get() = outWidth.toLong() * outHeight * BYTES_PER_PIXEL

    companion object {
        /** ARGB_8888. */
        const val BYTES_PER_PIXEL: Int = 4
    }
}

object ImageDecodePlanner {
    private const val MAX_SAMPLE = 1 shl 6

    /**
     * Plans a power-of-two sub-sampled decode of a [srcWidth] x [srcHeight] image for a [targetWidth] x
     * [targetHeight] area. The sample size is the largest one that keeps both dimensions at or above the target (no
     * needless quality loss), then is raised until the result fits `limits.maxDecodedPixels`. Returns null when the
     * image is empty or its declared size exceeds `limits.maxImagePixels` (a decompression-bomb image), so nothing is
     * ever decoded.
     */
    fun plan(
        srcWidth: Int,
        srcHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        limits: ComicLimits = ComicLimits(),
    ): DecodePlan? {
        val declared = srcWidth.toLong() * srcHeight
        if (srcWidth <= 0 || srcHeight <= 0 || declared > limits.maxImagePixels) return null
        val wantW = targetWidth.coerceAtLeast(1)
        val wantH = targetHeight.coerceAtLeast(1)
        var sample = 1
        while (sample < MAX_SAMPLE && srcWidth / (sample * 2) >= wantW && srcHeight / (sample * 2) >= wantH) sample *= 2
        while (sample < MAX_SAMPLE && pixelsAt(srcWidth, srcHeight, sample) > limits.maxDecodedPixels) sample *= 2
        val w = ceilDiv(srcWidth, sample)
        val h = ceilDiv(srcHeight, sample)
        return DecodePlan(sample, w, h).takeIf { w.toLong() * h <= limits.maxDecodedPixels }
    }

    private fun pixelsAt(width: Int, height: Int, sample: Int): Long =
        ceilDiv(width, sample).toLong() * ceilDiv(height, sample)

    private fun ceilDiv(value: Int, by: Int): Int = (value + by - 1) / by
}
