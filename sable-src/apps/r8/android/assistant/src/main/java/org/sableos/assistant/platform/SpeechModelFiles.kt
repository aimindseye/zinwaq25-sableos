package org.sableos.assistant.platform

import android.content.Context
import org.sableos.assistant.core.Speech
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream

/** The speech model in Cosgrove's own storage, outside backups: fetched on request, removable. */
class SpeechModelFiles(
    context: Context,
) {
    private val root = File(context.applicationContext.noBackupFilesDir, "speech")
    val dir = File(root, "en-us")
    private val marker = File(dir, READY)

    val ready: Boolean get() = marker.isFile

    /** Downloads and unpacks the model; blocking, so call it off the main thread. */
    fun download(): Boolean {
        val partial = File(root, "en-us.part")
        partial.deleteRecursively()
        val connection = URI(Speech.MODEL_URL).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_MS
            connection.readTimeout = READ_MS
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            ZipInputStream(connection.inputStream.buffered()).use { zip -> unpack(zip, partial) }
            dir.deleteRecursively()
            if (!partial.renameTo(dir)) throw IOException("rename")
            marker.createNewFile()
        } catch (_: IOException) {
            partial.deleteRecursively()
            false
        } finally {
            connection.disconnect()
        }
    }

    fun remove() {
        root.deleteRecursively()
    }

    private fun unpack(
        zip: ZipInputStream,
        into: File,
    ) {
        generateSequence { zip.nextEntry }.forEach { entry ->
            val target = Speech.entryPath(entry.name)?.let { File(into, it) }
            when {
                target == null -> {
                    Unit
                }

                entry.isDirectory -> {
                    target.mkdirs()
                }

                else -> {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }

    private companion object {
        const val READY = ".ready"
        const val CONNECT_MS = 15_000
        const val READ_MS = 60_000
    }
}
