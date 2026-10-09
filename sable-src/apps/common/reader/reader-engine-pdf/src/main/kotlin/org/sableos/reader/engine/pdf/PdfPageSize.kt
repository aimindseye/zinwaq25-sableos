package org.sableos.reader.engine.pdf

import kotlin.math.sqrt

/** Size of a PDF page in points (1/72 inch), as reported by the document. */
data class PdfPageSize(val widthPoints: Int, val heightPoints: Int) {
    init {
        require(widthPoints > 0 && heightPoints > 0) { "page size must be positive: ${widthPoints}x$heightPoints" }
    }
}

/** Target bitmap size in pixels. */
data class PixelSize(val width: Int, val height: Int)

/**
 * Viewport-appropriate render sizing. A page is rendered to fit the viewport (never larger than the display needs) and
 * is additionally capped by [MAX_BITMAP_PIXELS], so a huge page can never produce an unbounded bitmap.
 */
object PdfRenderSizing {
    /** Hard budget for one page bitmap: 4 Mpx = 16 MiB as ARGB_8888. Three cached pages stay well under 64 MiB. */
    const val MAX_BITMAP_PIXELS: Long = 4L * 1024 * 1024

    private const val MIN_DIMENSION = 1

    fun fit(page: PdfPageSize, viewportWidth: Int, viewportHeight: Int): PixelSize {
        val vw = viewportWidth.coerceAtLeast(MIN_DIMENSION).toDouble()
        val vh = viewportHeight.coerceAtLeast(MIN_DIMENSION).toDouble()
        val scale = minOf(vw / page.widthPoints, vh / page.heightPoints)
        var width = page.widthPoints * scale
        var height = page.heightPoints * scale
        val pixels = width * height
        if (pixels > MAX_BITMAP_PIXELS) {
            val shrink = sqrt(MAX_BITMAP_PIXELS / pixels)
            width *= shrink
            height *= shrink
        }
        return PixelSize(width.toInt().coerceAtLeast(MIN_DIMENSION), height.toInt().coerceAtLeast(MIN_DIMENSION))
    }
}
