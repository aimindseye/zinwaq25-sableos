/*
 *  Copyright (c) 2026 Piyush Daiya
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 */

package org.vaachak.reader.core.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Android-specific operations (File System, Permissions, Uri parsing)
 * to keep the Repository clean and KMP-ready.
 */
@Singleton
class LibraryPlatformHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Persist permission to read this URI across device restarts.
     * Essential for Scoped Storage on modern Android.
     */
    fun takePersistableUriPermission(uri: Uri) {
        try {
            // Only try to take persistable permission if the URI supports it (e.g., SAF URIs)
            // File:// URIs or simple content:// URIs might throw SecurityException if not supported
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            // Permission might already be granted or not supported for this specific URI type
            // e.g. "No persistable permission grants found for UID..."
            e.printStackTrace()
        }
    }

    /**
     * Saves a Bitmap cover image to the app's internal private storage.
     * Returns the absolute file path.
     */
    fun saveCoverBitmapToStorage(bitmap: Bitmap, title: String): String? {
        return try {
            // Sanitize filename hash to ensure valid file path
            val uniqueSuffix = System.currentTimeMillis()
            val filename = "cover_${title.hashCode()}_$uniqueSuffix.png"
            val file = File(context.filesDir, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Converts a File object to a string URI (file://...)
     */
    fun getFileUriString(file: File): String {
        return Uri.fromFile(file).toString()
    }

    /**
     * Opens an InputStream for the given URI.
     * Used for calculating MD5 hashes or reading content without exposing Context to Repositories.
     */
    fun openInputStream(uri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


}