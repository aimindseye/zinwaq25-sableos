package org.sableos.phone.android

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.util.LruCache

/**
 * Contact thumbnails for the Calls, Favorites and Contacts lists (from TenSeven's Phone, 2026-11-12).
 * Read from the contacts provider only, kept in a small memory cache, never copied anywhere. A
 * contact without a photo, or no contacts permission, gives null and the row shows initials.
 */
class ContactPhotos(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val byContact = LruCache<Long, Photo>(CACHE_SIZE)
    private val byNumber = LruCache<String, Photo>(CACHE_SIZE)

    /** A cached lookup: [bitmap] is null when the contact has no photo. */
    private class Photo(val bitmap: Bitmap?)

    fun forContact(contactId: Long): Bitmap? {
        byContact.get(contactId)?.let { return it.bitmap }
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        val bitmap = runCatching {
            ContactsContract.Contacts.openContactPhotoInputStream(resolver, uri, false)
                ?.use(BitmapFactory::decodeStream)
        }.getOrNull()
        byContact.put(contactId, Photo(bitmap))
        return bitmap
    }

    fun forNumber(number: String): Bitmap? =
        if (number.isEmpty()) null else byNumber.get(number)?.bitmap ?: lookUp(number)

    private fun lookUp(number: String): Bitmap? {
        val contactId = runCatching {
            resolver.query(
                Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number)
                ),
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        }.getOrNull()
        val bitmap = contactId?.let(::forContact)
        byNumber.put(number, Photo(bitmap))
        return bitmap
    }

    private companion object {
        const val CACHE_SIZE = 64
    }
}
