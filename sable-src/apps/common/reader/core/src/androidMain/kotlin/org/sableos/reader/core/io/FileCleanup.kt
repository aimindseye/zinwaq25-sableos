package org.sableos.reader.core.io

import java.io.File
import timber.log.Timber

/**
 * Best-effort removal of a cache or staging file. A file that is already gone counts as removed;
 * a file that is still there afterwards is logged, since the caller has nothing else to do with it.
 */
internal fun File.deleteQuietly(what: String) {
    if (!delete() && exists()) Timber.w("Could not delete %s: %s", what, name)
}
