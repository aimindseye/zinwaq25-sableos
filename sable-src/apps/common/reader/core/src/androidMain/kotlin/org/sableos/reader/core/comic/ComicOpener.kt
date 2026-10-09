package org.sableos.reader.core.comic

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.sableos.reader.engine.comic.ComicLimits
import org.sableos.reader.engine.comic.ComicOpenException
import org.sableos.reader.engine.comic.ComicOpenFailure
import org.sableos.reader.engine.comic.ComicSource
import org.sableos.reader.engine.comic.ZipComicSource

sealed interface ComicOpenOutcome {
    data class Opened(val source: ComicSource) : ComicOpenOutcome

    data class Failed(val failure: ComicOpenFailure) : ComicOpenOutcome
}

/** Opens a library comic: an app-private CBZ copy (`file:`) or a picked image folder (a SAF tree URI). */
@Singleton
class ComicOpener @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Blocking; call off the main thread. */
    fun open(localUri: String, limits: ComicLimits = ComicLimits()): ComicOpenOutcome {
        val uri = Uri.parse(localUri)
        return try {
            ComicOpenOutcome.Opened(
                when {
                    uri.scheme == "file" -> ZipComicSource.open(File(requireNotNull(uri.path)), limits)
                    DocumentsContract.isTreeUri(uri) -> TreeComicSource.open(context.contentResolver, uri, limits)
                    else -> throw ComicOpenException(ComicOpenFailure.UNREADABLE, "unsupported comic location")
                },
            )
        } catch (e: ComicOpenException) {
            ComicOpenOutcome.Failed(e.failure)
        }
    }
}
