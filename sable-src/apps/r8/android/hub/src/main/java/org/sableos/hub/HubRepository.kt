package org.sableos.hub

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import org.sableos.hub.notifications.ConnectedReplyRegistry
import java.util.Locale

class HubRepository(
    private val context: Context,
) {
    private val connectedApps = ConnectedAppsRepository(context)
    private val connectedHistory = ConnectedNotificationHistoryStore(context)
    private val connectedInventory = ConnectedAppsInventory(context)

    fun snapshot(): HubSnapshot {
        val capabilities = capabilities()
        val messages =
            if (capabilities.canReadSms) {
                loadMessages()
            } else {
                emptyList()
            }
        val people =
            if (capabilities.canReadContacts) {
                loadPeople()
            } else {
                emptyList()
            }

        val namesByNumber =
            people.associate { person ->
                normalizeAddress(person.phoneNumber) to person.displayName
            }

        val smsConversations =
            HubConversationReducer.reduce(messages) { address ->
                namesByNumber[normalizeAddress(address)]
                    ?: lookupContactName(address)
                    ?: address
            }

        val connectedPolicies =
            connectedApps
                .loadPolicies()
                .associateBy { policy ->
                    policy.key
                }
        val connectedRecords =
            connectedHistory.load(
                policies = connectedPolicies,
            )
        val connected =
            HubConnectedConversationReducer.reduce(
                records = connectedRecords,
                canReply = ConnectedReplyRegistry::canReply,
            )

        return HubSnapshot(
            capabilities = capabilities,
            conversations =
                (smsConversations + connected.conversations)
                    .sortedByDescending { conversation ->
                        conversation.lastDateMillis
                    },
            messages =
                (messages + connected.messages)
                    .sortedByDescending { message ->
                        message.dateMillis
                    },
            people = people,
            mail = loadMailSnapshot(),
        )
    }

    fun replyToConnected(
        notificationKey: String,
        body: String,
    ): HubSendResult {
        val result =
            ConnectedReplyRegistry.send(
                context = context,
                notificationKey = notificationKey,
                body = body,
            )
        if (result.success) {
            val policySnapshot =
                connectedApps
                    .loadPolicies()
                    .associateBy { policy ->
                        policy.key
                    }
            connectedHistory.appendLocalReply(
                notificationKey = notificationKey,
                body = body,
                policies = policySnapshot,
            )
        }
        return result
    }

    fun openSableMail(): Boolean {
        val intent =
            context.packageManager
                .getLaunchIntentForPackage(SABLE_MAIL_PACKAGE)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun loadMailSnapshot(): HubMailSnapshot {
        val fallback =
            HubMailSnapshot(
                detail = "Sable Mail is unavailable.",
                available = false,
                observedAtMillis = System.currentTimeMillis(),
            )

        return runCatching {
            context.contentResolver
                .query(
                    SABLE_MAIL_SNAPSHOT_URI,
                    arrayOf(
                        "detail",
                        "availability",
                        "observed_at",
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        fallback
                    } else {
                        HubMailSnapshot(
                            detail =
                                cursor.getString(
                                    cursor.getColumnIndexOrThrow("detail"),
                                ),
                            available =
                                cursor.getString(
                                    cursor.getColumnIndexOrThrow("availability"),
                                ) != "UNAVAILABLE",
                            observedAtMillis =
                                cursor.getLong(
                                    cursor.getColumnIndexOrThrow("observed_at"),
                                ),
                        )
                    }
                } ?: fallback
        }.getOrDefault(fallback)
    }

    fun openConnectedApp(
        packageName: String,
        userSerial: Long,
    ): Boolean =
        connectedInventory.open(
            ConnectedAppKey(
                packageName = packageName,
                userSerial = userSerial,
            ),
        )

    fun capabilities(): HubCapabilities {
        val roleManager =
            context.getSystemService(RoleManager::class.java)

        return HubCapabilities(
            hasTelephony =
                context.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_MESSAGING,
                ),
            canReadSms = hasPermission(Manifest.permission.READ_SMS),
            canSendSms = hasPermission(Manifest.permission.SEND_SMS),
            canReadContacts = hasPermission(Manifest.permission.READ_CONTACTS),
            isDefaultSmsRoleHolder =
                roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true,
        )
    }

    @SuppressLint("MissingPermission")
    fun sendSms(
        recipient: String,
        body: String,
    ) {
        require(recipient.isNotBlank()) {
            "Recipient is required."
        }
        require(body.isNotBlank()) {
            "Message is empty."
        }

        // Lint cannot infer this project-local permission predicate. Keep the
        // suppression scoped to this method and fail closed before any
        // telephony API is called.
        check(hasPermission(Manifest.permission.SEND_SMS)) {
            "SMS sending permission is not available."
        }

        val subscriptionId =
            SubscriptionManager.getDefaultSmsSubscriptionId()
        val smsManager =
            if (SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                smsManagerForSubscription(subscriptionId)
            } else {
                defaultSmsManager()
            }

        val parts = smsManager.divideMessage(body)
        if (parts.size <= 1) {
            smsManager.sendTextMessage(
                recipient,
                null,
                body,
                null,
                null,
            )
        } else {
            smsManager.sendMultipartTextMessage(
                recipient,
                null,
                parts,
                null,
                null,
            )
        }
    }

    private fun smsManagerForSubscription(subscriptionId: Int): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context
                .getSystemService(SmsManager::class.java)
                .createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }

    private fun defaultSmsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    fun openAndroidMessaging(
        recipient: String? = null,
        body: String? = null,
    ): Boolean {
        val intent =
            if (recipient.isNullOrBlank()) {
                Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_MESSAGING,
                )
            } else {
                Intent(
                    Intent.ACTION_SENDTO,
                    Uri.parse("smsto:" + Uri.encode(recipient)),
                ).apply {
                    if (!body.isNullOrBlank()) {
                        putExtra("sms_body", body)
                    }
                }
            }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun loadMessages(maxMessages: Int = MAX_SMS_MESSAGES): List<HubMessage> {
        val projection =
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
            )

        val result = mutableListOf<HubMessage>()

        runCatching {
            context
                .contentResolver
                .query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    null,
                    null,
                    Telephony.Sms.DATE + " DESC",
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                    val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                    val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)

                    while (cursor.moveToNext() && result.size < maxMessages) {
                        val type = cursor.getInt(typeIndex)
                        result +=
                            HubMessage(
                                id = cursor.getLong(idIndex),
                                threadId = cursor.getLong(threadIndex),
                                address = cursor.getString(addressIndex).orEmpty(),
                                body = cursor.getString(bodyIndex).orEmpty(),
                                dateMillis = cursor.getLong(dateIndex),
                                incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                                read = cursor.getInt(readIndex) != 0,
                            )
                    }
                }
        }

        return result
    }

    private fun loadPeople(maxPeople: Int = MAX_CONTACT_PEOPLE): List<HubPerson> {
        val locale = Locale.getDefault()
        val result = linkedMapOf<String, HubPerson>()

        runCatching {
            queryContactPeople(maxPeople, result)
        }

        return sortPeople(result.values, locale)
    }

    private fun queryContactPeople(
        maxPeople: Int,
        result: MutableMap<String, HubPerson>,
    ) {
        context
            .contentResolver
            .query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                contactPeopleProjection(),
                null,
                null,
                CONTACTS_FAVORITES_FIRST_SORT,
            )?.use { cursor ->
                loadPeopleFromCursor(cursor, maxPeople, result)
            }
    }

    private fun contactPeopleProjection(): Array<String> =
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            CONTACT_STARRED_COLUMN,
        )

    private fun loadPeopleFromCursor(
        cursor: Cursor,
        maxPeople: Int,
        result: MutableMap<String, HubPerson>,
    ) {
        val indexes = contactColumnIndexes(cursor)
        while (cursor.moveToNext() && result.size < maxPeople) {
            addPersonFromCursor(cursor, indexes, result)
        }
    }

    private fun contactColumnIndexes(cursor: Cursor): ContactColumnIndexes =
        ContactColumnIndexes(
            id =
                cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ),
            name =
                cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ),
            number =
                cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
            favorite = cursor.getColumnIndex(CONTACT_STARRED_COLUMN),
        )

    private fun addPersonFromCursor(
        cursor: Cursor,
        indexes: ContactColumnIndexes,
        result: MutableMap<String, HubPerson>,
    ) {
        val number = cursor.getString(indexes.number).orEmpty()
        val normalized = normalizeAddress(number)
        if (normalized.isBlank() || result.containsKey(normalized)) {
            return
        }

        result[normalized] =
            HubPerson(
                id = cursor.getLong(indexes.id),
                displayName =
                    cursor
                        .getString(indexes.name)
                        ?.takeIf { it.isNotBlank() }
                        ?: number,
                phoneNumber = number,
                favorite =
                    indexes.favorite >= 0 &&
                        cursor.getInt(indexes.favorite) != 0,
            )
    }

    private fun sortPeople(
        people: Collection<HubPerson>,
        locale: Locale,
    ): List<HubPerson> =
        people.sortedWith(
            compareByDescending<HubPerson> {
                it.favorite
            }.thenBy {
                it.displayName.lowercase(locale)
            }.thenBy {
                normalizeAddress(it.phoneNumber)
            },
        )

    private fun lookupContactName(address: String): String? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return null
        }

        val uri =
            Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address),
            )

        return runCatching {
            context
                .contentResolver
                .query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                ContactsContract.PhoneLookup.DISPLAY_NAME,
                            ),
                        )
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    private fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun normalizeAddress(value: String): String =
        PhoneNumberUtils
            .normalizeNumber(value)
            .ifBlank { value.trim() }

    private data class ContactColumnIndexes(
        val id: Int,
        val name: Int,
        val number: Int,
        val favorite: Int,
    )

    private companion object {
        const val MAX_SMS_MESSAGES = 1200
        const val MAX_CONTACT_PEOPLE = 300
        const val SABLE_MAIL_PACKAGE = "org.sableos.mail"
        private const val CONTACT_STARRED_COLUMN = "starred"
        private const val CONTACTS_FAVORITES_FIRST_SORT =
            "$CONTACT_STARRED_COLUMN DESC, " +
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME +
                " COLLATE LOCALIZED ASC"
        val SABLE_MAIL_SNAPSHOT_URI: Uri =
            Uri.parse("content://org.sableos.mail.snapshot/current")
    }
}
