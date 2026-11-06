package org.sableos.start.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Cosgrove, the Sable assistant, as Command sees him: his name and its command word, read from
 * his read-only provider, so "/cosgrove" follows a rename. Only the name crosses over.
 */
data class AssistantPersona(
    val name: String,
    val word: String,
) {
    companion object {
        const val DEFAULT_NAME = "Cosgrove"
        private const val PACKAGE = "org.sableos.assistant"
        private const val ACTION_ASK = "org.sableos.assistant.action.ASK"
        private val URI: Uri = Uri.parse("content://org.sableos.assistant.persona")

        /** Null when the assistant is not installed. Binder call: off the main thread. */
        fun read(context: Context): AssistantPersona? =
            try {
                context.contentResolver.query(URI, null, null, null, null)?.use { row ->
                    if (!row.moveToFirst()) return@use null
                    AssistantPersona(row.getString(0).orEmpty().ifBlank { DEFAULT_NAME }, row.getString(1).orEmpty())
                }
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }

        /** Opens the assistant with [question] asked at once. */
        fun ask(question: String): Intent =
            Intent(ACTION_ASK)
                .setPackage(PACKAGE)
                .putExtra(Intent.EXTRA_TEXT, question)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
