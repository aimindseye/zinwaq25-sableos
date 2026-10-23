package org.sableos.start.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sableos.start.model.HubPeople
import org.sableos.start.model.HubPerson

/**
 * The Hub page's Contacts tab: the personal profile's contacts (names, favourites and one
 * number each), read with Start's own READ_CONTACTS when the user has granted it. Nothing is
 * cached on disk; the list is read again when the tab is shown or Start comes back.
 */
class PeopleRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Sorted as [HubPeople.sort]; empty without the permission or on any provider error. */
    suspend fun load(): List<HubPerson> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()
            runCatching {
                val numbers = numbers()
                val people = ArrayList<HubPerson>()
                resolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.LOOKUP_KEY,
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                        ContactsContract.Contacts.STARRED,
                    ),
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " IS NOT NULL",
                    null,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext() && people.size < MAX_PEOPLE) {
                        val id = cursor.getLong(0)
                        val name = cursor.getString(2)?.trim().orEmpty()
                        if (name.isEmpty()) continue
                        people +=
                            HubPerson(
                                id = id,
                                lookupKey = cursor.getString(1).orEmpty(),
                                name = name,
                                starred = cursor.getInt(3) != 0,
                                number = numbers[id],
                            )
                    }
                }
                HubPeople.sort(people)
            }.getOrDefault(emptyList())
        }

    /** One number per contact: the default (super primary) one, else the first. */
    private fun numbers(): Map<Long, String> {
        val best = HashMap<Long, Pair<Boolean, String>>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val number = cursor.getString(1)?.trim().orEmpty()
                if (number.isEmpty()) continue
                val primary = cursor.getInt(2) != 0
                val current = best[id]
                if (current == null || (primary && !current.first)) best[id] = primary to number
            }
        }
        return best.mapValues { it.value.second }
    }

    companion object {
        private const val MAX_PEOPLE = 2000

        /** Enter on a person: the contact in the Contacts app. */
        fun viewIntent(person: HubPerson): Intent =
            Intent(
                Intent.ACTION_VIEW,
                ContactsContract.Contacts.getLookupUri(person.id, person.lookupKey)
                    ?: Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, person.id.toString()),
            )

        /** Call: the dialer with the number (Start holds no CALL_PHONE; the user presses Call). */
        fun callIntent(number: String): Intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))

        /** Message: a new message to the number, in Sable Messages when it is installed. */
        fun messageIntent(
            number: String,
            messagesPackage: String?,
        ): Intent =
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).apply {
                if (messagesPackage != null) setPackage(messagesPackage)
            }
    }
}
