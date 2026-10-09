package org.sableos.reader.core.importing

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.sableos.reader.core.documents.DocumentTreeLister
import org.sableos.reader.core.documents.TreeListingException
import org.sableos.reader.engine.audio.AudioCatalog
import org.sableos.reader.engine.comic.ComicEntryPolicy
import org.sableos.reader.engine.comic.EntryVerdict
import org.sableos.reader.model.PublicationKind

/** Decides whether a picked folder holds a comic (page images) or an audiobook (audio files). Bounded listing. */
@Singleton
class FolderKindProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Blocking. Null when the folder cannot be listed or holds neither pages nor audio. */
    fun probe(tree: Uri): PublicationKind? {
        val files = try {
            DocumentTreeLister(context.contentResolver, tree, MAX_ENTRIES, MAX_DEPTH).list()
        } catch (e: TreeListingException) {
            timber.log.Timber.d(e, "Folder cannot be probed")
            return null
        }
        val images = files.count { ComicEntryPolicy.classify(it.path) == EntryVerdict.IMAGE }
        val audio = files.count { AudioCatalog.isAudioName(it.path) }
        return when {
            audio > images -> PublicationKind.AUDIOBOOK
            images > 0 -> PublicationKind.COMIC
            else -> null
        }
    }

    private companion object {
        const val MAX_ENTRIES = 20_000
        const val MAX_DEPTH = 8
    }
}
