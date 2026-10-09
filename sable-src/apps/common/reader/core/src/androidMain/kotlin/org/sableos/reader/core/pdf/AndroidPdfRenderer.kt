package org.sableos.reader.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.sableos.reader.engine.pdf.PdfPageRenderer
import org.sableos.reader.engine.pdf.PdfPageSize
import org.sableos.reader.engine.pdf.PixelSize

/**
 * Android `PdfRenderer` binding (no Pdfium, no third-party SDK). `PdfRenderer` allows one open page at a time and is
 * not thread-safe, so every operation is serialized and each page is opened, used and closed within one call.
 */
class AndroidPdfRenderer(private val descriptor: ParcelFileDescriptor) : PdfPageRenderer<Bitmap> {
    private val renderer = PdfRenderer(descriptor)
    private var closed = false

    override val pageCount: Int = renderer.pageCount

    @Synchronized
    override fun pageSize(page: Int): PdfPageSize {
        check(!closed) { "renderer is closed" }
        val open = renderer.openPage(page)
        try {
            return PdfPageSize(open.width.coerceAtLeast(1), open.height.coerceAtLeast(1))
        } finally {
            open.close()
        }
    }

    @Synchronized
    override fun render(page: Int, size: PixelSize): Bitmap {
        check(!closed) { "renderer is closed" }
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        // PdfRenderer does not clear the target; a transparent bitmap would render black on some pages.
        bitmap.eraseColor(Color.WHITE)
        val open = renderer.openPage(page)
        try {
            open.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        } finally {
            open.close()
        }
        return bitmap
    }

    /**
     * Intentionally does not `recycle()`: the page currently on screen may still reference an image the cache just
     * evicted. Memory stays bounded because the cache holds a fixed number of strong references.
     */
    override fun release(image: Bitmap) = Unit

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        renderer.close()
        descriptor.close()
    }
}
