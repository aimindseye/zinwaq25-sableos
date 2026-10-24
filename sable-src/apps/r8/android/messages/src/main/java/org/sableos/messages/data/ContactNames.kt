package org.sableos.messages.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import org.sableos.messages.core.Addresses
import java.util.concurrent.ConcurrentHashMap

data class ContactNumber(
    val name: String,
    val number: String,
    val label: String,
)

/** Contact names for numbers and the "To" picker. Empty without READ_CONTACTS. */
class ContactNames(
    private val context: Context,
) {
    private val cache = ConcurrentHashMap<String, String>()
    private val resolver = context.contentResolver

    fun canRead(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Clears names cached before contacts changed or the permission was granted. */
    fun clear() = cache.clear()

    fun nameFor(address: String): String? {
        val key = Addresses.matchKey(address)
        if (key.isEmpty() || !canRead()) return null
        // "" caches "no contact", so unknown numbers are looked up once.
        return cache.getOrPut(key) { lookup(address).orEmpty() }.ifEmpty { null }
    }

    private fun lookup(address: String): String? =
        runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
            resolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.onFailure { Log.w(TAG, "contact lookup failed", it) }
            .getOrNull()

    /** Contacts with a phone number whose name or number contains [query]. */
    fun search(
        query: String,
        limit: Int = MAX_RESULTS,
    ): List<ContactNumber> {
        if (!canRead() || query.isBlank()) return emptyList()
        val uri =
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI
                .buildUpon()
                .appendPath(query.trim())
                .build()
        val projection =
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
            )
        return runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        val label =
                            ContactsContract.CommonDataKinds.Phone
                                .getTypeLabel(
                                    context.resources,
                                    cursor.getInt(COLUMN_TYPE),
                                    cursor.getString(COLUMN_LABEL),
                                ).toString()
                        add(
                            ContactNumber(
                                cursor.getString(COLUMN_NAME).orEmpty(),
                                cursor.getString(COLUMN_NUMBER).orEmpty(),
                                label,
                            ),
                        )
                    }
                }.distinctBy { it.name to Addresses.matchKey(it.number) }
            } ?: emptyList()
        }.onFailure { Log.w(TAG, "contact search failed", it) }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "SableMessages"
        const val MAX_RESULTS = 8
        const val COLUMN_NAME = 0
        const val COLUMN_NUMBER = 1
        const val COLUMN_TYPE = 2
        const val COLUMN_LABEL = 3
    }
}
