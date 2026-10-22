package org.sableos.titan2.camera.android

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import org.sableos.titan2.camera.core.LaunchIntent

/**
 * The answer to an `IMAGE_CAPTURE` request. The photo is written to the caller's `EXTRA_OUTPUT`
 * (Android grants write access to it with the intent), or returned as a small bitmap in `data`
 * when the caller gave none. Sable Camera's own MediaStore copy is then removed, so a photo taken
 * for another app does not also appear in Photos. Only files this app saved are deleted.
 */
class CaptureResult(private val activity: Activity) {
    private val output: Uri? =
        activity.intent?.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)

    /** Copies off the UI thread, then calls [done] on it with the result code and data. */
    fun deliver(saved: Saved, done: (Int, Intent?) -> Unit) {
        Thread {
            val cr = activity.contentResolver
            val data = runCatching {
                if (output != null) {
                    cr.openInputStream(saved.uri).use { input ->
                        checkNotNull(input) { "capture unreadable" }
                        cr.openOutputStream(output, "wt").use { out ->
                            checkNotNull(out) { "EXTRA_OUTPUT not writable" }
                            input.copyTo(out)
                        }
                    }
                    Intent().setData(output)
                } else {
                    Intent().putExtra("data", thumbnail(saved.uri))
                }
            }.getOrNull()
            runCatching { cr.delete(saved.uri, null, null) }
            activity.runOnUiThread {
                done(if (data != null) Activity.RESULT_OK else Activity.RESULT_CANCELED, data)
            }
        }.start()
    }

    private fun thumbnail(uri: Uri): android.graphics.Bitmap {
        val cr = activity.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = LaunchIntent.thumbnailSample(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
        return checkNotNull(bitmap) { "capture not decodable" }
    }
}
