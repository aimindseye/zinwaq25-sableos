package org.sableos.start.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sableos.start.model.SpeedDialKeys

/**
 * Phone's speed dial, seen from Start: which letters have a number (Phone's read-only provider
 * returns letters only) and the request to call one. Both need the signature permission
 * org.sableos.permission.SPEED_DIAL; without Phone, or without it, no letter is assigned.
 */
class SpeedDialRepository(
    private val context: Context,
) {
    suspend fun assignedLetters(): Set<Char> =
        withContext(Dispatchers.IO) {
            val uri = Uri.Builder().scheme("content").authority(SpeedDialKeys.AUTHORITY).build()
            runCatching {
                context.contentResolver.query(uri, arrayOf(SpeedDialKeys.COLUMN_LETTER), null, null, null)?.use { c ->
                    buildSet { while (c.moveToNext()) c.getString(0)?.singleOrNull()?.let(::add) }
                }
            }.getOrNull() ?: emptySet()
        }

    /** Asks Phone to call [letter]'s number; false when Phone cannot be reached. */
    fun call(letter: Char): Boolean =
        runCatching {
            context.startActivity(
                Intent(SpeedDialKeys.ACTION_SPEED_DIAL)
                    .setPackage(SpeedDialKeys.DIALER_PACKAGE)
                    .putExtra(SpeedDialKeys.EXTRA_LETTER, letter.toString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
}
