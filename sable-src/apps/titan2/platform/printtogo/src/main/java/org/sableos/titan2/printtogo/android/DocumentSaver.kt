package org.sableos.titan2.printtogo.android

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId
import org.sableos.titan2.printtogo.core.IppPrinter
import org.sableos.titan2.printtogo.core.PrintJob
import org.sableos.titan2.printtogo.core.PrintToGo
import org.sableos.titan2.printtogo.core.RasterReader

/**
 * Saves one received document in Documents/Print To Go through MediaStore (no storage permission): a PDF as
 * it came, a PWG or Apple raster job redrawn page by page into a PDF at the size it was printed.
 */
class DocumentSaver(private val context: Context) {
    data class Saved(val name: String, val uri: Uri)

    fun save(job: PrintJob, declared: String, body: InputStream): Saved? {
        val input = BufferedInputStream(body)
        input.mark(SNIFF)
        val head = ByteArray(SNIFF)
        val read = input.readNBytes(head, 0, SNIFF)
        input.reset()
        val format = PrintToGo.sniff(head.copyOf(read), declared)
        return format?.let { write(job, it, input) }
    }

    /** Into a new pending MediaStore file, published only once it is complete; removed if writing fails. */
    private fun write(job: PrintJob, format: String, input: InputStream): Saved? {
        val name = PrintToGo.fileName(job.name, System.currentTimeMillis(), ZoneId.systemDefault())
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, IppPrinter.PDF)
                put(MediaStore.MediaColumns.RELATIVE_PATH, PrintToGo.FOLDER + "/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        val ok =
            runCatching {
                resolver.openOutputStream(uri)?.use { out ->
                    if (format == IppPrinter.PDF) input.copyTo(out) else rasterToPdf(input, out)
                } ?: error("no output")
            }.isSuccess
        return if (ok) {
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null,
                null
            )
            Saved(name, uri)
        } else {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun rasterToPdf(input: InputStream, out: OutputStream) {
        val reader = RasterReader(input)
        val pdf = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        try {
            var number = 1
            while (true) {
                val page = reader.nextPage() ?: break
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                reader.readRows { y, argb ->
                    bitmap.setPixels(argb, 0, page.width, 0, y, page.width, 1)
                }
                val widthPt = page.width * POINTS_PER_INCH / page.dpi
                val heightPt = page.height * POINTS_PER_INCH / page.dpi
                val pdfPage = pdf.startPage(
                    PdfDocument.PageInfo.Builder(widthPt, heightPt, number++).create()
                )
                pdfPage.canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(0f, 0f, widthPt.toFloat(), heightPt.toFloat()),
                    paint
                )
                pdf.finishPage(pdfPage)
                bitmap.recycle()
            }
            check(number > 1) { "no pages" }
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private companion object {
        const val SNIFF = 8
        const val POINTS_PER_INCH = 72
    }
}
