package io.github.aimindseye.tenseven.frames

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * SableOS cover API: `content://org.sableos.launcher.covers`. An app calls
 * `contentResolver.call(Uri.parse("content://org.sableos.launcher.covers"), "setCover", null, extras)` with
 * the string extras `title` and `detail`, or `"clearCover"`, to say what its own Active Frame shows. The
 * cover is always the caller's own: the package comes from [getCallingPackage], which Android checks against
 * the calling UID. Nothing else is offered; queries, inserts and updates are refused.
 */
class SableCoverProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        val caller = callingPackage.orEmpty()
        val ok =
            when (method) {
                METHOD_SET -> {
                    SableFrameCovers.set(
                        caller,
                        extras?.getString(EXTRA_TITLE),
                        extras?.getString(EXTRA_DETAIL),
                        System.currentTimeMillis(),
                    )
                }

                METHOD_CLEAR -> {
                    caller.isNotEmpty().also { if (it) SableFrameCovers.clear(caller) }
                }

                else -> {
                    false
                }
            }
        return Bundle().apply { putBoolean(RESULT_OK, ok) }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val METHOD_SET = "setCover"
        const val METHOD_CLEAR = "clearCover"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DETAIL = "detail"
        const val RESULT_OK = "ok"
    }
}
