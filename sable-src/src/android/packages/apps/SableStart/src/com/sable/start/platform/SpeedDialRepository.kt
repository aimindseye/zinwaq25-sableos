package org.sableos.start.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sableos.start.model.SpeedDialKeys

/**
 * The letters held on the home screen: Phone's speed dial (which letters have a number, Phone's read-only provider
 * returns letters only, and the request to call one; both need the signature permission
 * org.sableos.permission.SPEED_DIAL, so without Phone, or without it, no number is assigned) and the app shortcuts
 * Start keeps itself ([LetterShortcutRepository]). A letter with an app opens that app, ahead of any number.
 */
class SpeedDialRepository(
    private val context: Context,
    private val shortcuts: LetterShortcutRepository? = null,
) {
    suspend fun assignedLetters(): Set<Char> =
        withContext(Dispatchers.IO) { phoneLetters() + (shortcuts?.load()?.letters.orEmpty()) }

    private fun phoneLetters(): Set<Char> {
        val uri = Uri.Builder().scheme("content").authority(SpeedDialKeys.AUTHORITY).build()
        return runCatching {
            context.contentResolver.query(uri, arrayOf(SpeedDialKeys.COLUMN_LETTER), null, null, null)?.use { c ->
                buildSet { while (c.moveToNext()) c.getString(0)?.singleOrNull()?.let(::add) }
            }
        }.getOrNull() ?: emptySet()
    }

    /** Opens [letter]'s app, else asks Phone to call its number; false when neither can be reached. */
    fun call(letter: Char): Boolean = shortcuts?.launch(letter) == true || callPhone(letter)

    private fun callPhone(letter: Char): Boolean =
        runCatching {
            context.startActivity(
                Intent(SpeedDialKeys.ACTION_SPEED_DIAL)
                    .setPackage(SpeedDialKeys.DIALER_PACKAGE)
                    .putExtra(SpeedDialKeys.EXTRA_LETTER, letter.toString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
}
