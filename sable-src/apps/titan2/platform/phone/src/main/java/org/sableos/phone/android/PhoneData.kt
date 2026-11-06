package org.sableos.phone.android

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import java.time.Instant
import java.time.ZoneId
import org.sableos.phone.core.CallHistory
import org.sableos.phone.core.PhoneSearch

/**
 * Reads the call log and the contacts' phone numbers from their providers. No copy is kept: every screen load
 * queries again, bounded by [MAX_CALLS] and [MAX_NUMBERS]. Call off the main thread.
 */
class PhoneData(private val context: Context) {
    private val resolver = context.contentResolver

    fun calls(): List<CallHistory.Call> {
        val zone = ZoneId.systemDefault()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.NUMBER_PRESENTATION
        )
        val out = mutableListOf<CallHistory.Call>()
        runCatching {
            resolver.query(
                CallLog.Calls.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, MAX_CALLS.toString())
                    .build(),
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val time = c.getLong(DATE)
                    out += CallHistory.Call(
                        id = c.getLong(ID),
                        number = c.getString(NUMBER).orEmpty(),
                        name = c.getString(NAME)?.takeIf { it.isNotBlank() },
                        kind = CallHistory.kindOf(c.getInt(TYPE)),
                        timeMillis = time,
                        durationSeconds = c.getLong(DURATION),
                        day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate().toEpochDay(),
                        hidden = c.getInt(PRESENTATION) != CallLog.Calls.PRESENTATION_ALLOWED
                    )
                }
            }
        }
        return out
    }

    /** Every contact with a phone number: name, numbers (the default first) and starred. */
    fun people(): List<PhoneSearch.Person> {
        val byContact = linkedMapOf<Long, PhoneSearch.Person>()
        runCatching {
            resolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.STARRED),
                null,
                null,
                "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC, ${Phone.IS_SUPER_PRIMARY} DESC"
            )?.use { c ->
                var rows = 0
                while (c.moveToNext() && rows++ < MAX_NUMBERS) {
                    val id = c.getLong(0)
                    val number = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val name = c.getString(1).orEmpty().ifEmpty { number }
                    byContact[id] =
                        withNumber(byContact[id], id, name, number, c.getInt(STARRED_COLUMN) != 0)
                }
            }
        }
        return byContact.values.toList()
    }

    private fun withNumber(
        old: PhoneSearch.Person?,
        id: Long,
        name: String,
        number: String,
        starred: Boolean
    ) = when {
        old == null -> PhoneSearch.Person(id, name, listOf(number), starred)
        old.numbers.any { it.filter(Char::isDigit) == number.filter(Char::isDigit) } -> old
        else -> old.copy(numbers = old.numbers + number)
    }

    /** The contact name for [number], or null. */
    fun nameFor(number: String): String? {
        if (number.isEmpty()) return null
        return runCatching {
            resolver.query(
                Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number)
                ),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    /** Missed calls the user has now seen stop counting as new. */
    fun markMissedSeen() {
        runCatching {
            val values = ContentValues().apply {
                put(CallLog.Calls.NEW, 0)
                put(CallLog.Calls.IS_READ, 1)
            }
            resolver.update(
                CallLog.Calls.CONTENT_URI,
                values,
                "${CallLog.Calls.TYPE} = ? AND (${CallLog.Calls.NEW} = 1 OR ${CallLog.Calls.IS_READ} = 0)",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString())
            )
        }
    }

    fun deleteCalls(ids: List<Long>) {
        if (ids.isEmpty()) return
        runCatching {
            resolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} IN (${ids.joinToString(",") { "?" }})",
                ids.map { it.toString() }.toTypedArray()
            )
        }
    }

    /** Sable private mode from Sable Hub: names in the call list are hidden while it is on. */
    fun privateMode(): Boolean = runCatching {
        resolver.query(
            Uri.parse("content://org.sableos.hub.privacy/state"),
            arrayOf("private_mode"),
            null,
            null,
            null
        )
            ?.use { c -> c.moveToFirst() && c.getInt(0) != 0 }
    }.getOrNull() ?: false

    private companion object {
        const val MAX_CALLS = 500
        const val MAX_NUMBERS = 20000
        const val ID = 0
        const val NUMBER = 1
        const val NAME = 2
        const val TYPE = 3
        const val DATE = 4
        const val DURATION = 5
        const val PRESENTATION = 6
        const val STARRED_COLUMN = 3
    }
}
